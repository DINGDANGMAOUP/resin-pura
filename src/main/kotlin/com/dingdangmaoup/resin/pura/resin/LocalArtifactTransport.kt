package com.dingdangmaoup.resin.pura.resin

import com.intellij.openapi.util.io.FileUtil
import java.io.File
import java.io.IOException

/** Local paths reported by the running archive deployer, not guessed from RESIN_HOME. */
internal class LocalArtifactTransport(
    private val archiveDirectory: File,
    private val expandDirectory: File,
    private val expandPrefix: String,
    private val expandSuffix: String,
) {
    init {
        require(archiveDirectory.isAbsolute && expandDirectory.isAbsolute) { "Resin deployment directories must be absolute" }
        require(archiveDirectory.isDirectory && expandDirectory.isDirectory) { "Resin deployment directories do not exist" }
        require(listOf(expandPrefix, expandSuffix).none { '/' in it || '\\' in it || it == ".." })
    }

    private fun destination(source: File): File =
        if (source.isDirectory) File(expandDirectory, expandPrefix + source.name + expandSuffix)
        else File(archiveDirectory, source.name)

    fun accepts(source: File): Boolean {
        val sourcePath = source.canonicalFile.toPath()
        val expansionName = if (!source.isDirectory && source.name.endsWith(".war", ignoreCase = true)) source.nameWithoutExtension else source.name
        val paths = listOf(destination(source), File(expandDirectory, expandPrefix + expansionName + expandSuffix))
        return paths.all { target ->
            val canonical = target.canonicalFile.toPath()
            val parent = target.parentFile.canonicalFile.toPath()
            canonical.startsWith(parent) && canonical != parent &&
                !canonical.startsWith(sourcePath) && !sourcePath.startsWith(canonical)
        }
    }

    @Throws(IOException::class)
    fun transfer(source: File): Boolean {
        if (!source.exists() || !accepts(source)) return false
        FileUtil.copyFileOrDir(source, destination(source))
        return true
    }

    fun delete(source: File): Boolean {
        // The caller passes the archive name or the extension-free expansion name.
        val target = if (!source.isDirectory && source.name.endsWith(".war", ignoreCase = true)) File(archiveDirectory, source.name)
            else File(expandDirectory, expandPrefix + source.name + expandSuffix)
        val canonical = target.canonicalFile.toPath()
        val parent = target.parentFile.canonicalFile.toPath()
        if (!canonical.startsWith(parent) || canonical == parent || canonical == source.canonicalFile.toPath()) return false
        return FileUtil.delete(target)
    }
}
