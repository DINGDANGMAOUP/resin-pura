package com.dingdangmaoup.resin.pura.resin.configuration

import com.dingdangmaoup.resin.pura.ResinModel
import com.dingdangmaoup.resin.pura.resin.ResinInstallation
import com.dingdangmaoup.resin.pura.resin.WebApp
import org.jdom.Element
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class HostScopedDeploymentTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun `all XML strategies only remove the requested virtual host`() {
        val home = temp.newFolder("resin")
        home.resolve("bin").mkdir(); home.resolve("lib").mkdir()
        val installation = ResinInstallation.create(home.path)
        val strategies = listOf(
            Resin2XConfigurationStrategy() to "http-server",
            Resin3XConfigurationStrategy(installation) to "server",
            Resin31ConfigurationStrategy(installation) to "cluster",
            ResinXmlConfigurationStrategy(installation) to "cluster",
            Resin4XmlConfigurationStrategy(installation) to "cluster",
        )
        for ((strategy, parentName) in strategies) {
            val root = Element("resin")
            val parent = Element(parentName).addContent(Element("server-default"))
            root.addContent(parent)
            val a = host("a.example")
            val b = host("b.example")
            parent.addContent(a); parent.addContent(b)
            // Resin 4 init consults runtime credentials; initialize the XML root directly for this inherited operation.
            val field = ResinConfigurationStrategy::class.java.getDeclaredField("myElement")
            field.isAccessible = true; field.set(strategy, root)
            val webApp = WebApp(false, "/app", "a.example", "/tmp/app.war", null)
            assertTrue(strategy.javaClass.name, strategy.undeploy(webApp))
            assertTrue(a.getChildren("web-app").isEmpty())
            assertEquals(1, b.getChildren("web-app").size)
            assertFalse(strategy.undeploy(webApp))
            assertFalse(strategy.undeploy(WebApp(false, "/app", "missing", "/tmp/app.war", null)))
            assertEquals(2, parent.getChildren("host").size)
        }
    }

    @Test fun `exact host wins over regexp and default host remains distinct`() {
        val regexp = Element("host").setAttribute("regexp", ".*")
        val exact = host("a.example")
        val default = host("")
        val hosts = listOf(regexp, exact, default)
        assertSame(exact, HostSelector.find(hosts, "a.example"))
        assertSame(default, HostSelector.find(hosts, ""))
        assertSame(regexp, HostSelector.find(hosts, "other.example"))
        assertNull(HostSelector.find(hosts, "other.example", allowRegexp = false))
    }

    private fun host(id: String) = Element("host").setAttribute("id", id)
        .addContent(Element("web-app").setAttribute("id", "/app"))
}
