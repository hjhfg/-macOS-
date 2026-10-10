package com.ios25pan.launcher.domain

/**
 * === 这个文件是干什么的 ===
 *
 * "内置小程序"的最小描述。和桌面上大多数图标不一样——大多数图标点一下是用
 * `Intent` 跳到手机上**另一个真实安装的 App**（见 [LaunchItemUseCase]、
 * `data/apps/AppRepository.kt`），但文件管理器和浏览器这两个，点一下是在
 * **启动器自己的界面里**，用一个可以拖动、缩放的"浮动窗口"把我们自己用 Compose
 * 写的界面画出来——有点像电脑上"程序没有全屏、浮在桌面上面"的那种窗口，
 * 所以代码里统一管这套东西叫 **Floating Window（浮动窗口）**。
 *
 * 浮动窗口本身的拖拽/缩放/层叠逻辑在 `ui/window/FloatingWindow.kt`，
 * 具体某个 App 长什么样在 `ui/apps/FileManagerApp.kt` / `ui/apps/BrowserApp.kt`，
 * 这个文件只放"数据长什么样"。
 */

/** 启动器里已经真正实现了界面的内置 App 种类。 */
enum class FloatingAppType {
    /** 文件管理器：真实读写手机存储，见 `data/files/FileManagerRepository.kt`。 */
    FILES,

    /** 浏览器：真实的 WebView，支持多标签页，见 `mvi/BrowserStore.kt`。 */
    BROWSER,
}

/**
 * 一个"正在打开着"的浮动窗口。
 *
 * 文件管理器、浏览器都被设计成**单例窗口**——点击桌面图标时，如果这个 App 已经开着了，
 * 不会再开一个新的，只会把已经存在的这个窗口提到最前面（和 macOS Dock 点已打开的
 * App 图标是一个效果）。[id] 目前就直接用 [type] 的名字当唯一标识，正是因为"同一类型
 * 只会有一个实例"；以后如果想支持"浏览器可以同时开好几个独立窗口"，只需要把 id
 * 换成随机生成的唯一值——其它代码（[com.ios25pan.launcher.mvi.HomeStore] 的查找逻辑、
 * `ui/window/FloatingWindowHost.kt` 的 `key(entry.id)`）都已经是按 id 而不是按
 * type 来认window的，不需要跟着改。
 */
data class FloatingWindowEntry(
    val id: String,
    val type: FloatingAppType,
)
