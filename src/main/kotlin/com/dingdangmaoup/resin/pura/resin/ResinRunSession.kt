package com.dingdangmaoup.resin.pura.resin

import java.io.File

/** Resources belonging to one launch, never copied into editable configuration snapshots. */
internal class ResinRunSession : AutoCloseable {
    @Volatile var configuration: ResinConfiguration? = null
    var username: String? = null
    var password: String? = null
    // These files are created and managed by IntelliJ's JmxRemoteUtil.
    var accessFile: File? = null
    var passwordFile: File? = null
    @Volatile var isClosed = false
        private set
    @Volatile private var process: Process? = null

    val hasLiveProcess: Boolean get() = process?.isAlive == true

    fun own(process: Process) {
        this.process = process
        process.onExit().thenRun { close() }
    }

    @Synchronized
    override fun close() {
        if (isClosed) return
        isClosed = true
        configuration?.close()
        configuration = null
        username = null
        password = null
        accessFile = null
        passwordFile = null
    }
}
