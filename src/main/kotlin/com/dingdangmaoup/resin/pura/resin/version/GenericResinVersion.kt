package com.dingdangmaoup.resin.pura.resin.version

import java.io.File

class GenericResinVersion(
    name: String,
    versionNumber: String,
    private val myStartupClass: String,
    private val myAllowXdebug: Boolean,
    private val myAllowJmx: Boolean,
) : ResinVersion(name, versionNumber) {
    override fun isOfVersion(resinHome: File): Boolean {
        val ver = ResinVersionDetector.getResinVersion(resinHome)
        return this == ver
    }

    override fun getStartupClass(): String = myStartupClass

    override fun allowXdebug(): Boolean = myAllowXdebug

    override fun allowJmx(): Boolean = myAllowJmx
}
