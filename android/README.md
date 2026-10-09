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
`./gradlew :app:assembleDebug` 首次编译时核对（主要是 Compose API 版本相关的细节）。
