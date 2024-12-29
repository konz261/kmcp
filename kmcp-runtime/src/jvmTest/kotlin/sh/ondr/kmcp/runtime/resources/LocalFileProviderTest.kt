package sh.ondr.kmcp.runtime.resources

import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import sh.ondr.kmcp.schema.resources.ResourceContents

class LocalFileProviderTest {
	@Test
	fun testDiscreteMode() =
		runBlocking {
			// Given a FakeFileSystem and a root directory in memory
			val fileSystem = FakeFileSystem()
			val rootDir = "/discreteRoot".toPath()
			fileSystem.createDirectories(rootDir)

			// Add a test file "Main.kt" at rootDir
			val fileName = "Main.kt"
			val fileContent = "println(lala)"
			val filePath = rootDir.resolve(fileName)
			fileSystem.write(filePath) {
				writeUtf8(fileContent)
			}

			// Known files: we only know about "Main.kt"
			val knownFiles = listOf(fileName)

			// Create the LocalFileProvider in DISCRETE mode
			val provider = LocalFileProvider(
				fileSystem = fileSystem,
				rootDir = rootDir,
				fileProviderMode = LocalFileProviderMode.DISCRETE,
				knownFiles = knownFiles,
			)

			// When we list the resources
			val resources = provider.listResources()

			// Then we expect one resource
			assertEquals(1, resources.size)
			val resource = resources.first()
			assertEquals("Main.kt", resource.name)
			assertEquals("file://Main.kt", resource.uri)

			// When we read the resource
			val contents = provider.readResource(resource.uri)
			assertNotNull(contents)
			when (contents) {
				is ResourceContents.Text -> {
					// Check that the text matches
					assertEquals("println(lala)", contents.text)
					assertEquals("file://Main.kt", contents.uri)
				}
				else -> error("Expected text contents")
			}
		}

	@Test
	fun testNoFiles() =
		runBlocking {
			// Given a FakeFileSystem with no known files
			val fileSystem = FakeFileSystem()
			val rootDir = "/noFilesRoot".toPath()
			fileSystem.createDirectories(rootDir)

			val provider = LocalFileProvider(
				fileSystem = fileSystem,
				rootDir = rootDir,
				fileProviderMode = LocalFileProviderMode.DISCRETE,
				knownFiles = emptyList(),
			)

			// Listing should return empty
			val resources = provider.listResources()
			assertEquals(0, resources.size)
		}

	@Test
	fun testTemplateMode() =
		runBlocking {
			// Given a FakeFileSystem in template mode
			val fileSystem = FakeFileSystem()
			val rootDir = "/templateRoot".toPath()
			fileSystem.createDirectories(rootDir)

			// Create a file that isn't "known"
			val fileName = "secret.txt"
			val content = "Top secret!"
			fileSystem.write(rootDir.resolve(fileName)) {
				writeUtf8(content)
			}

			val provider = LocalFileProvider(
				fileSystem = fileSystem,
				rootDir = rootDir,
				fileProviderMode = LocalFileProviderMode.TEMPLATE,
				knownFiles = emptyList(),
			)

			// In template mode, listResources should return empty,
			// but listResourceTemplates should return one template.
			val templates = provider.listResourceTemplates()
			assertEquals(1, templates.size)
			val template = templates.first()
			assertEquals("file:///{path}", template.uriTemplate)

			// Now, if the client reads a resource using the template scheme,
			// e.g. "file://secret.txt"
			val uri = "file://secret.txt"
			val contents = provider.readResource(uri)
			assertNotNull(contents)
			when (contents) {
				is ResourceContents.Text -> {
					assertEquals(content, contents.text)
					assertEquals(uri, contents.uri)
				}
				else -> error("Expected text contents")
			}
		}
}
