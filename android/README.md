# iOS 桌面（Android 启动器）

把仓库根目录 `ios.25pan.com.zip`（TabOS 的 Web 前端静态导出）转成了一个**真正的 Android 桌面启动器**：
Kotlin + Jetpack Compose + Material 3 + MVI + Hilt + Room/DataStore。

启动器只要在 `AndroidManifest.xml` 里声明 `android.intent.category.HOME`，系统就会把它列进
"默认主屏幕"的选择列表里 —— **不需要系统签名，不需要 root**，用户在设置里选一次即可。

> **关于信息架构的一次反复**：中途试过把这个项目往"macOS 27 Golden Gate 桌面"的方向改
> （顶部菜单栏取代状态栏、桌面和"全部应用"分层、Dock 只放常用图标），实现之后做了 H5 demo
> 对比，确认效果不如直接忠于原网页，已经整体 revert 回这版——UI 就是对 `ios.25pan.com.zip`
> 的忠实 1:1 翻译，不是另起炉灶的 macOS 风格。下面的所有章节描述的都是这版「忠于原网页」的实现。

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
`LayoutEngine` 保留元素**顺序**和各自的跨度，重新打包成网格页
（首次适配、以后转屏或分屏都走同一套逻辑）。

列数不再是写死的 4 —— 这次加了 `domain/GridSpec.kt`：按实际可用 dp 算列数/行数，
手机竖屏落地还是 4×6，平板横屏（本项目的目标设备是 Galaxy Tab S11 Ultra）能到 10~12 列。
`HomeScreen` 用 `LocalConfiguration` 拿到当前尺寸，`HomeStore` 把它和布局数据、应用快照一起
`combine` 成桌面，旋转屏幕会自动重新打包（控制中心有「强制横屏」开关，默认开，关掉就跟随重力感应）。

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
    │   ├── wallpaper/WallpaperRepository.kt  # 壁纸来源仲裁：穿透 / 快照位图 / 预设
    │   └── window/                   # 自由窗口：Shizuku 通道 + am 指令封装 + 记账表
    │       ├── WindowShellService.kt #   运行在 Shizuku 特权进程里的 AIDL 实现
    │       ├── WindowShell.kt        #   绑定/调用 WindowShellService 的 Binder 客户端
    │       ├── FreeformController.kt #   封装 am start/task resize/force-stop
    │       └── WindowRepository.kt   #   打开着哪些自由窗口（我们自己这边的记账）
    ├── mvi/                          # HomeIntent / HomeState / HomeEffect / HomeStore
    └── ui/                           # Compose：桌面页、Dock、文件夹、控制中心
        ├── HomeScreen.kt             # 顶层编排：只负责把下面这些模块叠起来、转发 HomeIntent
        ├── StatusBar.kt              # 顶部状态栏（时间 + 控制中心/编辑完成按钮）
        ├── Wallpaper.kt              # 壁纸（图片/视频/系统壁纸透传）+ 视频壁纸播放
        ├── Desktop.kt                # 桌面：横滑翻页的图标网格 + 翻页小圆点
        ├── DesktopGrid.kt            # 网格摆位算法（按 row/col/rowSpan/colSpan 定位）
        ├── Dock.kt                   # 底部 Dock（鼠标/指针跟随放大）
        ├── Glass.kt                  # 玻璃材质统一入口（材质选择 + 降采样 + clip 顺序）
        ├── WindowShelf.kt            # 自由窗口芯片条（点击前置 / 叉掉关闭）
        └── Motion.kt                 # 动效规格
```

> 关于"文件管理器 App""浏览器 App"：这两个不是独立的 Compose 界面/kt 文件。本项目是一个
> **启动器（Launcher）**，桌面上"文件""浏览器"这类图标点击后，是 `HomeStore` 通过
> `AppRepository` 按"角色"（比如 role:filemanager、role:browser）去系统里解析出用户手机上
> 真正安装的那个 App，再用标准的 Android `Intent` 把它拉起来——启动器本身不内置、也不重新
> 实现这些 App 的界面。所以模块化拆分时，这两个"App"没有对应的 `ui/*.kt` 文件，职责边界在
> `data/apps/AppRepository.kt`（按 `role:xxx` 解析出目标 App 并生成 `Intent`） +
> `mvi/HomeStore.kt`（`HomeIntent.Tap` 的处理分支，决定何时调用上面的解析逻辑）。

数据流是单向的：

```
Compose UI  ──Intent──▶  HomeStore.reduce()  ──▶ State ──▶ UI
                              │
                              └─▶ perform()：IO / 启动 Activity / 系统服务 ──▶ Effect
```

## 玻璃质感（Haze）

用 [Haze](https://github.com/chrisbanes/haze) 做毛玻璃。选它的理由：Compose 生态里事实上的标准，
作者是 Chris Banes（Compose 团队成员之一），持续维护；而且它的 `haze-materials` 模块直接提供了
**`CupertinoMaterials`** —— 数值取自 Apple 官方发布的 iOS 18 Figma，做仿 macOS 的桌面时
不需要自己调透明度和模糊半径，直接用 `thin / regular / thick` 几档即可。

### 为什么是 1.7.3 而不是最新的 2.0.1

Haze 2.0（2026-09 发布）新增了 `haze-glass` 折射玻璃模块，效果更好，但**用不了**：

| | 需要的 Kotlin | 配套 KSP |
|---|---|---|
| Haze 2.0.1 | 2.4.20 | **不存在** |
| Haze 1.7.3 | 2.3.20 | 2.3.12 ✅ |

查证过程：KSP 的 GitHub 上最新只到 2.3.12，而 KSP 2.3.12 自己的构建基线是 `kotlin-base = 2.3.20`
（没有 2.4.x 的发布）。Kotlin 2.4.20 编译出来的库，元数据版本是 2.4，低版本编译器读不了；
而本项目用 Room 和 Hilt，两个都依赖 KSP。所以 Kotlin 2.4.x 目前没有可用的 KSP，
2.0.1 这条路走不通，1.7.3 是当前能用的最佳选择。

（如果后续 KSP 发布 2.4.x，升级到 Haze 2 只要把依赖换成 `haze` + `haze-glass`，
API 从 `hazeEffect(state, style)` 改成 `hazeGlass(input = HazeInput.Sources(state), style = GlassStyle.regular)`，
其余布局代码不用动。）

### 用法

全屏只有**一个**模糊源 —— 壁纸：

```kotlin
val hazeState = rememberHazeState()
Image(painter = painterResource(R.drawable.wallpaper_sunny_night),
      modifier = Modifier.fillMaxSize().hazeSource(hazeState))
```

需要玻璃的面板用 `Modifier.launcherGlass(hazeState, shape, style)`（`ui/Glass.kt`）：

```kotlin
Modifier.launcherGlass(hazeState, RoundedCornerShape(26.dp), dockGlass())
```

内部做了两件容易踩坑的事：

- **`clip(shape)` 必须写在 `hazeEffect` 之前**，否则圆角裁不到模糊上（Haze 官方 sample 的写法）。
- **`MaterialTheme.colorScheme.surface` 必须是不透明的**：Cupertino 材质预设拿它当玻璃背景色，
  半透明会让没被模糊的原图从玻璃背后透出来（主题里已改为 `0xFF1C1C1E`）。

应用位置：Dock（thin）、文件夹卡片（regular）、控制中心面板（thick）、小组件选择器（thick）。
玻璃上的文字统一用 `OnGlass`（跟随主题的 `onSurface`），深色主题白字、浅色主题深字，两种情况都能读。

### 平台差异

Android 12（API 31）以下 Haze 拿不到 `RenderEffect`，会自动退化成半透明遮罩（有玻璃感但没有模糊）。
另外 Haze 明确跳过了 API 31 —— 它在 31 上用遮罩，32 及以上才真正模糊
（`HazeDefaults.blurEnabled()` 的行为）。所以"真·毛玻璃"的最低要求是 Android 13。

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

### 玻璃的成本

模糊的开销和像素数成正比，所以：

- 统一开了 **0.8 降采样**（`Glass.kt` 的 `INPUT_SCALE`）：总像素数减少约 35%，肉眼基本无感。
  这是 Haze 官方推荐的性能旋钮（`HazeInputScale.Fixed`），觉得不够快可以调到 0.6。
- 壁纸视差会让模糊源每帧失效、重新计算模糊。这是本项目里玻璃最贵的地方。
  低端机上如果翻页掉帧，把 `Wallpaper.kt` 里的 `PARALLAX_SHIFT_DP` 改成 `0.dp` 即可（其他动效不受影响）。
- 同时可见的玻璃区域最多两块（常驻的 Dock + 一个浮层），没有满屏铺玻璃。

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

3. **窗口不是启动器画的，是系统画的**。Android 里每个 App 跑在自己的进程、自己的 Window 上，
   由 SystemUI / WM Shell 负责渲染、层叠、拖拽/缩放手柄——系统没有 iframe 的等价物让 A 应用
   把 B 应用的界面渲染成自己的一个 View（这是防点击劫持/UI redressing 的刻意设计）。
   Android 13+ 的 Activity Embedding 是唯一的官方口子，但要求**被嵌入的 App 主动声明信任宿主**，
   普通 App 不会声明，等于不存在。

   能做、也做了的是"发指令"：`ActivityOptions.setLaunchWindowingMode()` 是隐藏 API，
   第三方进程调不了（反射也会被限制），`setLaunchBounds()` 虽公开但只能设边界、改不了 windowing mode。
   实测可行的路径是 shell 身份执行 `am start --windowingMode 5`——于是引入 **Shizuku**，
   详见下面「自由窗口」一节。

## 壁纸：原手机的壁纸 + 可自定义

默认行为是"开箱即用显示真实壁纸，不需要用户做任何事"，判定逻辑在 `WallpaperRepository`：

1. 当前是**动态壁纸**（`WallpaperManager.getWallpaperInfo() != null`）→ 没法截成静态位图，
   直接用**透明穿透**：Activity 开 `FLAG_SHOW_WALLPAPER`、主题 `windowBackground = @null`、
   Compose 侧这块不画任何东西，系统合成的壁纸图层（含动态效果）就直接透出来，零权限。
2. 不是动态壁纸、且有读取权限 → 截一张系统壁纸的静态快照（`WallpaperManager.getDrawable()`），
   自己画成一张 `Image`，**可以被 Haze 模糊**。
3. 都不满足 → 还是回退透明穿透（没有模糊，但用户始终能看到真实壁纸，不会黑屏）。

**为什么默认不是直接截图**：Android 13 起 `WallpaperManager.getDrawable()` 必须持有
`READ_MEDIA_IMAGES`（或更早版本的 `READ_EXTERNAL_STORAGE`）才能拿到真实位图，否则系统只给占位图
甚至抛 `SecurityException`（见 `WallpaperManagerService#getWallpaperWithFeature` 的权限检查，
`android.permission.READ_WALLPAPER_INTERNAL` / `MANAGE_EXTERNAL_STORAGE` 都是系统特权应用才有的）。
这是一个普通的运行时危险权限，不是特殊授权，但既然不要也能"看到真实壁纸"，就不在启动时强制弹窗，
放到控制中心按需申请。

控制中心的「壁纸」区块三个选项：

- **系统壁纸**：按上面的逻辑自动判定，顺带触发一次权限申请（同意了就能被模糊）。
- **选择图片**：Android 自带的 Photo Picker（`ActivityResultContracts.PickVisualMedia`），
  不需要任何权限声明；选中的图复制进应用私有目录，可被模糊。
- **选择视频**：同一个 Photo Picker，筛子换成 `VideoOnly`，同样不需要权限声明；
  选中的视频复制进私有目录循环静音播放，见下面「视频壁纸」一节。
- **更换系统壁纸…**：直接拉起系统自己的壁纸选择器（`Intent.ACTION_SET_WALLPAPER`），
  在那边选好（含系统自带的动态壁纸）之后回到启动器会自动重新判定。
- 另外还有 3 张内置预设缩略图（转换脚本从网页端导出的），点一下直接切换，同样可被模糊。

壁纸跟随翻页做视差位移（`Wallpaper.kt` 的 `PARALLAX_SHIFT_DP`），四种渲染模式
（穿透 / 位图 / 预设 / 视频）共用同一段 `graphicsLayer` 逻辑。

### 视频壁纸

`WallpaperMode.VIDEO`：用户从 Photo Picker 选一段视频，复制进私有目录
（`custom_wallpaper_video.mp4`，和自定义图片同一套"复制一份、不依赖对方授权"的理由），
用 Media3 ExoPlayer 循环、静音播放（`volume = 0f`、`repeatMode = REPEAT_MODE_ONE`）。

有一个容易踩的坑单独说一下：**播放面必须是 `TextureView`，不能是默认的 `SurfaceView`**。
`SurfaceView` 画在独立的硬件图层上，由系统合成器单独叠加显示，不经过我们这棵 View/Compose 树
自己的绘制流程——Haze（以及任何基于 `RenderEffect`/`RenderNode` 快照的模糊方案）看到的只是
一个"空洞"，挡在它前面的毛玻璃面板会直接看穿到桌面图标，模糊效果形同虚设。`TextureView`
则是把每一帧解码结果贴成一张纹理，走的是普通 View 的绘制管线，和画一张 `Bitmap` 没有本质区别，
能被正常截帧、模糊，代价是比 `SurfaceView` 多一次 GPU 拷贝——这个量级的画面（桌面壁纸，不是
4K 播放器）可以接受。

`PlayerView` 的 surface 类型只能在 XML inflate 的时候通过 `app:surface_type="texture_view"`
定下来，没有运行时 setter，所以不是直接 `PlayerView(context)`，而是 inflate 了一个专门的
`res/layout/video_wallpaper_player.xml`（`ui/Wallpaper.kt` 的 `VideoWallpaper` 组件）。

生命周期上跟 `LocalLifecycleOwner` 挂钩：退到后台（`ON_STOP`）就 `pause()`，回到前台
（`ON_START`）再 `play()`——视频解码一直跑是实打实的电量和发热成本，用户已经看不到桌面
就没有理由继续解码；组件离开组合树时 `release()` 播放器，避免泄漏。

## 自由窗口（Shizuku）

点开控制中心的「窗口模式」开关，桌面图标就会尝试以**自由窗口**（而不是全屏）打开应用。

### 这不是魔法，分工说清楚

- **窗口不是我们画的**：系统 WM Shell 负责渲染、层叠、拖拽/缩放手柄。我们只是告诉系统
  "请用 freeform 模式启动这个 App"，剩下的全是系统的事——所以本项目**没有**也不需要实现
  窗口拖拽/缩放的手势，那是系统窗口装饰自带的。
- **唯一可行的路径是发 shell 命令**：`am start --windowingMode 5`。
  `ActivityOptions.setLaunchWindowingMode()` 是隐藏 API，第三方进程直接调用会被限制；
  `setLaunchBounds()` 公开但改不了 windowing mode。
- **shell 身份从哪来**：[Shizuku](https://github.com/RikkaApps/Shizuku)。用户先装 Shizuku App，
  用 ADB 或 root 启动它一次，之后我们这边通过 Shizuku 的 **UserService** 机制
  （`data/window/WindowShellService.kt`）把一个纯 Java 类丢进 Shizuku 拉起的特权进程
  （uid 0 或 2000）里执行命令。**没有用已经废弃的 `Shizuku#newProcess`**——
  官方从 13.1.1 起标记它废弃、14 起移除，UserService 才是现在推荐的方式。
- **两个全局开关**：第一次用的时候会自动执行
  `settings put global enable_freeform_support 1` 和
  `settings put global force_resizable_activities 1`（跑在 shell 身份下，不需要我们的 App
  持有 `WRITE_SECURE_SETTINGS`——shell 本来就有这个权限）。后者是"强制所有 Activity 可调整大小"，
  不开的话一半 App 会直接拒绝进自由窗口。

### 状态机

控制中心会显示三种状态之一（`data/window/WindowShell.kt` 的 `ShellState`）：

| 状态 | 含义 | 控制中心显示 |
|---|---|---|
| `NOT_RUNNING` | 没装 Shizuku，或者装了但服务没启动 | 提示去装/启动 Shizuku |
| `NOT_GRANTED` | 服务在跑，但还没把 shell 身份授权给本应用 | 「去授权」按钮，点了弹 Shizuku 自己的对话框 |
| `READY` | 可以执行命令了 | 正常显示窗口模式开关 |

**任何一步失败都静默降级为全屏启动**——`LaunchItemUseCase` 里打开自由窗口失败会直接退回
`startActivity`，只弹一条 Toast 说明，绝不会让用户点了图标没反应。

### 已知限制

- 这是**我们自己这边的记账**，不是真正的窗口管理器：`WindowRepository` 只记录"我们让系统开过哪些窗口"，
  系统随时可能让某个窗口消失而不通知我们（用户在最近任务里划掉它…）。桌面上的「运行中」芯片条
  （`ui/WindowShelf.kt`）以此为准，不保证绝对实时。
- `am task resize` 需要先从 `dumpsys activity activities` 里解析出任务 id，用的是正则匹配
  `Task{xxxxxx #123 ...}`，不同 Android 版本的 dumpsys 输出格式可能略有差异，解析失败时
  `FreeformController` 会退回"重新 start 一次"的兜底路径。
- **厂商可以覆盖多窗口行为**（AOSP 文档原话："device manufacturers can override these
  multi-window behaviors"）。三星 One UI 有自己的多窗口栈，实测效果以具体 ROM 版本为准；
  所有操作都返回成败，失败就降级，不会卡在中间状态。

## 内置小程序：文件管理器 / 浏览器（纯 Compose 悬浮窗口）

桌面上的「文件」「浏览器」两个图标点一下，不再走 Intent 跳到别的 App——它们是启动器自己用
Compose 实现的真实功能，以一个可以拖拽、缩放的悬浮卡片窗口形式叠在桌面上面，和上一节的
Shizuku 自由窗口是两套完全不同的机制，不要混淆：

| | Shizuku 自由窗口 | 本节的悬浮窗口 |
|---|---|---|
| 窗口谁来画 | 系统 WM Shell | 我们自己用 Compose 画（`ui/window/FloatingWindow.kt`） |
| 装的是什么 | 手机上另一个真实安装的 App | 启动器自己实现的界面（文件管理器 / 浏览器） |
| 依赖 | 需要装 Shizuku 并授权 | 不需要任何额外 App/权限（文件管理器自身的存储权限除外） |
| 拖拽/缩放手势 | 系统窗口装饰自带 | 自己手写（见下） |

### 窗口本身

`ui/window/FloatingWindow.kt` 是一个通用外壳，不认识里面装的是文件管理器还是浏览器，只接收
一个 `content: @Composable () -> Unit` 插槽。它实现了：

- 标题栏拖拽移动（`detectDragGestures`，拖动时内部状态直接按像素累加，只有最后摆放的那一刻才
  换算成 Dp，避免每一帧都做单位转换）；
- 四条边 + 四个角，一共 8 个透明拖拽热区自由缩放到任意尺寸（只保证不超出屏幕、不小于
  `MIN_WINDOW_WIDTH`/`MIN_WINDOW_HEIGHT`，没有分档位）；
- 一键最大化/还原（最大化只是渲染时临时换一套铺满全屏的数字，原来的位置/大小一直记着，
  点还原立刻变回去）；
- 一键最小化/展开（"卷起来"只剩标题栏那么高，内容区域仍留在组合树里，WebView/文件列表的状态
  不会丢，和关闭窗口有本质区别）；
- 点窗口任意位置置顶（和 `ui/Dock.kt` 放大效果同款的 `PointerEventPass.Initial` 非拦截式监听）。

`ui/window/FloatingWindowHost.kt` 负责"桌面现在该显示哪几个窗口"：读
`HomeState.floatingWindows`（一个 `FloatingWindowEntry` 列表，顺序即层叠顺序，最后一个在最上面），
每条记录画一个 `FloatingWindow`，按 `type` 分发到 `FileManagerApp` 或 `BrowserApp`。点桌面图标
（`LaunchItemUseCase` 把 `role:files`/`role:browser` 拦截成 `LaunchResult.OpenVirtualApp`）、
点已打开窗口（置顶）、点关闭按钮，分别对应 `HomeIntent.OpenFloatingApp/FocusFloatingWindow/
CloseFloatingWindow`，逻辑都在 `HomeStore.kt` 里几行纯数据操作，不涉及任何 IO。

### 文件管理器：真实的增删改查

`data/files/FileManagerRepository.kt` 直接用 `java.io.File` 操作手机存储，`mvi/FileManagerStore.kt`
管状态流转，`ui/apps/FileManagerApp.kt` 画界面——支持新建文件夹、删除（含递归删文件夹）、
重命名、复制、移动（同分区用 `File.renameTo` 秒改名，跨分区退化成"复制后删原件"），长按进入
多选模式。

权限上最绕的一点：Android 11（API 30）起，"看任意目录"需要 `MANAGE_EXTERNAL_STORAGE`
（所有文件访问权限）——这是特殊权限，不能用普通运行时权限弹窗申请，必须跳一个专门的系统设置页
让用户手动开关（`MainActivity.requestFilesAccess()`）；更低版本退回传统的
`WRITE_EXTERNAL_STORAGE` 运行时权限。没有权限时界面只显示一个引导授权的提示，不会尝试读取
任何目录。

点开一个文件交给系统"用什么打开"的选择器时，本地 `file://` 路径必须先经过
`androidx.core.content.FileProvider` 换成 `content://` 地址（否则 Android 7.0 起会直接抛
`FileUriExposedException` 崩溃），对应 `AndroidManifest.xml` 里的 `FileProvider` 声明和
`res/xml/file_paths.xml`。

### 浏览器：真实 WebView + 多标签页 + 书签/历史

`ui/apps/BrowserApp.kt` 里，每个标签页对应一个独立的 `android.webkit.WebView` 实例（保存在
一个 `remember` 住的 `Map<标签页id, WebView>` 里），切换标签页时只是把对应的 WebView 从一个
共享的 `FrameLayout` 容器里换入换出——这样切回某个标签页时，滚动位置、前进/后退历史栈都还在，
不会每次切换都重新加载。

`mvi/BrowserStore.kt`（`mvi/BrowserContract.kt` 定义三件套）管的是"标签页元数据"（网址、标题、
加载进度……），真正的网页内容不归它管（WebView 太大、也不该塞进可随意复制比较的 MVI State 里）。
两者的分工：WebView 的 `WebViewClient`/`WebChromeClient` 回调把"网页发生了什么"通过
`BrowserIntent.PageUpdated` 报给 Store；用户操作（地址栏回车、点后退/前进/刷新）则反过来，
Store 判断完地址之后用 `BrowserEffect` 让 UI 层去调用真正的 `webView.loadUrl()`/`goBack()`。

书签和历史记录持久化在 `data/browser/BrowserPrefs.kt`——项目里没有引入 JSON 序列化库（沙箱连不上
Google Maven，不能新增 Gradle 依赖），用的是和 `data/prefs/LauncherPrefs.kt` 一脉相承的手写编码：
一行一条记录，字段用 `\u0001` 分隔，多条记录用换行分隔。地址栏输入框既能当网址用也能当搜索词用，
由 `BrowserStore.normalizeInput()` 判断：已有 `http(s)://` 前缀直接用；形如 `example.com` 的
补一个 `https://`；其它一律当成关键词交给必应搜索。

### 不会影响什么

这一整块都是纯增量——桌面网格、Dock、状态栏、控制中心、文件夹卡片、Shizuku 自由窗口的外观和
交互都没有改动；`FloatingAppType`/`FloatingWindowEntry`/`FloatingWindow` 这套命名也刻意避开了
`WindowMode`/`WindowRect`/`AppWindow`（Shizuku 那套既有类型），两套机制在代码里不会互相串线。

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

`minSdk 26`（Android 8），`targetSdk 35`，需要 JDK 17。纯 Kotlin，无 Java 代码。
毛玻璃在 Android 13（API 33）及以上才是真模糊，以下自动退化为半透明遮罩。

> 壁纸：默认显示真实系统壁纸（穿透或快照，见上面「壁纸」一节），不需要配置；
> 想强制用某张内置预设，去控制中心点一下缩略图即可，不用改代码。
> 应用图标目前直接拿导出的 `app_safari` 当自适应图标前景，正式用建议换成自己的图。

> 自由窗口：需要先在设备上装好 [Shizuku](https://shizuku.rikka.app/download/)
> 并用 ADB 启动一次（`adb shell sh /sdcard/Android/.../start.sh`，具体命令 Shizuku App 里有）；
> 平板上通常用无线调试（Android 11+）就能在设备上直接启动，不需要连电脑。
> 装好之后在本启动器的控制中心点「去授权」，再打开「自由窗口打开应用」开关即可。
> 不装 Shizuku 完全不影响其它功能，图标照常全屏打开应用。

## 已实现 / 未实现

已实现：桌面分页（HorizontalPager）、文件夹、Dock（放大动效）、系统应用按角色解析、
系统里装了但布局里没有的应用自动补齐并按名称排序、网页书签、小组件添加与删除、
控制中心（亮度/音量/网络面板）、编辑态（长按进入，角标移除）、包安装卸载实时刷新、
**Haze 毛玻璃**（Dock / 文件夹 / 控制中心 / 小组件选择器，Apple 官方材质）、
**平板横屏自适应网格**（`GridSpec`，手机 4×6、平板最多 12×8，旋转实时重算）、
**真实系统壁纸**（动态壁纸透明穿透 / 静态壁纸快照模糊 / 预设 / 自定义图片 / 自定义视频，见上面「壁纸」一节）、
**视频壁纸**（Media3 ExoPlayer + TextureView，循环静音播放，仍可被毛玻璃模糊，见「视频壁纸」一节）、
**自由窗口**（Shizuku + `am start --windowingMode 5`，见上面「自由窗口」一节，失败自动降级全屏）、
**内置文件管理器**（真实读写手机存储：新建/删除/重命名/复制/移动，见「内置小程序」一节）、
**内置浏览器**（真实 WebView，多标签页 + 书签 + 历史记录，同上一节）、
**纯 Compose 悬浮窗口**（文件管理器/浏览器专用，标题栏拖拽移动 + 四边四角自由缩放 +
一键最大化还原，和 Shizuku 自由窗口是两套独立机制，互不依赖）。

未实现（有意留白）：图标拖拽排序（当前是删除/添加，位置由 `LayoutEngine` 自动打包）、
自由旋转（`rotation` 字段已在表里，UI 未开放）、自由窗口的拖拽/缩放手势
（这部分系统自己画、自己处理，启动器不用管）、备份还原。

悬浮窗口的"最小化"采用的是"卷起来只剩标题栏"的方案（标题栏右侧的朝下箭头点一下收起，
朝上箭头点一下展开），不是另开一个类似 `WindowShelf.kt` 的收纳条 UI——内容区域（WebView、
文件列表）在收起期间依然留在组合树里，状态不会丢，和直接关闭窗口有本质区别。

## 崩溃排查

启动器崩了会直接回不了桌面，所以崩溃栈会自动落盘到

```
/sdcard/Android/data/com.ios25pan.launcher/files/crash.log
```

这是应用专属目录，不需要 root、不需要 adb，用任意文件管理器（或把手机连电脑）就能打开。
**控制中心 → 「崩溃日志」** 里也能直接查看和复制。logcat 里同样会打完整堆栈（tag `LauncherCrash`）。

抓 logcat：

```bash
adb logcat -c && adb logcat | grep -E "LauncherCrash|AndroidRuntime|FATAL"
```

### 已经做过的加固

- **单包异常隔离**：设备上只要有一个 App 信息异常（`loadLabel` 抛 BadParcelableException 等），
  以前会让整条 Flow 失败、进而崩掉 ViewModel 协程 —— 现象就是"进了桌面才闪退"。
  现在逐个 `runCatching` 跳过，桌面照常起来。
- **图标解码隔离**：畸形图标 drawable 绘制时抛异常，同样被拦住。
- **建库失败不再致命**：种子数据写入失败只记日志，桌面空着也能起来。
- **Flow 兜底**：上游异常重试 3 次，仍失败则记日志而不是让协程崩掉。

### 如果崩在毛玻璃上

Haze 会把 `RenderEffect.createBlurEffect` 的 `IllegalArgumentException` **原样抛出**
（信息类似 "this device does not support a blur radius of Xdp"），而玻璃第一帧就开始画，
等于一进桌面就崩。Cupertino / Haze 的材质预设写死 24dp，本项目在 `ui/Glass.kt` 里覆盖成了更保守的值：

```kotlin
private val BLUR_RADIUS = 16.dp   // 继续报错就往下调；调到 0.dp 退化成纯半透明，不会崩
```

## 工具链

因为 Haze 1.7.3 是用 Kotlin 2.3.20 / Compose 1.12.0 编译出来的，本项目把工具链整个对齐到了它自己的
CI 组合上（而不是各自取最新版，那才是风险所在）。所有版本都在 `gradle/libs.versions.toml` 顶部注明了来源：

| 组件 | 版本 | 依据 |
|---|---|---|
| Haze | 1.7.3 | 最新 1.x |
| Kotlin | 2.3.20 | Haze 1.7.3 的构建基线 |
| KSP | 2.3.12 | 其内部基线就是 Kotlin 2.3.20；且要求 AGP ≥ 8.12.0 |
| AGP | 8.13.0 | 满足 KSP 的 AGP 下限；仍只需 JDK 17 |
| Gradle | 8.14.6 | 8.x 线最新，满足 AGP 8.13 |
| Compose runtime / ui / foundation / animation | 1.12.0 | Haze 1.7.3 的构建基线 |
| Room | 2.8.3 | Now in Android（Google 官方样本） |
| Hilt | 2.59 | 同上 |
| Lifecycle / Activity / core-ktx / coroutines | 2.10.0 / 1.12.2 / 1.17.0 / 1.10.1 | 同上 + Haze 1.7.3 |
| Shizuku api / provider | 13.1.5 | Shizuku-API 最新稳定版（UserService 机制，非已废弃的 newProcess） |
| Media3 exoplayer / ui | 1.5.1 | 视频壁纸播放，当前稳定版 |

Compose 各构件是**显式锁版本、不用 BOM** 的：BOM 会把版本拉到它自己的组合上，
反而和 Haze 的编译基线错开。

**唯一没能查证的版本是 Material3（用了 1.5.0）**：它是按 androidx 开发分支上的
`COMPOSE_MATERIAL3 = "1.6.0-alpha01"` 推断的稳定版。如果 `./gradlew :app:assembleDebug`
报找不到这个版本，改成 1.4.x 或 1.5.x 里实际存在的版本即可（不影响 Haze）。

## 说明

本次开发环境里没有 JDK 和 Android SDK，也拿不到 Maven Central，所以**代码未经编译验证** ——
这次的版本号不是凭记忆写的，而是逐个查证过的（Haze 的版本矩阵来自它的 GitHub 仓库，
KSP / Gradle / Hilt 的可用版本来自各自的 release 记录，Room / Lifecycle 来自 Google 官方样本
Now in Android 的版本目录）。

已经做的静态检查：括号平衡、包名与目录一致、导入无冗余、Kotlin 源文件结构自检。
首次编译请跑 `./gradlew :app:assembleDebug`（wrapper 已就位，指向 Gradle 8.14.6）。
