package com.dingdangmaoup.resin.pura.resin.common

import javax.management.MalformedObjectNameException
import javax.management.ObjectName

object MBeanUtil {
    @JvmStatic
    fun newObjectName(objectName: String): ObjectName {
        return try {
            ObjectName.getInstance(objectName)
        } catch (_: MalformedObjectNameException) {
            throw InternalError("Never happens")
        }
    }
}
