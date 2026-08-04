package com.dingdangmaoup.resin.pura.resin.version

import com.intellij.openapi.util.io.FileFilters
import java.io.File
import java.io.FileFilter

object ResinLibCollector {
    private val JAR_FILTER: FileFilter = FileFilters.filesWithExtension("jar")

    @JvmStatic
    fun getLibFiles(libDir: File, all: Boolean): Array<File> {
        val files = if (all) {
            libDir.listFiles(JAR_FILTER) ?: emptyArray()
        } else {
            libDir.listFiles { dir, name ->
                JAR_FILTER.accept(File(dir, name)) && (name.contains("jsdk") || name.contains("javaee-"))
            } ?: emptyArray()
        }
        return files.sortedBy(File::getName).toTypedArray()
    }
}
