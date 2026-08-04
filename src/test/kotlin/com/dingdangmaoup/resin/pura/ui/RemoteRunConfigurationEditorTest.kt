package com.dingdangmaoup.resin.pura.ui

import com.dingdangmaoup.resin.pura.resin.jmx.JmxCredentialEdit
import com.dingdangmaoup.resin.pura.ResinRemoteModel
import com.intellij.openapi.options.ConfigurationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteRunConfigurationEditorTest {
    @Test
    fun `persistent side effects are limited to the model used to reset the editor`() {
        val editorModel = ResinRemoteModel()
        val snapshotModel = ResinRemoteModel()
        var sideEffects = 0

        assertTrue(RemoteRunConfigurationEditor.isPersistentApply(editorModel, editorModel))
        assertEquals(false, RemoteRunConfigurationEditor.isPersistentApply(editorModel, snapshotModel))

        RemoteRunConfigurationEditor.runPersistentSideEffect(editorModel, snapshotModel) {
            sideEffects++
        }
        assertEquals(0, sideEffects)

        RemoteRunConfigurationEditor.runPersistentSideEffect(editorModel, editorModel) {
            sideEffects++
        }
        assertEquals(1, sideEffects)
    }

    @Test
    fun `credential editor distinguishes keep replace and clear`() {
        assertSame(
            JmxCredentialEdit.Keep,
            RemoteRunConfigurationEditor.parseCredentialEdit("", "", false),
        )
        assertSame(
            JmxCredentialEdit.Clear,
            RemoteRunConfigurationEditor.parseCredentialEdit("", "", true),
        )

        val replace = RemoteRunConfigurationEditor.parseCredentialEdit(" resin-admin ", "secret", false)
        assertTrue(replace is JmxCredentialEdit.Replace)
        replace as JmxCredentialEdit.Replace
        assertEquals("resin-admin", replace.credentials.username)
        assertEquals("secret", replace.credentials.password)
    }

    @Test
    fun `credential editor rejects incomplete and conflicting input`() {
        assertThrows(ConfigurationException::class.java) {
            RemoteRunConfigurationEditor.parseCredentialEdit("resin-admin", "", false)
        }
        assertThrows(ConfigurationException::class.java) {
            RemoteRunConfigurationEditor.parseCredentialEdit("", "secret", false)
        }
        assertThrows(ConfigurationException::class.java) {
            RemoteRunConfigurationEditor.parseCredentialEdit("resin-admin", "secret", true)
        }
    }
}
