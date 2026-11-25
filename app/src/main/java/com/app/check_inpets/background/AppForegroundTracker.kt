package com.app.check_inpets.background

object AppForegroundTracker {
    @Volatile
    var isForeground: Boolean = false

    @Volatile
    private var suppressNextExit: Boolean = false

    fun suppressNextExitNotification() {
        suppressNextExit = true
    }

    fun consumeSuppressExitNotification(): Boolean {
        val current = suppressNextExit
        suppressNextExit = false
        return current
    }
}
