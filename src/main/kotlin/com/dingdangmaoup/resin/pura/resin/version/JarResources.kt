package com.dingdangmaoup.resin.pura.resin.version

import java.io.BufferedInputStream
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.util.HashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

class JarResources(private val jarFileName: String, private val fullLoaded: Boolean = true) {
    var debugOn: Boolean = false
    private val htJarContents: MutableMap<String, ByteArray> = ConcurrentHashMap()

    init {
        if (fullLoaded) {
            fullScan()
        }
    }

    fun getResource(name: String): ByteArray? {
        if (!fullLoaded && !htJarContents.containsKey(name)) {
            loadOnDemand(name)
        }
        return htJarContents[name]
    }

    private fun fullScan() {
        try {
            val htSizes: MutableMap<String, Int> = HashMap()
            ZipFile(jarFileName).use { zf ->
                val e = zf.entries()
                while (e.hasMoreElements()) {
                    val ze = e.nextElement()
                    htSizes[ze.name] = ze.size.toInt()
                }
            }

            val fis = FileInputStream(jarFileName)
            val bis = BufferedInputStream(fis)
            val zis = ZipInputStream(bis)
            while (true) {
                val ze = zis.nextEntry ?: break
                if (ze.isDirectory) {
                    continue
                }

                if (debugOn) {
                    println("ze.getName()=${ze.name},getSize()=${ze.size}")
                }

                var size = ze.size.toInt()
                if (size == -1) {
                    size = htSizes[ze.name] ?: 0
                }

                val b = ByteArray(size)
                var rb = 0
                var chunk: Int
                while (size - rb > 0) {
                    chunk = zis.read(b, rb, size - rb)
                    if (chunk == -1) {
                        break
                    }
                    rb += chunk
                }
                htJarContents[ze.name] = b

                if (debugOn) {
                    println("${ze.name}  rb=$rb,size=$size,csize=${ze.compressedSize}")
                }
            }
            zis.close()
        } catch (_: NullPointerException) {
            println("done.")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadOnDemand(name: String) {
        var zf: ZipFile? = null
        var input: InputStream? = null
        try {
            zf = ZipFile(jarFileName)
            val ze = zf.getEntry(name) ?: return
            input = zf.getInputStream(ze)
            val size = input.available()
            if (size == -1) {
                return
            }
            val b = ByteArray(size)
            input.read(b)
            htJarContents[ze.name] = b
            input.close()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            if (zf != null) {
                try {
                    zf.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
            if (input != null) {
                try {
                    input.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
    }
}
