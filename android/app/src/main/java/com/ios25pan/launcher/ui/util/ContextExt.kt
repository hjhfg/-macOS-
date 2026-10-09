package com.ios25pan.launcher.ui.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/** Compose 里拿 Activity（用于调整窗口亮度）。 */
fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
