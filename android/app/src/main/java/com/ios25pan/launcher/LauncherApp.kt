package com.ios25pan.launcher

import android.app.Application
import com.ios25pan.launcher.util.CrashHandler
import com.ios25pan.launcher.util.SafeMode
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class LauncherApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // 启动器崩了会直接回不了桌面，先把崩溃栈落到能随手打开的地方
        SafeMode.install(this)
        CrashHandler.install(this)
    }
}
