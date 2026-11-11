package com.vibespace.core.language

import com.vibespace.core.language.lsp.WorkspaceLspUriMapper
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class WorkspaceLspUriMapperTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun mapsFilesystemUrisToAndFromTheAlpineMount() {
        val root = temporaryFolder.newFolder("workspace")
        val source = File(root, "src/main.py").apply {
            parentFile?.mkdirs()
            writeText("print('ok')")
        }
        val mapper = WorkspaceLspUriMapper(root.toURI().toString(), root)

        assertEquals("file:///workspace/src/main.py", mapper.toServerUri(source.toURI().toString()))
        assertEquals(source.toURI().toString(), mapper.toClientUri("file:///workspace/src/main.py"))
    }

    @Test
    fun mapsSafDocumentUrisWithoutAssumingTheyArePathPrefixes() {
        val root = temporaryFolder.newFolder("saf-mirror")
        val documentUri = "content://provider/document/tree%3Aproject%2Fsrc%2Fmain.py"
        val relativeToUri = mapOf("src/main.py" to documentUri)
        val uriToRelative = mapOf(documentUri to "src/main.py")
        val mapper = WorkspaceLspUriMapper(
            workspaceRootUri = "content://provider/tree/tree%3Aproject",
            workspaceDirectory = root,
            documentUriToRelativePath = uriToRelative::get,
            relativePathToDocumentUri = relativeToUri::get
        )

        assertEquals("file:///workspace", mapper.toServerUri("content://provider/tree/tree%3Aproject"))
        assertEquals("file:///workspace/src/main.py", mapper.toServerUri(documentUri))
        assertEquals(documentUri, mapper.toClientUri("file:///workspace/src/main.py"))
    }

    @Test
    fun leavesUrisOutsideTheWorkspaceUnmapped() {
        val root = temporaryFolder.newFolder("outside-workspace")
        val mapper = WorkspaceLspUriMapper("vibe://project", root)

        assertEquals("content://other/document/outside.py", mapper.toServerUri("content://other/document/outside.py"))
        assertEquals("file:///tmp/outside.py", mapper.toClientUri("file:///tmp/outside.py"))
    }
}
