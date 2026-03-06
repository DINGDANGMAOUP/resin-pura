package com.dingdangmaoup.resin.pura.resin.configuration

import com.intellij.execution.ExecutionException
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.NullableLazyValue
import org.jdom.Element
import org.jdom.JDOMException
import java.io.File
import java.io.IOException

class ResinConfigImport(private val myRoot: Element) {
    private val myImportDoc: NullableLazyValue<Element> = object : NullableLazyValue<Element>() {
        override fun compute(): Element? {
            val path = myRoot.getAttributeValue(ResinXmlConfigurationStrategy.IMPORT_SINGLE_PATH_ATTRIBUTE)
            return try {
                JDOMUtil.load(File(path))
            } catch (e: JDOMException) {
                LOG.debug(e)
                null
            } catch (e: IOException) {
                LOG.debug(e)
                null
            }
        }
    }

    private var myCopy: ResinGeneratedConfig? = null
    private var myCopyException: ExecutionException? = null

    fun getImportDoc(): Element? = myImportDoc.value

    fun copy() {
        if (myCopy == null && myCopyException == null) {
            try {
                myCopy = ResinGeneratedConfig(getImportDoc(), "resin-import")
                myRoot.setAttribute(
                    ResinXmlConfigurationStrategy.IMPORT_SINGLE_PATH_ATTRIBUTE,
                    myCopy!!.getFile().absolutePath,
                )
            } catch (e: ExecutionException) {
                myCopyException = e
            }
        }
    }

    @Throws(ExecutionException::class)
    fun save() {
        if (myCopyException != null) {
            throw myCopyException as ExecutionException
        }
        myCopy?.save()
    }

    companion object {
        private val LOG = Logger.getInstance(ResinConfigImport::class.java)
    }
}
