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
import sh.ondr.kmcp.runtime.serialization.deserializeResult
import sh.ondr.kmcp.runtime.transport.TestTransport
import sh.ondr.kmcp.schema.core.EmptyResult
import sh.ondr.kmcp.schema.core.JsonRpcErrorCodes
import sh.ondr.kmcp.schema.resources.ListResourceTemplatesRequest
import sh.ondr.kmcp.schema.resources.ListResourceTemplatesRequest.ListResourceTemplatesParams
import sh.ondr.kmcp.schema.resources.ListResourceTemplatesResult
import sh.ondr.kmcp.schema.resources.ListResourcesRequest
import sh.ondr.kmcp.schema.resources.ListResourcesRequest.ListResourcesParams
import sh.ondr.kmcp.schema.resources.ListResourcesResult
import sh.ondr.kmcp.schema.resources.ReadResourceRequest
import sh.ondr.kmcp.schema.resources.ReadResourceRequest.ReadResourceParams
import sh.ondr.kmcp.schema.resources.ReadResourceResult
import sh.ondr.kmcp.schema.resources.ResourceContents
import sh.ondr.kmcp.schema.resources.SubscribeRequest
import sh.ondr.kmcp.schema.resources.SubscribeRequest.SubscribeParams
import sh.ondr.kmcp.schema.resources.UnsubscribeRequest
import sh.ondr.kmcp.schema.resources.UnsubscribeRequest.UnsubscribeParams
import sh.ondr.kmcp.server
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TemplateFileProviderTest {
	@Test
	fun testListResourcesReturnsEmpty() =
		runTest {
			val fs = FakeFileSystem()
			val rootDir = "/templateRoot".toPath()
			fs.createDirectories(rootDir)

			val provider = TemplateFileProvider(
				fileSystem = fs,
				rootDir = rootDir,
			)

			val resources = provider.listResources()
			assertEquals(0, resources.size, "Expected no discrete resources from a pure template provider")
		}

	@Test
	fun testListResourceTemplates() =
		runTest {
			val fs = FakeFileSystem()
			val rootDir = "/templateRoot".toPath()
			fs.createDirectories(rootDir)

			val provider = TemplateFileProvider(
				fileSystem = fs,
				rootDir = rootDir,
			)

			val templates = provider.listResourceTemplates()
			assertEquals(1, templates.size, "Expected exactly one template from this provider")
			val tpl = templates.first()
			assertEquals("file:///{path}", tpl.uriTemplate)
			assertNotNull(tpl.name, "Should have a 'name' for the template")
			assertNotNull(tpl.description, "Should have a 'description' for the template")
		}

	@Test
	fun testDisallowDotDotEscapes() =
		runTest {
			val fs = FakeFileSystem()
			val rootDir = "/sandbox".toPath()
			fs.createDirectories(rootDir)

			// Also create an "outside" folder
			val outsideDir = "/outside".toPath()
			fs.createDirectories(outsideDir)
			fs.write(outsideDir.resolve("secret.txt")) {
				writeUtf8("Top secret data.")
			}

			val provider = TemplateFileProvider(
				fileSystem = fs,
				rootDir = rootDir,
			)

			// Attempt to read "file:///../outside/secret.txt"
			// This would physically point to /outside/secret.txt if not blocked
			val resource = provider.readResource("file:///../outside/secret.txt")
			assertNull(resource, "Expected null or error because we must disallow ../ path escapes")
		}

	@Test
	fun testReadingDirectoryFails() =
		runTest {
			val fs = FakeFileSystem()
			val rootDir = "/templateRoot".toPath()
			fs.createDirectories(rootDir)

			// Create a subdirectory (instead of a file).
			val subDir = rootDir.resolve("subDir")
			fs.createDirectories(subDir)

			// Build a TemplateFileProvider that uses "file:///{path}"
			val provider = TemplateFileProvider(
				fileSystem = fs,
				rootDir = rootDir,
			)

			// Try to read the directory as if it were a file resource
			// e.g. "file:///subDir"
			val result = provider.readResource("file:///subDir")
			assertNull(result, "Expected null, because subDir is actually a directory, not a file.")
		}

	@Test
	fun testIntegrationReadingFilePreservingSlashes() =
		runTest {
			val dispatcher = StandardTestDispatcher(testScheduler)
			val log = mutableListOf<String>()

			// 1) Prepare an in-memory filesystem with a subdirectory
			val fs = FakeFileSystem()
			val rootDir = "/templateRoot".toPath()
			fs.createDirectories(rootDir)

			// We'll create: /templateRoot/sub/folder/notes.txt
			val subFolder = rootDir.resolve("sub/folder")
			fs.createDirectories(subFolder)
			fs.write(subFolder.resolve("notes.txt")) {
				writeUtf8("Hello from subfolder template!")
			}

			// 2) Build a TemplateFileProvider with e.g. "file:///{path}"
			val provider = TemplateFileProvider(
				fileSystem = fs,
				rootDir = rootDir,
			)

			// 3) Start a server with that provider
			val (clientTransport, serverTransport) = TestTransport.createClientAndServerTransport()
			val server = Server.Builder()
				.withDispatcher(dispatcher)
				.withLogger { line -> log.server(line) }
				.withTransport(serverTransport)
				.withResourceProvider(provider)
				.build()

			server.start()

			// 4) Start a client
			val client = Client.Builder()
				.withTransport(clientTransport)
				.withDispatcher(dispatcher)
				.withLogger { line -> log.client(line) }
				.withClientInfo("TestClient", "1.0.0")
				.build()

			client.start()

			// (A) initialize
			client.initialize()
			advanceUntilIdle()
			log.clear()

			// (B) listResources => expect empty
			val listReq = client.sendRequest { reqId ->
				ListResourcesRequest(
					id = reqId,
					params = ListResourcesParams(),
				)
			}
			advanceUntilIdle()

			val listResp = listReq.result?.deserializeResult<ListResourcesResult>()
			assertNotNull(listResp)
			assertEquals(0, listResp.resources.size, "Template provider should list 0 discrete resources")

			val expectedListLogs = logLines {
				clientOutgoing("""{"method":"resources/list","jsonrpc":"2.0","id":"2","params":{}}""")
				serverIncoming("""{"method":"resources/list","jsonrpc":"2.0","id":"2","params":{}}""")
				serverOutgoing("""{"jsonrpc":"2.0","id":"2","result":{"resources":[]}}""")
				clientIncoming("""{"jsonrpc":"2.0","id":"2","result":{"resources":[]}}""")
			}
			assertLinesMatch(expectedListLogs, log, "template provider listResources logs")
			log.clear()

			// (C) listResourceTemplates => expect 1
			val listTemplatesReq = client.sendRequest { reqId ->
				ListResourceTemplatesRequest(
					id = reqId,
					params = ListResourceTemplatesParams(),
				)
			}
			advanceUntilIdle()

			val listTplResp = listTemplatesReq.result?.deserializeResult<ListResourceTemplatesResult>()
			assertNotNull(listTplResp)
			assertEquals(1, listTplResp.resourceTemplates.size, "One template from the provider")

			val templateEntry = listTplResp.resourceTemplates.first()
			assertEquals("file:///{path}", templateEntry.uriTemplate)

			val expectedTplLogs = logLines {
				clientOutgoing("""{"method":"resources/templates/list","jsonrpc":"2.0","id":"3","params":{}}""")
				serverIncoming("""{"method":"resources/templates/list","jsonrpc":"2.0","id":"3","params":{}}""")
				serverOutgoing(
					"""{"jsonrpc":"2.0","id":"3","result":{"resourceTemplates":[{"uriTemplate":"file:///{path}","name":"Arbitrary local file access","description":"Allows reading any file by specifying {path}"}]}}""",
				)
				clientIncoming(
					"""{"jsonrpc":"2.0","id":"3","result":{"resourceTemplates":[{"uriTemplate":"file:///{path}","name":"Arbitrary local file access","description":"Allows reading any file by specifying {path}"}]}}""",
				)
			}
			assertLinesMatch(expectedTplLogs, log, "list resource templates logs")
			log.clear()

			// (D) read resource from subfolder => "file:///sub/folder/notes.txt"
			val readReq = client.sendRequest { reqId ->
				ReadResourceRequest(
					id = reqId,
					params = ReadResourceParams(uri = "file:///sub/folder/notes.txt"),
				)
			}
			advanceUntilIdle()

			val readResp = readReq.result?.deserializeResult<ReadResourceResult>()
			assertNotNull(readResp)
			val contentsList = readResp.contents
			assertEquals(1, contentsList.size)
			val singleContent = contentsList.first()
			when (singleContent) {
				is ResourceContents.Text -> {
					assertEquals("Hello from subfolder template!", singleContent.text)
				}
				else -> error("Expected text content for the sub/folder/notes.txt file")
			}

			val expectedReadLogs = logLines {
				clientOutgoing("""{"method":"resources/read","jsonrpc":"2.0","id":"4","params":{"uri":"file:///sub/folder/notes.txt"}}""")
				serverIncoming("""{"method":"resources/read","jsonrpc":"2.0","id":"4","params":{"uri":"file:///sub/folder/notes.txt"}}""")
				serverOutgoing(
					"""{"jsonrpc":"2.0","id":"4","result":{"contents":[{"uri":"file:///sub/folder/notes.txt","mimeType":"text/plain","text":"Hello from subfolder template!"}]}}""",
				)
				clientIncoming(
					"""{"jsonrpc":"2.0","id":"4","result":{"contents":[{"uri":"file:///sub/folder/notes.txt","mimeType":"text/plain","text":"Hello from subfolder template!"}]}}""",
				)
			}
			assertLinesMatch(expectedReadLogs, log, "read sub/folder/notes.txt logs")
			log.clear()

			// (E) Try reading nonexistent => expect error -32002
			val invalidReq = client.sendRequest { reqId ->
				ReadResourceRequest(
					id = reqId,
					params = ReadResourceParams(uri = "file:///sub/folder/doesnotexist.txt"),
				)
			}
			advanceUntilIdle()

			val invalidRespError = invalidReq.error
			assertNotNull(invalidRespError, "Expected an error for nonexistent file in TemplateFileProvider")
			assertEquals(JsonRpcErrorCodes.RESOURCE_NOT_FOUND, invalidRespError.code)

			val expectedInvalidLogs = logLines {
				clientOutgoing("""{"method":"resources/read","jsonrpc":"2.0","id":"5","params":{"uri":"file:///sub/folder/doesnotexist.txt"}}""")
				serverIncoming("""{"method":"resources/read","jsonrpc":"2.0","id":"5","params":{"uri":"file:///sub/folder/doesnotexist.txt"}}""")
				serverOutgoing(
					"""{"jsonrpc":"2.0","id":"5","error":{"code":-32002,"message":"Resource not found: file:///sub/folder/doesnotexist.txt"}}""",
				)
				clientIncoming(
					"""{"jsonrpc":"2.0","id":"5","error":{"code":-32002,"message":"Resource not found: file:///sub/folder/doesnotexist.txt"}}""",
				)
			}
			assertLinesMatch(expectedInvalidLogs, log, "read nonexistent file logs")
			log.clear()

			// (F) Subscribe
			val subReq = client.sendRequest { reqId ->
				SubscribeRequest(
					id = reqId,
					params = SubscribeParams(uri = "file:///sub/folder/notes.txt"),
				)
			}
			advanceUntilIdle()

			val subResult = subReq.result?.deserializeResult<EmptyResult>()
			assertNotNull(subResult, "Expected an EmptyResult from subscribe")

			val expectedSubscribeLogs = logLines {
				clientOutgoing("""{"method":"resources/subscribe","jsonrpc":"2.0","id":"6","params":{"uri":"file:///sub/folder/notes.txt"}}""")
				serverIncoming("""{"method":"resources/subscribe","jsonrpc":"2.0","id":"6","params":{"uri":"file:///sub/folder/notes.txt"}}""")
				serverOutgoing("""{"jsonrpc":"2.0","id":"6","result":{}}""")
				clientIncoming("""{"jsonrpc":"2.0","id":"6","result":{}}""")
			}
			assertLinesMatch(expectedSubscribeLogs, log, "subscribe logs")
			log.clear()

			// (G) Notify resource updated
			provider.onResourceChange("file:///sub/folder/notes.txt")
			advanceUntilIdle()

			val expectedNotificationLogs = logLines {
				serverOutgoing("""{"method":"notifications/resources/updated","jsonrpc":"2.0","params":{"uri":"file:///sub/folder/notes.txt"}}""")
				clientIncoming("""{"method":"notifications/resources/updated","jsonrpc":"2.0","params":{"uri":"file:///sub/folder/notes.txt"}}""")
			}
			assertLinesMatch(expectedNotificationLogs, log, "resources/updated notification logs")
			log.clear()

			// (H) Unsubscribe
			val unsubReq = client.sendRequest { reqId ->
				UnsubscribeRequest(
					id = reqId,
					params = UnsubscribeParams(uri = "file:///sub/folder/notes.txt"),
				)
			}
			advanceUntilIdle()

			val unsubResult = unsubReq.result?.deserializeResult<EmptyResult>()
			assertNotNull(unsubResult, "Expected an EmptyResult from unsubscribe")

			val expectedUnsubscribeLogs = logLines {
				clientOutgoing("""{"method":"resources/unsubscribe","jsonrpc":"2.0","id":"7","params":{"uri":"file:///sub/folder/notes.txt"}}""")
				serverIncoming("""{"method":"resources/unsubscribe","jsonrpc":"2.0","id":"7","params":{"uri":"file:///sub/folder/notes.txt"}}""")
				serverOutgoing("""{"jsonrpc":"2.0","id":"7","result":{}}""")
				clientIncoming("""{"jsonrpc":"2.0","id":"7","result":{}}""")
			}
			assertLinesMatch(expectedUnsubscribeLogs, log, "unsubscribe logs")
			log.clear()

			// (I) Verify that no notification is received once unsubscribed
			provider.onResourceChange("file:///sub/folder/notes.txt")
			advanceUntilIdle()

			assertTrue(
				log.isEmpty(),
				"No further notifications should appear after unsubscribing from file:///sub/folder/notes.txt",
			)
		}

	@OptIn(ExperimentalCoroutinesApi::class)
	@Test
	fun testListResourceTemplatesPaginated() =
		runTest {
			val dispatcher = StandardTestDispatcher(testScheduler)
			val log = mutableListOf<String>()

			// Create multiple TemplateFileProviders, each referencing a different rootDir,
			// so they remain independent. Each provider yields 1 template in listResourceTemplates().
			val fsA = FakeFileSystem()
			val fsB = FakeFileSystem()
			val fsC = FakeFileSystem()

			// We don't need actual directories since we won't read them,
			// but let's create them for demonstration:
			fsA.createDirectories("/rootA".toPath())
			fsB.createDirectories("/rootB".toPath())
			fsC.createDirectories("/rootC".toPath())

			val providerA = TemplateFileProvider(
				fileSystem = fsA,
				rootDir = "/rootA".toPath(),
				name = "Template A",
				description = "Allows reading files from /rootA",
			)
			val providerB = TemplateFileProvider(
				fileSystem = fsB,
				rootDir = "/rootB".toPath(),
				name = "Template B",
				description = "Allows reading files from /rootB",
			)
			val providerC = TemplateFileProvider(
				fileSystem = fsC,
				rootDir = "/rootC".toPath(),
				name = "Template C",
				description = "Allows reading files from /rootC",
			)

			// 3 providers => 3 templates total. We'll set pageSize=1 => 3 pages.
			val (clientTransport, serverTransport) = TestTransport.createClientAndServerTransport()

			val server = Server.Builder()
				.withDispatcher(dispatcher)
				.withLogger { line -> log.server(line) }
				.withTransport(serverTransport)
				.withPageSize(1) // Force 1 template per page
				.withResourceProvider(providerA)
				.withResourceProvider(providerB)
				.withResourceProvider(providerC)
				.build()

			server.start()

			val client = Client.Builder()
				.withTransport(clientTransport)
				.withDispatcher(dispatcher)
				.withLogger { line -> log.client(line) }
				.withClientInfo("TestClient", "1.0.0")
				.build()
			client.start()

			// (A) Initialization
			client.initialize()
			advanceUntilIdle()
			log.clear()

			// (B) List all resource templates using pagination
			val allTemplates = mutableListOf<sh.ondr.kmcp.schema.resources.ResourceTemplate>()
			var pageCount = 0

			client.fetchPagesAsFlow(ListResourceTemplatesRequest).collect { pageOfTemplates ->
				pageCount++
				allTemplates += pageOfTemplates
			}
			advanceUntilIdle()

			// We should see exactly 3 pages, each with 1 template => total 3.
			assertEquals(3, pageCount, "Expected 3 pages, one for each provider's template")
			assertEquals(3, allTemplates.size, "Should have 3 total templates")

			// Optionally, check the names to see that we got A, B, C in some order
			val names = allTemplates.map { it.name }.toSet()
			assertEquals(
				setOf("Template A", "Template B", "Template C"),
				names,
				"Should see all 3 named templates in the result",
			)

			val expectedLogs = logLines {
				// 1st page
				clientOutgoing("""{"method":"resources/templates/list","jsonrpc":"2.0","id":"2"}""")
				serverIncoming("""{"method":"resources/templates/list","jsonrpc":"2.0","id":"2"}""")
				serverOutgoing(
					"""{"jsonrpc":"2.0","id":"2","result":{"resourceTemplates":[{"uriTemplate":"file:///{path}","name":"Template A","description":"Allows reading files from /rootA"}],"nextCursor":"eyJwYWdlIjoxLCJwYWdlU2l6ZSI6MX0="}}""",
				)
				clientIncoming(
					"""{"jsonrpc":"2.0","id":"2","result":{"resourceTemplates":[{"uriTemplate":"file:///{path}","name":"Template A","description":"Allows reading files from /rootA"}],"nextCursor":"eyJwYWdlIjoxLCJwYWdlU2l6ZSI6MX0="}}""",
				)

				// 2nd page
				clientOutgoing(
					"""{"method":"resources/templates/list","jsonrpc":"2.0","id":"3","params":{"cursor":"eyJwYWdlIjoxLCJwYWdlU2l6ZSI6MX0="}}""",
				)
				serverIncoming(
					"""{"method":"resources/templates/list","jsonrpc":"2.0","id":"3","params":{"cursor":"eyJwYWdlIjoxLCJwYWdlU2l6ZSI6MX0="}}""",
				)
				serverOutgoing(
					"""{"jsonrpc":"2.0","id":"3","result":{"resourceTemplates":[{"uriTemplate":"file:///{path}","name":"Template B","description":"Allows reading files from /rootB"}],"nextCursor":"eyJwYWdlIjoyLCJwYWdlU2l6ZSI6MX0="}}""",
				)
				clientIncoming(
					"""{"jsonrpc":"2.0","id":"3","result":{"resourceTemplates":[{"uriTemplate":"file:///{path}","name":"Template B","description":"Allows reading files from /rootB"}],"nextCursor":"eyJwYWdlIjoyLCJwYWdlU2l6ZSI6MX0="}}""",
				)

				// 3rd page
				clientOutgoing(
					"""{"method":"resources/templates/list","jsonrpc":"2.0","id":"4","params":{"cursor":"eyJwYWdlIjoyLCJwYWdlU2l6ZSI6MX0="}}""",
				)
				serverIncoming(
					"""{"method":"resources/templates/list","jsonrpc":"2.0","id":"4","params":{"cursor":"eyJwYWdlIjoyLCJwYWdlU2l6ZSI6MX0="}}""",
				)
				serverOutgoing(
					"""{"jsonrpc":"2.0","id":"4","result":{"resourceTemplates":[{"uriTemplate":"file:///{path}","name":"Template C","description":"Allows reading files from /rootC"}]}}""",
				)
				clientIncoming(
					"""{"jsonrpc":"2.0","id":"4","result":{"resourceTemplates":[{"uriTemplate":"file:///{path}","name":"Template C","description":"Allows reading files from /rootC"}]}}""",
				)
			}
			assertLinesMatch(expectedLogs, log, "paginated templates logs")
		}
}
