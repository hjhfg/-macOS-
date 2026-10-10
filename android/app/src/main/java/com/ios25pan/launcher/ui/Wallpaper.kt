package com.ios25pan.launcher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.ios25pan.launcher.R
import com.ios25pan.launcher.data.wallpaper.WallpaperRender
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import androidx.compose.foundation.pager.PagerState

/**
 * === 这个文件是干什么的 ===
 *
 * 专门负责"画桌面背景"这一件事——壁纸。[WallpaperRender] 一共有 4 种情况（在
 * [com.ios25pan.launcher.data.wallpaper.WallpaperRepository] 里判定好、作为状态传进来），
 * 这个文件只管"拿到判定结果之后具体怎么画出来"：
 *
 *  - [WallpaperRender.Transparent]：什么都不画，让 Android 系统自己的壁纸从我们窗口背后透出来。
 *  - [WallpaperRender.Bitmap]：画一张位图（比如系统壁纸的快照）。
 *  - [WallpaperRender.Preset]：画一张打包进 App 里的图片。
 *  - [WallpaperRender.Video]：循环播放一段本地视频（见下面的 [VideoWallpaper]）。
 *
 * `HomeScreen.kt` 只会调用最上面的 [Wallpaper] 一个函数，不关心具体是哪一种，这是
 * Kotlin `sealed interface` + `when` 的好处：以后要加第 5 种壁纸模式，这里加一个
 * `when` 分支，编译器会在所有"忘记处理新分支"的地方报错，不会漏改。
 */

/**
 * 翻页时壁纸跟着做一点点视差位移的偏移量，单位 dp。数值越大，壁纸"追随"手指的幅度越明显。
 *
 * 声明成 `internal`（不是 `private`）是因为 `HomeScreen.kt` 需要用同一个数值把它换算成像素
 * 传进来——`private` 在 Kotlin 里对"顶层声明"来说是**文件级**可见性，即使两个文件在同一个包里，
 * 对方也看不到，所以这里不能用 `private`。
 */
internal val PARALLAX_SHIFT_DP = 40.dp


/**
 * 壁纸层：接到桌面状态里的 [render]，按它的真实类型画出对应内容。
 *
 * @param hazeState 毛玻璃模糊要用的"取景框"。[dev.chrisbanes.haze.hazeSource] 的意思是
 *   "把这块区域画出来的内容记下来，供上面的玻璃面板（Dock、控制中心…）截屏模糊"。
 *   即使是 [WallpaperRender.Transparent]（什么都没画）也要挂这个修饰符——
 *   不挂的话 Haze 找不到任何"源内容"，玻璃面板会直接变成不透明，而不是我们想要的
 *   "看不清但半透明"的效果。
 * @param pagerState 桌面翻页用的分页器状态，这里只读它的"翻到第几页/翻了多少"用来算视差。
 * @param parallaxPx [PARALLAX_SHIFT_DP] 换算成像素后的值（Compose 的 `graphicsLayer`
 *   只接受像素，不接受 dp，所以转换这步要在调用方做好，这里直接拿来用）。
 */
@Composable
fun Wallpaper(
    render: WallpaperRender,
    hazeState: HazeState,
    pagerState: PagerState,
    parallaxPx: Float,
) {
    // 位图 / 预设图两种情况公用同一套"铺满 + 视差位移"修饰符，提出来避免重复写两遍。
    val parallax = Modifier
        .fillMaxSize()
        // hazeSource 一定要放在 graphicsLayer 之前：视差位移也要算作"源内容"的一部分，
        // 这样模糊面板里看到的背景也会跟着轻轻移动，而不是纹丝不动显得假。
        .hazeSource(hazeState)
        .graphicsLayer {
            // pagerState.currentPageOffsetFraction：当前页相对目标页的"翻了多少"，
            // 范围大致是 -1f..1f，翻页动画进行中这个值会连续变化。
            translationX = -pagerState.currentPageOffsetFraction * parallaxPx
            // 壁纸比屏幕稍微放大一点（1.08 倍），这样左右位移的时候不会露出图片边缘的空白。
            scaleX = 1.08f
            scaleY = 1.08f
        }

    when (render) {
        // 什么都不画：一个空 Box，只负责把这块区域登记成 Haze 的"源内容"。
        WallpaperRender.Transparent -> Box(modifier = Modifier.fillMaxSize().hazeSource(hazeState))

        is WallpaperRender.Bitmap -> Image(
            bitmap = render.image,
            contentDescription = null,
            // Crop = 居中裁剪铺满，和 CSS 的 background-size: cover 是一回事。
            contentScale = ContentScale.Crop,
            modifier = parallax,
        )

        is WallpaperRender.Preset -> Image(
            painter = painterResource(render.resId),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = parallax,
        )

        is WallpaperRender.Video -> VideoWallpaper(path = render.path, modifier = parallax)
    }
}

/**
 * 视频壁纸：循环、静音播放用户选的一段本地视频文件。
 *
 * === 给不熟悉 Android View 系统的同学的背景知识 ===
 * Jetpack Compose 没有自己的视频播放控件，播视频还是得借用传统 Android View 世界里的
 * `PlayerView`（来自 Media3/ExoPlayer 库）。[AndroidView] 就是 Compose 提供的"桥"，
 * 专门用来在 Compose 的 UI 树里塞一个传统 View。
 *
 * === 一个容易踩的坑：必须用 TextureView，不能用默认的 SurfaceView ===
 * `SurfaceView` 会在屏幕上开一个独立的"硬件图层"，由系统合成器单独叠加显示，
 * 不经过我们这个 App 自己的绘制流程——这意味着 Haze 的模糊效果（它靠"截一张当前画面的
 * 快照再模糊"实现）根本看不到这层画面，模糊面板盖上去会变成直接"看穿"到桌面图标。
 * `TextureView` 则是把解码出来的每一帧贴成一张普通的纹理，走的是正常 View 绘制管线，
 * 跟画一张 `Bitmap` 没有本质区别，可以被正常截屏、模糊。代价是比 `SurfaceView` 多一次
 * GPU 拷贝，但壁纸这种画面（不是 4K 播放器）可以接受。
 *
 * `PlayerView` 到底用哪种 Surface，只能在 XML 布局 inflate 的那一刻通过
 * `app:surface_type="texture_view"` 定死，没有"运行时改一下"的 setter——所以下面没有
 * 直接 `PlayerView(context)`，而是 inflate 了专门写好的
 * `res/layout/video_wallpaper_player.xml`。
 *
 * @param path 视频文件在本机的绝对路径（已经被 `WallpaperRepository.setVideo()` 复制进了
 *   App 私有目录，不是用户相册里原始的 content:// 地址——理由和自定义图片壁纸一样：
 *   用户随时可能在系统设置里撤回相册权限，私有目录里的那一份不受影响）。
 */
@Composable
private fun VideoWallpaper(path: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    // 拿到"这个 Composable 现在活在哪个生命周期里"（Activity 的 onStart/onStop 等），
    // 用来决定视频该播还是该暂停。
    val lifecycleOwner = LocalLifecycleOwner.current

    // remember(path)：只有 path 变了（用户换了一个视频）才会重新创建播放器；
    // 否则每次重组都复用同一个 ExoPlayer 实例，不会从头建一个新的、画面跳一下。
    val player = remember(path) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(java.io.File(path))))
            repeatMode = Player.REPEAT_MODE_ONE // 单个视频循环播放，不是"播放列表循环"
            volume = 0f // 壁纸不该有声音，和系统自带动态壁纸的默认行为一致
            playWhenReady = true // 准备好就自动开始播放，不用额外调用 play()
            prepare()
        }
    }

    // === 生命周期联动：退到后台就暂停，回到前台再续播 ===
    // 视频解码是实打实的电量 + 发热成本，用户已经看不到桌面了（切到别的 App 或锁屏），
    // 没有理由让它一直在后台跑。DisposableEffect 的 onDispose 块会在这个 Composable
    // 彻底离开屏幕时执行，用来注销监听、释放播放器，避免内存泄漏。
    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> player.play()
                Lifecycle.Event.ON_STOP -> player.pause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.release() // 必须释放，否则底层的解码器资源会一直占着不还
        }
    }

    AndroidView(
        modifier = modifier,
        // factory：只在第一次创建这个 View 时调用一次，之后重组不会重新执行。
        factory = { ctx ->
            val view = android.view.LayoutInflater.from(ctx)
                .inflate(R.layout.video_wallpaper_player, null) as PlayerView
            view.player = player
            view
        },
        // onRelease：这个 View 从 Compose 树上被移除时调用，用来解绑播放器（避免悬挂引用）。
        onRelease = { it.player = null },
    )
}
