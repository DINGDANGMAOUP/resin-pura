package com.dingdangmaoup.resin.pura.resin.configuration

import com.dingdangmaoup.resin.pura.ResinBundle
import com.intellij.execution.ExecutionException
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.io.FileUtil
import org.jdom.Element
import java.io.File
import java.io.IOException

class ResinGeneratedConfig(element: Element?, prefix: String) : AutoCloseable {
    private val myElement: Element
    private val myFile: File

    init {
        myElement = element ?: throw ExecutionException(ResinBundle.message("run.resin.conf.load.error"))
        try {
            myFile = FileUtil.createTempFile(prefix, ".conf")
            myFile.deleteOnExit()
        } catch (e: IOException) {
            throw ExecutionException(ResinBundle.message("message.error.resin.conf.cant.create", e))
        }
    }

    private var closed = false

    fun getFile(): File = myFile

    override fun close() {
        if (!closed) {
            FileUtil.delete(myFile)
            closed = true
        }
    }

    @Throws(ExecutionException::class)
    fun save() {
        check(!closed) { "Generated Resin configuration is closed" }
        try {
            JDOMUtil.write(myElement, myFile.toPath())
        } catch (e: IOException) {
            throw ExecutionException(ResinBundle.message("message.error.resin.conf.update"), e)
        }
    }
}
