package com.vibespace.core.workspace

import com.vibespace.core.common.dispatchers.TestDispatcherProvider
import com.vibespace.core.common.result.VibeResult
import com.vibespace.core.workspace.git.JGitWorkspaceManager
import com.vibespace.core.workspace.manager.DefaultWorkspaceManager
import com.vibespace.core.workspace.model.Project
import kotlinx.coroutines.test.runTest
import org.eclipse.jgit.lib.StoredConfig
import org.eclipse.jgit.storage.file.FileBasedConfig
import org.eclipse.jgit.util.FS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class JGitWorkspaceManagerAdversarialTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private suspend fun setupEnvironment(): Triple<File, DefaultWorkspaceManager, JGitWorkspaceManager> {
        val root = temporaryFolder.newFolder("workspace_${System.nanoTime()}")
        val source = File(root, "src/main.py")
        source.parentFile?.mkdirs()
        source.writeText("print('hello')\n")

        val dispatchers = TestDispatcherProvider()
        val workspace = DefaultWorkspaceManager(dispatchers = dispatchers)
        val project = Project("git-proj-${System.nanoTime()}", "Git project", root.toURI().toString())
        workspace.openProject(project)

        val storageDir = temporaryFolder.newFolder("repositories_${System.nanoTime()}")
        val git = JGitWorkspaceManager(
            workspaceManager = workspace,
            storageDirectory = storageDir,
            dispatchers = dispatchers
        )
        return Triple(storageDir, workspace, git)
    }

    private fun findGitConfigFile(storageDir: File): File {
        val repoDirs = storageDir.listFiles { f -> f.isDirectory } ?: emptyArray()
        assertTrue("Expected repo directory in storageDir", repoDirs.isNotEmpty())
        val gitConfig = File(repoDirs[0], ".git/config")
        assertTrue("Expected .git/config to exist: ${gitConfig.path}", gitConfig.exists())
        return gitConfig
    }

    @Test
    fun unconfiguredIdentity_commitSafelyFailsWithoutCrashing() = runTest {
        val (storageDir, _, git) = setupEnvironment()

        val status = git.refreshStatus()
        assertTrue(status is VibeResult.Success)
        val data = (status as VibeResult.Success).data
        assertNull("authorName must be null without local identity", data.authorName)
        assertNull("authorEmail must be null without local identity", data.authorEmail)

        assertTrue(git.stageAll().isSuccess)
        val commitResult = git.commit("Initial commit")
        assertTrue("Commit must fail when identity is unconfigured", commitResult is VibeResult.Error)
        val errorMsg = (commitResult as VibeResult.Error).message
        assertTrue(
            "Error must guide user to configure name and email, was: $errorMsg",
            errorMsg.contains("Configure your Git author name and email")
        )
    }

    @Test
    fun gitConfigWithUserNameButEmptyEmail_commitSafelyFails() = runTest {
        val (storageDir, _, git) = setupEnvironment()
        // Initialize the repo
        git.refreshStatus()

        val configFile = findGitConfigFile(storageDir)
        val config: StoredConfig = FileBasedConfig(configFile, FS.DETECTED)
        config.load()
        config.setString("user", null, "name", "Valid Developer")
        config.setString("user", null, "email", "")
        config.save()

        val status = git.refreshStatus()
        assertTrue(status is VibeResult.Success)
        val data = (status as VibeResult.Success).data
        assertEquals("Valid Developer", data.authorName)
        assertTrue("authorEmail must be null or empty", data.authorEmail.isNullOrEmpty())

        assertTrue(git.stageAll().isSuccess)
        val commitResult = git.commit("Commit with empty email")
        assertTrue("Commit must fail when email is empty string", commitResult is VibeResult.Error)
        val errorMsg = (commitResult as VibeResult.Error).message
        assertTrue(
            "Error message must mention configuring identity, was: $errorMsg",
            errorMsg.contains("Configure your Git author name and email")
        )
    }

    @Test
    fun gitConfigWithWhitespaceOnlyName_commitSafelyFails() = runTest {
        val (storageDir, _, git) = setupEnvironment()
        git.refreshStatus()

        val configFile = findGitConfigFile(storageDir)
        val config: StoredConfig = FileBasedConfig(configFile, FS.DETECTED)
        config.load()
        config.setString("user", null, "name", "   \t  ")
        config.setString("user", null, "email", "dev@example.com")
        config.save()

        assertTrue(git.stageAll().isSuccess)
        val commitResult = git.commit("Commit with whitespace-only name")
        assertTrue("Commit must fail when name is whitespace only", commitResult is VibeResult.Error)
        val errorMsg = (commitResult as VibeResult.Error).message
        assertTrue(
            "Error message must mention configuring identity, was: $errorMsg",
            errorMsg.contains("Configure your Git author name and email")
        )
    }

    @Test
    fun gitConfigWithWhitespaceOnlyEmail_commitSafelyFails() = runTest {
        val (storageDir, _, git) = setupEnvironment()
        git.refreshStatus()

        val configFile = findGitConfigFile(storageDir)
        val config: StoredConfig = FileBasedConfig(configFile, FS.DETECTED)
        config.load()
        config.setString("user", null, "name", "Valid Dev")
        config.setString("user", null, "email", "   ")
        config.save()

        assertTrue(git.stageAll().isSuccess)
        val commitResult = git.commit("Commit with whitespace-only email")
        assertTrue("Commit must fail when email is whitespace only", commitResult is VibeResult.Error)
        val errorMsg = (commitResult as VibeResult.Error).message
        assertTrue(
            "Error message must mention configuring identity, was: $errorMsg",
            errorMsg.contains("Configure your Git author name and email")
        )
    }

    @Test
    fun configureIdentityValidatesInputSafely() = runTest {
        val (_, _, git) = setupEnvironment()
        git.refreshStatus()

        // Empty/whitespace name rejected
        assertTrue(git.configureIdentity("", "dev@example.com") is VibeResult.Error)
        assertTrue(git.configureIdentity("   ", "dev@example.com") is VibeResult.Error)

        // Invalid email format rejected
        assertTrue(git.configureIdentity("Valid Dev", "") is VibeResult.Error)
        assertTrue(git.configureIdentity("Valid Dev", "not-an-email") is VibeResult.Error)
        assertTrue(git.configureIdentity("Valid Dev", "@missinguser.com") is VibeResult.Error)

        // Valid configuration succeeds and enables commit
        assertTrue(git.configureIdentity("Real Dev", "real@example.com").isSuccess)
        assertTrue(git.stageAll().isSuccess)
        val commitResult = git.commit("Valid commit")
        assertTrue("Commit must succeed with valid identity", commitResult is VibeResult.Success)
    }
}
