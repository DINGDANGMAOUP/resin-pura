package com.dingdangmaoup.resin.pura.resin.version

class JarClassLoader(jarName: String) : MultiClassLoader() {
    private val jarResources: JarResources = JarResources(jarName, false)

    override fun loadClassBytes(className: String): ByteArray? {
        val formatted = formatClassName(className)
        return jarResources.getResource(formatted)
    }
}
