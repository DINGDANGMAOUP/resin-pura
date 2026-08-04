package com.dingdangmaoup.resin.pura.resin.configuration

import com.dingdangmaoup.resin.pura.ResinModel
import com.dingdangmaoup.resin.pura.resin.ResinInstallation
import com.intellij.execution.ExecutionException
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import org.jdom.Element
import java.io.File
import java.io.InputStream

open class ResinXmlConfigurationStrategy(resinInstallation: ResinInstallation) : Resin31ConfigurationStrategy(resinInstallation) {
    private var myImports: MutableList<ResinConfigImport> = ArrayList()

    @Throws(ExecutionException::class)
    override fun init(serverModel: ResinModel, element: Element) {
        super.init(serverModel, element)
        myImports = ArrayList()
        resolveImports(element, myImports)
    }

    override fun getDefaultResinConfContent(): InputStream? = javaClass.getResourceAsStream(RESIN_CONF)

    protected fun resolveImports(rootElement: Element, imports: MutableList<ResinConfigImport>?) {

        val confFolder = File(getInstallation().getResinHome(), "conf")
        val confFolderPath = FileUtil.toSystemIndependentName(confFolder.absolutePath)
        val elements = findImportElements(rootElement)
        for (importAttrName in IMPORT_ATTRIBUTE_NAMES) {
            for (element in elements) {
                val path = element.getAttributeValue(importAttrName) ?: continue
                element.setAttribute(importAttrName, StringUtil.replace(path, CONF_FOLDER_VAR, confFolderPath))
                if (imports != null && StringUtil.equals(IMPORT_SINGLE_PATH_ATTRIBUTE, importAttrName)) {
                    imports.add(ResinConfigImport(element))
                }
            }
        }
    }

    protected fun getImports(): List<ResinConfigImport> = myImports

    @Throws(ExecutionException::class)
    override fun save() {
        for (configImport in getImports()) {
            configImport.save()
        }
    }

    companion object {
        const val IMPORT_SINGLE_PATH_ATTRIBUTE = "path"
        private val IMPORT_ATTRIBUTE_NAMES = arrayOf(IMPORT_SINGLE_PATH_ATTRIBUTE, "fileset")
        private val IMPORT_ELEMENT_NAMES = setOf("resin:import", "resin:properties")
        private const val CONF_FOLDER_VAR = "\${__DIR__}"
        protected const val RESIN_CONF = "resin32.xml"

        internal fun findImportElements(rootElement: Element): List<Element> {
            return rootElement.getDescendants()
                .asSequence()
                .filterIsInstance<Element>()
                .filter { it.getQualifiedName() in IMPORT_ELEMENT_NAMES }
                .toList()
        }
    }
}
