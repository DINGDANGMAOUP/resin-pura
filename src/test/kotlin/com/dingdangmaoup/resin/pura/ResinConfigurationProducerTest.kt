package com.dingdangmaoup.resin.pura

import com.intellij.execution.BeforeRunTask
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.javaee.appServers.appServerIntegrations.ApplicationServer
import com.intellij.javaee.appServers.run.configuration.CommonModel
import com.intellij.javaee.appServers.run.configuration.ServerModel
import com.intellij.openapi.util.Key
import com.intellij.packaging.artifacts.Artifact
import org.jdom.Element
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

class ResinConfigurationProducerTest {
    @Test
    fun `generated settings retain state name parallel policy and independent build tasks`() {
        val buildTask = TestBuildTask().apply { isEnabled = true }
        val source = ConfigurationState().apply {
            name = "index.jsp"
            state = Element("configuration")
                .setAttribute("server", "Resin")
                .setAttribute("url", "http://localhost:8080/app/index.jsp")
                .addContent(Element("deployment").setAttribute("artifact", "app:war exploded"))
            tasks = listOf(buildTask)
            parallel = true
        }
        val target = ConfigurationState()

        ResinConfigurationProducer.copyGeneratedConfiguration(source.configuration, target.configuration)

        assertEquals(source.name, target.name)
        assertEquals("Resin", target.state.getAttributeValue("server"))
        assertEquals(source.state.getAttributeValue("url"), target.state.getAttributeValue("url"))
        assertEquals("app:war exploded", target.state.getChild("deployment").getAttributeValue("artifact"))
        assertTrue(target.parallel)
        assertTrue(target.tasks.single().isEnabled)
        assertNotSame(buildTask, target.tasks.single())
        target.tasks.single().isEnabled = false
        target.state.setAttribute("server", "Changed")
        assertTrue(buildTask.isEnabled)
        assertEquals("Resin", source.state.getAttributeValue("server"))
    }

    @Test
    fun `matching context reuses a configuration with a customized name and model options`() {
        val server = identity<ApplicationServer>()
        val artifact = identity<Artifact>()
        val generated = model(server, artifacts = listOf(artifact))
        val existing = model(server, artifacts = listOf(artifact), name = "My server", resin = ResinModel().apply {
            charset = "UTF-8"
            jmxPort = 12345
        })

        assertTrue(ResinConfigurationProducer.matchesGeneratedConfiguration(existing, generated))
    }

    @Test
    fun `different server browser target or deployment artifact is not reused`() {
        val server = identity<ApplicationServer>()
        val artifact = identity<Artifact>()
        val generated = model(server, artifacts = listOf(artifact))

        assertFalse(matches(model(identity(), artifacts = listOf(artifact)), generated))
        assertFalse(matches(model(server, url = "http://localhost:8080/other.jsp", artifacts = listOf(artifact)), generated))
        assertFalse(matches(model(server, artifacts = listOf(identity())), generated))
        assertFalse(matches(model(server, artifacts = listOf(artifact, identity())), generated))
    }

    @Test
    fun `remote models and incomplete targets are not reused`() {
        val server = identity<ApplicationServer>()
        val generated = model(server)

        assertFalse(matches(model(server, local = false), generated))
        assertFalse(matches(model(server, resin = ResinRemoteModel()), generated))
        assertFalse(matches(model(null), model(null)))
        assertFalse(matches(model(server, url = ""), model(server, url = "")))
    }

    @Test
    fun `deployment ordering does not cause duplicate configurations`() {
        val server = identity<ApplicationServer>()
        val first = identity<Artifact>()
        val second = identity<Artifact>()

        assertTrue(matches(model(server, artifacts = listOf(first, second)), model(server, artifacts = listOf(second, first))))
    }

    private fun matches(existing: CommonModel, generated: CommonModel) =
        ResinConfigurationProducer.matchesGeneratedConfiguration(existing, generated)

    private fun model(
        server: ApplicationServer?,
        url: String = "http://localhost:8080/app/index.jsp",
        artifacts: List<Artifact> = emptyList(),
        local: Boolean = true,
        name: String = "index.jsp",
        resin: ServerModel = ResinModel(),
    ): CommonModel = proxy { method, _ ->
        when (method) {
            "getApplicationServer" -> server
            "getUrlToOpenInBrowser" -> url
            "getDeployedArtifacts" -> artifacts
            "isLocal" -> local
            "getServerModel" -> resin
            "getName" -> name
            else -> error("Unexpected model call: $method")
        }
    }

    private class TestBuildTask : BeforeRunTask<TestBuildTask>(Key.create("resin-producer-test-build"))

    private class ConfigurationState {
        var state = Element("configuration")
        var name = "Template"
        var tasks: List<BeforeRunTask<*>> = emptyList()
        var parallel = false
        val configuration: RunConfiguration = proxy { method, args ->
            when (method) {
                "writeExternal" -> (args[0] as Element).apply {
                    state.attributes.forEach { setAttribute(it.clone()) }
                    state.children.forEach { addContent(it.clone()) }
                }
                "readExternal" -> { state = (args[0] as Element).clone(); null }
                "getName" -> name
                "setName" -> { name = args[0] as String; null }
                "getBeforeRunTasks" -> tasks
                "setBeforeRunTasks" -> {
                    @Suppress("UNCHECKED_CAST")
                    tasks = args[0] as List<BeforeRunTask<*>>
                    null
                }
                "isAllowRunningInParallel" -> parallel
                "setAllowRunningInParallel" -> { parallel = args[0] as Boolean; null }
                else -> error("Unexpected configuration call: $method")
            }
        }
    }

    companion object {
        private inline fun <reified T> identity(): T = proxy { method, _ -> error("Unexpected identity call: $method") }

        private inline fun <reified T> proxy(crossinline invoke: (String, Array<out Any?>) -> Any?): T =
            Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { self, method, args ->
                when (method.name) {
                    "equals" -> self === args?.get(0)
                    "hashCode" -> System.identityHashCode(self)
                    "toString" -> T::class.java.simpleName
                    else -> invoke(method.name, args ?: emptyArray())
                }
            } as T
    }
}
