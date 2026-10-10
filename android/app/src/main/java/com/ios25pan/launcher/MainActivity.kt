package com.ios25pan.launcher

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.ios25pan.launcher.data.widget.WidgetRepository
import com.ios25pan.launcher.domain.WidgetProvider
import com.ios25pan.launcher.mvi.HomeIntent
import com.ios25pan.launcher.mvi.HomeStore
import com.ios25pan.launcher.ui.HomeScreen
import com.ios25pan.launcher.ui.theme.LauncherTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 因为声明了 android.intent.category.HOME，系统会把它列进"默认主屏幕"选择里 ——
 * 不需要系统签名，也不需要 root，用户手动选一次即可。
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var widgets: WidgetRepository

    // 与 Compose 中的 hiltViewModel() 是同一个实例（ViewModelStoreOwner 都是本 Activity）
    private val store: HomeStore by viewModels()

    /** 等待系统绑定确认的小组件：绑定页不返回结果数据，只能回来对账。 */
    private var pendingWidget: Pair<Int, WidgetProvider>? = null

    private val bindWidgetLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        val (id, provider) = pendingWidget ?: return@registerForActivityResult
        pendingWidget = null
        // AOSP Launcher3 的做法：看这个 id 是否真的拿到了 providerInfo
        store.dispatch(HomeIntent.WidgetBindResult(id, provider, widgets.isBound(id)))
    }

    /** Android 13+ 用 READ_MEDIA_IMAGES，更早用 READ_EXTERNAL_STORAGE；结果只用来触发重新判定。 */
    private val wallpaperPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        store.dispatch(HomeIntent.RefreshWallpaper)
    }

    /** 系统相册选择器：不需要任何权限声明，这是 Android 的 Photo Picker。 */
    private val pickWallpaperLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) store.dispatch(HomeIntent.PickedCustomWallpaper(uri.toString()))
    }

    /** 同一个 Photo Picker，筛视频——视频壁纸同样不需要任何权限声明。 */
    private val pickVideoWallpaperLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) store.dispatch(HomeIntent.PickedVideoWallpaper(uri.toString()))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 启动器必须开这个 flag：系统才会把壁纸图层画在我们窗口背后。
        // 没有它的话，WallpaperRender.Transparent 只会露出一片纯黑，等于"穿透"白做了。
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)

        // 平板横屏是本次的设计目标（Galaxy Tab S11 Ultra 这类大屏设备），但留一个开关：
        // 用户在控制中心关掉"强制横屏"后，跟随系统/重力感应正常转向。
        //
        // 坑：之前这里直接 collect 了整个 state，而 state 在每次翻页 / 点击 / 任何 dispatch
        // 都会重新 emit（PageChanged 每次都会产出一个新的 currentPage，StateFlow 的结构相等去重
        // 救不了它）。于是每划一下桌面都会重新 setRequestedOrientation 一次——即使值没变，
        // 系统也会当成一次方向请求去重新走一遍布局流程，和 HorizontalPager 的滑动动画抢一帧，
        // 表现出来就是整页图标/状态栏在滑动时重叠、重影。
        // 改成只在 forceLandscape 这个字段真正变化时才设置一次。
        lifecycleScope.launch {
            store.state
                .map { it.forceLandscape }
                .distinctUntilChanged()
                .collect { forceLandscape ->
                    val target = if (forceLandscape) {
                        ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
                    } else {
                        ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    }
                    if (requestedOrientation != target) requestedOrientation = target
                }
        }

        setContent {
            LauncherTheme {
                HomeScreen(
                    store = store,
                    onBindWidget = { intent, id, provider ->
                        pendingWidget = id to provider
                        bindWidgetLauncher.launch(intent)
                    },
                    onRequestWallpaperPermission = {
                        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            android.Manifest.permission.READ_MEDIA_IMAGES
                        } else {
                            android.Manifest.permission.READ_EXTERNAL_STORAGE
                        }
                        wallpaperPermissionLauncher.launch(permission)
                    },
                    onPickCustomWallpaper = {
                        pickWallpaperLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                    onPickVideoWallpaper = {
                        pickVideoWallpaperLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly),
                        )
                    },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // 开始接收小组件的 RemoteViews 更新
        widgets.startListening()
    }

    override fun onResume() {
        super.onResume()
        // 用户可能刚从系统设置换了壁纸，或者刚在权限弹窗里点了允许——回来就重新判定一次。
        store.dispatch(HomeIntent.RefreshWallpaper)
    }

    override fun onStop() {
        widgets.stopListening()
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // 作为 HOME，再次点击主页键时回到第一页
        store.dispatch(HomeIntent.PageChanged(0))
    }
}
