package com.vibespace.core.terminal

import com.vibespace.core.common.dispatchers.TestDispatcherProvider
import com.vibespace.core.terminal.model.StreamType
import com.vibespace.core.terminal.service.DefaultTerminalService
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultTerminalServiceTest {

    private val service = DefaultTerminalService(dispatchers = TestDispatcherProvider())

    @Test
    fun createSession_initializes_welcome_lines_and_sets_active() = runTest {
        val result = service.createSession("Bash", "/sdcard/projects")
        assertTrue(result.isSuccess)
        val session = result.getOrNull()
        assertNotNull(session)
        assertEquals("Bash", session!!.title)
        assertEquals("/sdcard/projects", session.workingDirectory)
        assertTrue(session.lines.isNotEmpty())
        assertEquals(session, service.activeSession.value)
    }

    @Test
    fun sendInput_echo_outputs_correctly() = runTest {
        val session = service.createSession("Console", "~").getOrNull()!!
        service.sendInput(session.sessionId, "echo Hello VibeSpace")

        val updated = service.sessions.value.first { it.sessionId == session.sessionId }
        assertTrue(updated.lines.any { it.text == "$ echo Hello VibeSpace" })
        assertTrue(updated.lines.any { it.text == "Hello VibeSpace" && it.streamType == StreamType.STDOUT })
    }

    @Test
    fun clearSession_empties_lines() = runTest {
        val session = service.createSession("Console", "~").getOrNull()!!
        service.clearSession(session.sessionId)

        val updated = service.sessions.value.first { it.sessionId == session.sessionId }
        assertTrue(updated.lines.isEmpty())
    }

    @Test
    fun sendInput_builtins_work_properly() = runTest {
        val session = service.createSession("Console", "~").getOrNull()!!
        service.sendInput(session.sessionId, "pwd")
        service.sendInput(session.sessionId, "which grep")
        service.sendInput(session.sessionId, "which sh")

        val updated = service.sessions.value.first { it.sessionId == session.sessionId }
        assertTrue(updated.lines.any { it.text.contains("/bin/grep") })
        assertTrue(updated.lines.any { it.text.contains("/bin/sh") })
    }

    @Test
    fun sendInput_supports_quoted_cd_redirection_and_linux_commands() = runTest {
        val tempRoot = java.nio.file.Files.createTempDirectory("term-linux-test").toFile()
        try {
            val session = service.createSession("Console", tempRoot.absolutePath).getOrNull()!!
            service.sendInput(session.sessionId, "mkdir hi")
            service.sendInput(session.sessionId, "cd \"${java.io.File(tempRoot, "hi").absolutePath}\"")
            service.sendInput(session.sessionId, "echo 'hello world' > msg.txt && cat msg.txt")
            service.sendInput(session.sessionId, "seq 1 3")
            service.sendInput(session.sessionId, "expr 6 + 7")

            val updated = service.sessions.value.first { it.sessionId == session.sessionId }
            assertEquals(java.io.File(tempRoot, "hi").canonicalPath, updated.workingDirectory)
            assertTrue(updated.lines.none { it.streamType == StreamType.STDERR })
            assertTrue(updated.lines.any { it.text == "hello world" && it.streamType == StreamType.STDOUT })
            assertTrue(updated.lines.any { it.text == "1\n2\n3" && it.streamType == StreamType.STDOUT })
            assertTrue(updated.lines.any { it.text == "13" && it.streamType == StreamType.STDOUT })
        } finally {
            tempRoot.deleteRecursively()
        }
    }
}
