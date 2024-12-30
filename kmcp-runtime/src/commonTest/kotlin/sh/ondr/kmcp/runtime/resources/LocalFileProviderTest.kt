@file:OptIn(ExperimentalCoroutinesApi::class)

package sh.ondr.kmcp.runtime.resources

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import sh.ondr.kmcp.assertLinesMatch
import sh.ondr.kmcp.client
import sh.ondr.kmcp.logLines
import sh.ondr.kmcp.runtime.Client
import sh.ondr.kmcp.runtime.Server
import sh.ondr.kmcp.runtime.transport.TestTransport
import sh.ondr.kmcp.schema.core.JsonRpcErrorCodes
import sh.ondr.kmcp.schema.resources.ReadResourceRequest
import sh.ondr.kmcp.schema.resources.ResourceContents
import sh.ondr.kmcp.server
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class LocalFileProviderTest {
	@Test
	fun testDiscreteMode() =
		runTest {
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
		runTest {
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
		runTest {
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

	@Test
	fun testTemplateModeInvalidUri() =
		runTest {
			// Setup
			val fileSystem = FakeFileSystem()
			val rootDir = "/templateRoot".toPath()
			fileSystem.createDirectories(rootDir)

			// Putting a real file in there
			fileSystem.write(rootDir.resolve("secret.txt")) {
				writeUtf8("Top secret!")
			}

			// Create the LocalFileProvider in TEMPLATE mode
			val provider = LocalFileProvider(
				fileSystem = fileSystem,
				rootDir = rootDir,
				fileProviderMode = LocalFileProviderMode.TEMPLATE,
				knownFiles = emptyList(),
			)

			// If we read a resource "http://invalid.txt", the scheme is not "file://",
			// so we expect null from readResource.
			val invalidUri = "http://invalid.txt"
			val contents1 = provider.readResource(invalidUri)
			assertEquals(null, contents1)

			// If we try "file://" but no relative path, we also expect null:
			val emptyUri = "file://"
			val contents2 = provider.readResource(emptyUri)
			assertEquals(null, contents2)

			// If we try "file://nonexistentSubDir/secret.txt", it
			// returns null because the path doesn't exist:
			val nonExistentUri = "file://nonexistentSubDir/secret.txt"
			val contents3 = provider.readResource(nonExistentUri)
			assertEquals(null, contents3)
		}

	@Test
	fun testTemplateModeInvalidUriInServer() =
		runTest {
			val testDispatcher = StandardTestDispatcher(testScheduler)
			val log = mutableListOf<String>()

			// File system with one real file
			val fs = FakeFileSystem()
			val rootDir = "/templateRoot".toPath()
			fs.createDirectories(rootDir)
			fs.write(rootDir.resolve("secret.txt")) {
				writeUtf8("Top secret!")
			}

			// Create provider in TEMPLATE mode
			val provider = LocalFileProvider(
				fileSystem = fs,
				rootDir = rootDir,
				fileProviderMode = LocalFileProviderMode.TEMPLATE,
				knownFiles = emptyList(),
			)

			// Build server with that provider
			val (clientTransport, serverTransport) = TestTransport.Companion.createClientAndServerTransport()
			val server = Server.Builder()
				.withDispatcher(testDispatcher)
				.withLogger { line -> log.server(line) }
				.withTransport(serverTransport)
				.withResourceProvider(provider)
				.build()
			server.start()

			// Build client
			val client = Client.Builder()
				.withTransport(clientTransport)
				.withDispatcher(testDispatcher)
				.withLogger { line -> log.client(line) }
				.withClientInfo("TestClient", "1.0.0")
				.build()
			client.start()

			// 1) Initialization
			client.initialize()
			advanceUntilIdle()
			log.clear()

			// 2) Request a totally invalid URI
			val response = client.sendRequest { reqId ->
				ReadResourceRequest(
					id = reqId,
					params = ReadResourceRequest.ReadResourceParams(uri = "http://invalid.txt"),
				)
			}
			advanceUntilIdle()

			// 3) The server should return JSON-RPC error -32002 or -32602 etc.
			assertNotNull(response.error)
			assertEquals(JsonRpcErrorCodes.RESOURCE_NOT_FOUND, response.error.code)

			// 4) Check logs
			val expectedLogs = logLines {
				clientOutgoing("""{"method":"resources/read","jsonrpc":"2.0","id":"2","params":{"uri":"http://invalid.txt"}}""")
				serverIncoming("""{"method":"resources/read","jsonrpc":"2.0","id":"2","params":{"uri":"http://invalid.txt"}}""")
				serverOutgoing("""{"jsonrpc":"2.0","id":"2","error":{"code":-32002,"message":"Resource not found: http://invalid.txt"}}""")
				clientIncoming("""{"jsonrpc":"2.0","id":"2","error":{"code":-32002,"message":"Resource not found: http://invalid.txt"}}""")
			}
			assertLinesMatch(expectedLogs, log, "invalid template uri test")
		}
}
