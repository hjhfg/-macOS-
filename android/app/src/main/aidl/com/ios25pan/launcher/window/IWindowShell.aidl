package com.ios25pan.launcher.window;

/**
 * Shizuku 的 UserService 接口。
 *
 * 这个接口的实现运行在 Shizuku 拉起的**特权进程**里（uid 0 / 2000，取决于 Shizuku 是 root 还是 adb 启动），
 * 而不是本应用的普通进程 —— 所以 exec() 里的 Runtime 拥有 shell 权限，可以执行 am / settings。
 *
 * 注意：UserService 的进程不是一个合法的 Android 应用进程（没有注册到 ActivityThread 的应用形态），
 * 里面不要碰 Context / ContentResolver / registerReceiver 之类的框架 API，只做纯 Java 的事。
 */
interface IWindowShell {

    /** Shizuku 服务端保留的销毁方法，必须在 destroy 里结束进程，否则特权进程会一直活着。 */
    void destroy() = 16777114;

    /** 执行一条 shell 命令，返回合并后的 stdout+stderr 以及退出码。 */
    String exec(String command) = 1;
}
