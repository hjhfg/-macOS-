# iOS 桌面（Android 启动器）

把仓库根目录 `ios.25pan.com.zip`（TabOS 的 Web 前端静态导出）转成了一个**真正的 Android 桌面启动器**：
Kotlin + Jetpack Compose + Material 3 + MVI + Hilt + Room/DataStore。

启动器只要在 `AndroidManifest.xml` 里声明 `android.intent.category.HOME`，系统就会把它列进
"默认主屏幕"的选择列表里 —— **不需要系统签名，不需要 root**，用户在设置里选一次即可。

## 从网页到 Android 的映射

网页端和 Android 端的 UI 层几乎可以 1:1 翻译，只有底层数据源不同：网页调浏览器 API，
这里调 `PackageManager` / `AppWidgetHost` / `Intent`。

| 网页端（Vue） | Android 端 |
| --- | --- |
| `desk_layout.gridSettings` 的像素级摆放 | Room 表 `desktop_items`，每行 `row/col/rowSpan/colSpan/rotation/type/action` |
| Dock 的鼠标跟随放大 | `Modifier.graphicsLayer { scaleX/scaleY }` + `transformOrigin` 固定底部（`ui/Dock.kt`） |
| 控制中心（Wi-Fi/蓝牙/亮度） | Compose 的 `Slider` + `Settings.Panel` 面板（`ui/ControlCenter.kt`） |
| 窗口层级管理 | MVI 的 `HomeState.editing / openFolderId / controlCenterOpen / widgetPickerOpen` |
| 系统应用（电话、邮箱、相机…） | **按 Intent 角色解析真实应用**，见下 |
| 桌面小组件 | `AppWidgetHost` + `AndroidView` 桥接 |

### 关于"系统应用"

网页里的 `app-1`~`app-25` 是它自己实现的应用，Android 上没有固定包名。
所以转换脚本只记录 **Intent 角色**（`role:phone`、`role:camera`、`role:mail` …），
运行时由 `AppRepository` 用 `Intent.CATEGORY_APP_*` / `ACTION_DIAL` 等去系统里查真实应用。
网页里有但 Android 没有对应能力的（天气卡片、视频、钱包、AI 助手、锁屏、Office、废纸篓）
会被跳过 —— 这些要么做成小组件，要么做成本地应用，不属于启动器。

### 关于布局数据

网页是 15 列的大画布，手机竖屏只有 4 列，所以不能直接照搬原坐标。
`LayoutEngine` 保留元素**顺序**和各自的跨度，重新打包成 4×6 的网格页
（首次适配、以后转屏或分屏都走同一套逻辑）。

## 结构

```
android/
├── tools/convert_web_to_android.py   # 从 zip 抽取布局/图标 → seed JSON + drawable
└── app/src/main/java/com/ios25pan/launcher/
    ├── MainActivity.kt               # HOME Activity，小组件绑定回调
    ├── data/
    │   ├── apps/AppRepository.kt     # PackageManager、Intent 角色解析、图标 LRU 缓存
    │   ├── widget/WidgetRepository.kt# AppWidgetHost、RemoteViews 视图缓存
    │   ├── db/                       # Room：桌面布局表 + 首次启动写入 seed
    │   ├── prefs/LauncherPrefs.kt    # DataStore：设置项（当前记录被移除的元素）
    │   └── DesktopRepository.kt
    ├── domain/                       # 纯 Kotlin，无 Android 依赖，可直接单测
    │   ├── Models.kt                 # DesktopItem / Desktop / ItemAction
    │   ├── LayoutEngine.kt           # 网格打包
    │   ├── BuildDesktopUseCase.kt    # 布局 + 应用快照 → 可渲染桌面
    │   └── LaunchItemUseCase.kt
    ├── mvi/                          # HomeIntent / HomeState / HomeEffect / HomeStore
    └── ui/                           # Compose：桌面页、Dock、文件夹、控制中心
```

数据流是单向的：

```
Compose UI  ──Intent──▶  HomeStore.reduce()  ──▶ State ──▶ UI
                              │
                              └─▶ perform()：IO / 启动 Activity / 系统服务 ──▶ Effect
```

## 流畅度与动效

掉帧基本都出在"把每帧都在变的东西放进了组合期"和"把阻塞 IPC 放错了线程"上。逐条处理如下。

### 避开重组：延迟读取

每帧变化的状态一律放进 `graphicsLayer { }` 的 lambda 里读 —— 这里读取状态只会让**图层重绘**，
不会触发重组。用在这几处：

| 位置 | 每帧变化的量 | 不这么做会怎样 |
| --- | --- | --- |
| Dock 放大 | 指针 x | 手指每动一像素就重组整个 Dock |
| 壁纸视差 | `currentPageOffsetFraction` | 翻页每一帧重组整棵树 |
| 图标按压缩放 | 按压状态 | 按下时重组整个格子 |
| 编辑态抖动 | 无限动画的当前值 | 整页 24 个格子每帧重组 |

Dock 的 `zIndex` 属于布局阶段、没法延迟读，所以用一个量化后的 `derivedStateOf`
（指针落在第几个格子）来触发，只在跨格时重组一次，而不是每次移动都重组。

### 避开主线程：PackageManager 查询

`queryIntentActivities` / `loadLabel` 都是阻塞式 IPC，一次快照要查十几次。处理方式：
包变化广播**去抖 400ms**（安装/更新会连发好几条）、整条链路走 `Dispatchers.IO`
（不是 `Default`，那是给 CPU 密集任务用的）、末尾 `distinctUntilChanged()`
让"变了包但桌面没变"的情况不再重组。中文排序用的 `Collator` 构造不便宜，也复用了一份。

### 别反复重绘：小组件与图标

- `AndroidView` 刻意不写 `update = { invalidate() }` —— update 每次重组都会跑，
  那会让小组件在无关的重组里反复重绘 RemoteViews。
- 图标加载的初始值**同步取自内存缓存**，所以翻回上一页时图标第一帧就在，
  不会"空白 → 淡入"地闪一下；只有缓存没命中、真的等了一会儿才出现的图标才做淡入
  （用 `Animatable`，因为 `animateFloatAsState` 的初始值等于目标值，动画根本不会跑）。

### 动效

统一用 spring 而不是固定时长补间（`ui/Motion.kt`）：手势驱动的界面里动画经常被中途打断，
弹簧能从当前速度接着走，补间会重新起跑，看着就是顿一下。

- 文件夹展开 / 收起：缩放 + 淡入淡出，轻微回弹
- 控制中心：从顶部滑入滑出 + 遮罩淡入淡出（不回弹，否则滑到底会抖）
- 图标按压：缩放到 0.86，弹簧回位；不画水波纹，少一层绘制也更接近 iOS
- 编辑态：每个图标以略微不同的周期左右摆动（避免整齐划一地"齐步走"），删除角标弹簧缩放入场
- 翻页：壁纸反向位移做视差 + 指示器圆点大小弹簧过渡
- 分页预组合相邻页（`beyondBoundsPageCount = 1`），滑动时不用现场组合

### 还有哪里可能卡

- **首次启动**：`snapshot()` 要遍历全机应用，在 IO 线程，不阻塞界面；机器上装了 300+ 应用时
  可以考虑把结果缓存到 Room。
- **壁纸解码**：`painterResource` 首次解码在主线程，只发生一次。想要彻底避免就换 Coil
  异步加载。
- **DPR 高的大屏**：`IconCache` 目前按 128px 缓存、上限 160 张（约 10MB），
  可按屏幕密度调整。

## 三个绕不开的点

1. **小组件没法纯 Compose**。`AppWidgetHost` 返回的是 `RemoteViews`，属于 View 体系，
   只能用 `AndroidView` 桥接 —— 这是全项目唯一"不纯"的地方（`ui/Widgets.kt`）。
   配套做了两件事：`WidgetRepository` 按 `appWidgetId` 缓存 `AppWidgetHostView` 复用；
   `HorizontalPager` 只组合当前页及相邻页，离屏的 `AndroidView` 会被 dispose，不会全量 inflate。

2. **包可见性**。列全机应用需要 `QUERY_ALL_PACKAGES`。启动器属于 Google 明确列出的允许用例
   （和文件管理器、安全工具同级），上架 Play 要提交 Permissions Declaration Form；
   **自用侧载时系统自动授予，不受限**。

3. **窗口化不是启动器的活**。DeX 式独立桌面依赖 `config_isDesktopModeSupported` 这类系统级 flag，
   OEM 层才改得动。普通应用能做的是 Taskbar 那条路：
   `ActivityOptions.setLaunchWindowingMode(WINDOWING_MODE_FREEFORM)` 起浮动窗口，
   前提是设备支持 freeform 且拿到 `WRITE_SECURE_SETTINGS`（Shizuku 授权）。本工程没做。

## 构建

```bash
# 1) 重新生成 seed 数据与图标（zip 变了就要跑一次）
python3 tools/convert_web_to_android.py ../ios.25pan.com.zip

# 2) 编译
./gradlew :app:assembleDebug          # 或 assembleRelease
./gradlew :app:installDebug           # 装到手机上

# 3) 设为默认桌面
#    设置 → 应用 → 默认应用 → 主屏幕应用 → 选择「iOS 桌面」
#    或者：adb shell cmd package set-home-activity com.ios25pan.launcher/.MainActivity
```

`minSdk 26`（Android 8），`targetSdk 35`。纯 Kotlin，无 Java 代码。

> 壁纸：默认用 `wallpaper_sunny_night`，想换直接改 `HomeScreen.kt` 里的
> `R.drawable.wallpaper_sunny_night` 为 `wallpaper_fog` 或 `wallpaper_t01f2b8957f4c756004`。
> 应用图标目前直接拿导出的 `app_safari` 当自适应图标前景，正式用建议换成自己的图。

## 已实现 / 未实现

已实现：桌面分页（HorizontalPager）、文件夹、Dock（放大动效）、系统应用按角色解析、
系统里装了但布局里没有的应用自动补齐并按名称排序、网页书签、小组件添加与删除、
控制中心（亮度/音量/网络面板）、编辑态（长按进入，角标移除）、包安装卸载实时刷新。

未实现（有意留白）：图标拖拽排序（当前是删除/添加，位置由 `LayoutEngine` 自动打包）、
自由旋转（`rotation` 字段已在表里，UI 未开放）、真正的自由窗口、备份还原、动态壁纸
（视频壁纸在启动器上代价太高）。

## 说明

本次开发环境里没有 JDK 和 Android SDK，也拿不到 Gradle 依赖，所以**代码未经编译验证**。
领域层（`domain/`）是纯 Kotlin 且带了单元测试，逻辑已经跑通；UI 层请用
`./gradlew :app:assembleDebug` 首次编译时核对。

需要留意的版本相关 API（都要求 foundation 1.6+，当前 BOM 2024.12 对应 1.7.x）：
`combinedClickable` 的 `indication` / `interactionSource` 参数、`HorizontalPager` 的
`beyondBoundsPageCount`、`PagerState.currentPageOffsetFraction`。

