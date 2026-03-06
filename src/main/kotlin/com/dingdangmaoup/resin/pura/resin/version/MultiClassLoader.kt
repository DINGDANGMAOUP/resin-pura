package com.dingdangmaoup.resin.pura.resin.version

import java.util.concurrent.ConcurrentHashMap

abstract class MultiClassLoader : ClassLoader() {
    private val classes: MutableMap<String, Class<*>> = ConcurrentHashMap()
    private var classNameReplacementChar: Char = '\u0000'

    protected var monitorOn = false
    protected var sourceMonitorOn = true

    @Throws(ClassNotFoundException::class)
    override fun loadClass(className: String): Class<*> {
        return loadClass(className, true)
    }

    @Synchronized
    @Throws(ClassNotFoundException::class)
    override fun loadClass(className: String, resolveIt: Boolean): Class<*> {
        monitor(">> MultiClassLoader.loadClass($className, $resolveIt)")

        val cached = classes[className]
        if (cached != null) {
            monitor(">> returning cached result.")
            return cached
        }

        try {
            val systemClass = findSystemClass(className)
            monitor(">> returning system class (in CLASSPATH).")
            return systemClass
        } catch (_: ClassNotFoundException) {
            monitor(">> Not a system class.")
        }

        val classBytes = loadClassBytes(className) ?: throw ClassNotFoundException()
        val result = defineClass(className, classBytes, 0, classBytes.size) ?: throw ClassFormatError()

        if (resolveIt) resolveClass(result)
        classes[className] = result
        monitor(">> Returning newly loaded class.")
        return result
    }

    fun setClassNameReplacementChar(replacement: Char) {
        classNameReplacementChar = replacement
    }

    protected abstract fun loadClassBytes(className: String): ByteArray?

    protected fun formatClassName(className: String): String {
        return if (classNameReplacementChar == '\u0000') {
            className.replace('.', '/') + ".class"
        } else {
            className.replace('.', classNameReplacementChar) + ".class"
        }
    }

    protected fun monitor(text: String) {
        if (monitorOn) print(text)
    }

    companion object {
        @JvmStatic
        protected fun print(text: String) {
            println(text)
        }
    }
}
