package com.ios25pan.launcher.mvi

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ios25pan.launcher.data.video.VideoLibraryRepository
import com.ios25pan.launcher.domain.VideoEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * "本地视频"小程序的 Store（ViewModel）。跟 [FileManagerStore]/[BrowserStore] 一样，
 * 用 `hiltViewModel()` 拿到的是跟 Activity 绑定的全局唯一实例——关掉视频窗口再打开，
 * 已经扫过的列表、选中的相册、搜索词都还在，不用重新扫一遍 MediaStore。
 */
@HiltViewModel
class VideoStore @Inject constructor(
    private val repo: VideoLibraryRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(VideoState(hasAccess = repo.hasAccess()))
    val state: StateFlow<VideoState> = _state.asStateFlow()

    /** 权限请求要用到的那个具体权限名——Activity 发运行时权限弹窗时要用，见 MainActivity。 */
    val requiredPermission: String get() = repo.requiredPermission()

    init {
        if (_state.value.hasAccess) refresh()
    }

    fun dispatch(intent: VideoIntent) {
        when (intent) {
            VideoIntent.Refresh -> refresh()
            VideoIntent.RecheckAccess -> {
                val has = repo.hasAccess()
                _state.update { it.copy(hasAccess = has) }
                if (has && _state.value.videos.isEmpty()) refresh()
            }
            is VideoIntent.SelectBucket -> _state.update { it.copy(selectedBucket = intent.bucket) }
            is VideoIntent.SetSortOrder -> _state.update { it.copy(sortOrder = intent.order) }
            is VideoIntent.SetSearchQuery -> _state.update { it.copy(searchQuery = intent.query) }
            is VideoIntent.Play -> _state.update { it.copy(playing = intent.entry) }
            VideoIntent.StopPlayback -> _state.update { it.copy(playing = null) }
        }
    }

    /** 给 UI 层按需生成缩略图用——UI 不直接依赖 Repository，统一走 Store 这一层。 */
    suspend fun thumbnailFor(entry: VideoEntry): Bitmap? = repo.thumbnail(entry)

    private fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val videos = repo.list()
            _state.update { it.copy(videos = videos, loading = false) }
        }
    }
}
