@file:OptIn(ExperimentalCoroutinesApi::class)

package sh.ondr.mcp4k.test.resources

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import sh.ondr.mcp4k.runtime.Client
import sh.ondr.mcp4k.runtime.Server
import sh.ondr.mcp4k.runtime.resources.DiscreteFileProvider
import sh.ondr.mcp4k.runtime.resources.File
import sh.ondr.mcp4k.runtime.serialization.deserializeResult
import sh.ondr.mcp4k.runtime.transport.ChannelTransport
import sh.ondr.mcp4k.schema.core.EmptyResult
import sh.ondr.mcp4k.schema.core.JsonRpcErrorCodes
import sh.ondr.mcp4k.schema.resources.ListResourcesRequest
import sh.ondr.mcp4k.schema.resources.ListResourcesRequest.ListResourcesParams
import sh.ondr.mcp4k.schema.resources.ListResourcesResult
import sh.ondr.mcp4k.schema.resources.ReadResourceRequest
import sh.ondr.mcp4k.schema.resources.ReadResourceRequest.ReadResourceParams
import sh.ondr.mcp4k.schema.resources.ReadResourceResult
import sh.ondr.mcp4k.schema.resources.Resource
import sh.ondr.mcp4k.schema.resources.ResourceContents
import sh.ondr.mcp4k.schema.resources.SubscribeRequest
import sh.ondr.mcp4k.schema.resources.UnsubscribeRequest
import sh.ondr.mcp4k.test.assertLinesMatch
import sh.ondr.mcp4k.test.buildLog
import sh.ondr.mcp4k.test.clientIncoming
import sh.ondr.mcp4k.test.clientOutgoing
import sh.ondr.mcp4k.test.serverIncoming
import sh.ondr.mcp4k.test.serverOutgoing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DiscreteFileProviderTest {
	@Test
	fun testListResourcesWithKnownFiles() =
		runTest {
			val fs = FakeFileSystem()
			val rootDir = "/discreteRoot".toPath()
			fs.createDirectories(rootDir)

			val fileName = "Main.kt"
			val fileContent = "fun main() { println(\"Hello!\") }"
			fs.write(rootDir.resolve(fileName)) {
				writeUtf8(fileContent)
			}
			val file = File(
				relativePath = fileName,
				name = "Main.kt",
				description = "Main file",
			)

			val provider = DiscreteFileProvider(
				fileSystem = fs,
				rootDir = rootDir,
				initialFiles = listOf(file),
			)

			val resources = provider.listResources()
			assertEquals(1, resources.size)
			val resource = resources.first()
			assertEquals("file://Main.kt", resource.uri)
			assertEquals("Main.kt", resource.name)

			// Also verify readResource for that file
			val contents = provider.readResource(resource.uri)
			assertNotNull(contents, "Expected to read the known file successfully.")
			when (contents) {
				is ResourceContents.Text -> {
					assertEquals(fileContent, contents.text)
					assertEquals("file://Main.kt", contents.uri)
				}
				else -> error("Expected ResourceContents.Text")
			}
		}

	@Test
	fun testListResourcesEmpty() =
		runTest {
			val fs = FakeFileSystem()
			val rootDir = "/emptyRoot".toPath()
			fs.createDirectories(rootDir)

			val provider = DiscreteFileProvider(
				fileSystem = fs,
				rootDir = rootDir,
				initialFiles = emptyList(),
			)

			val resources = provider.listResources()
			assertEquals(0, resources.size, "No known files => no resources listed")
		}

	@Test
	fun testReadResourceNonexistentFile() =
		runTest {
			val fs = FakeFileSystem()
			val rootDir = "/someRoot".toPath()
			fs.createDirectories(rootDir)

			// We have no known files
			val provider = DiscreteFileProvider(
				fileSystem = fs,
				rootDir = rootDir,
				initialFiles = emptyList(),
			)

			// Try to read "file://idontexist.txt"
			val result = provider.readResource("file://idontexist.txt")
			assertNull(result, "Should return null for unknown or nonexistent file.")
		}

	@Test
	fun testAddAndRemoveFile() =
		runTest {
			val fs = FakeFileSystem()
			val rootDir = "/myroot".toPath()
			fs.createDirectories(rootDir)

			// Put a file on disk
			fs.write(rootDir.resolve("one.txt")) {
				writeUtf8("File one contents")
			}

			val provider = DiscreteFileProvider(
				fileSystem = fs,
				rootDir = rootDir,
				initialFiles = emptyList(),
			)

			// Initially empty
			val initialResources = provider.listResources()
			assertEquals(0, initialResources.size, "No files should be listed initially.")

			// 1) Add one file
			val addFirstResult = provider.addFile(
				File(
					relativePath = "one.txt",
					name = "one.txt",
					description = "File one",
				),
			)
			assertTrue(addFirstResult, "Expected true when adding a brand-new file.")

			// Verify that the file now shows up
			val afterAdd = provider.listResources()
			assertEquals(1, afterAdd.size, "Should list the newly added file.")
			val resource = afterAdd.first()
			assertEquals("file://one.txt", resource.uri)
			assertEquals("one.txt", resource.name)
			assertEquals("File one", resource.description)

			// 2) Try adding the same file again
			val addDuplicateResult = provider.addFile(
				File(
					relativePath = "one.txt",
					name = "one.txt",
					description = "File one",
				),
			)
			assertFalse(
				addDuplicateResult,
				"Expected false since 'one.txt' was already in the list.",
			)
			// The resource list should remain the same
			val afterAddDuplicate = provider.listResources()
			assertEquals(1, afterAddDuplicate.size, "Still only one resource after duplicate add.")

			// 3) Remove the file
			val removeFirstResult = provider.removeFile("one.txt")
			assertTrue(removeFirstResult, "Expected true for removing existing file 'one.txt'.")
			val afterRemove = provider.listResources()
			assertEquals(0, afterRemove.size, "No resources left after removing the only file.")

			// 4) Try removing a file that no longer exists
			val removeAgainResult = provider.removeFile("one.txt")
			assertFalse(
				removeAgainResult,
				"Expected false when removing a file that isn't in the list anymore.",
			)
			val afterRemoveAgain = provider.listResources()
			assertEquals(0, afterRemoveAgain.size, "Should still have 0 resources after removing a non-existent file.")
		}

	@Test
	fun testIntegrationWithSubdirectory() =
		runTest {
			val dispatcher = StandardTestDispatcher(testScheduler)
			val log = mutableListOf<String>()

			// 1) Prepare a FakeFileSystem with two files:
			//    - /discreteRoot/hello.txt
			//    - /discreteRoot/sub/folder/bye.txt
			val fs = FakeFileSystem()
			val rootDir = "/discreteRoot".toPath()
			fs.createDirectories(rootDir)

			// The top-level file
			fs.write(rootDir.resolve("hello.txt")) {
				writeUtf8("Hello from top-level file!")
			}
			val topLevelFile = File(
				relativePath = "hello.txt",
				name = "hello.txt",
				description = "File at hello.txt",
			)

			// Sub-folder + file
			val subFolder = rootDir.resolve("sub/folder")
			fs.createDirectories(subFolder)
			fs.write(subFolder.resolve("bye.txt")) {
				writeUtf8("Bye from sub-folder!")
			}
			val subFile = File(
				relativePath = "sub/folder/bye.txt",
				name = "bye.txt",
				description = "File at sub/folder/bye.txt",
			)

			// 2) Build a DiscreteFileProvider with known files that includes the sub-folder path
			val provider = DiscreteFileProvider(
				fileSystem = fs,
				rootDir = rootDir,
				initialFiles = listOf(
					topLevelFile,
					subFile,
				),
			)

			// 3) Build server with that provider
			val clientTransport = ChannelTransport()
			val serverTransport = clientTransport.flip()
			val server = Server.Builder()
				.withDispatcher(dispatcher)
				.withTransportLogger(
					logIncoming = { msg -> log.add(serverIncoming(msg)) },
					logOutgoing = { msg -> log.add(serverOutgoing(msg)) },
				)
				.withTransport(serverTransport)
				.withResourceProvider(provider)
				.build()
			server.start()

			// 4) Build client
			val client = Client.Builder()
				.withTransport(clientTransport)
				.withDispatcher(dispatcher)
				.withTransportLogger(
					logIncoming = { msg -> log.add(clientIncoming(msg)) },
					logOutgoing = { msg -> log.add(clientOutgoing(msg)) },
				)
				.withClientInfo("TestClient", "1.0.0")
				.build()
			client.start()

			// 5) Perform MCP initialization
			client.initialize()
			advanceUntilIdle()
			log.clear()

			// 6) List resources => expect 2
			val listResp = client.sendRequest { id ->
				ListResourcesRequest(id = id, params = ListResourcesParams())
			}
			advanceUntilIdle()

			val listResult = listResp.result?.deserializeResult<ListResourcesResult>()
			assertNotNull(listResult)
			assertEquals(2, listResult.resources.size, "Should see both files (top-level + subfolder).")

			// Optional: verify the URIs
			val uris = listResult.resources.map { it.uri }.toSet()
			assertEquals(
				setOf("file://hello.txt", "file://sub/folder/bye.txt"),
				uris,
				"Expected the two known files in discrete provider",
			)

			// Check the logs for the list call (partial check)
			val expectedListLogs = buildLog {
				addClientOutgoing("""{"method":"resources/list","jsonrpc":"2.0","id":"2","params":{}}""")
				addServerIncoming("""{"method":"resources/list","jsonrpc":"2.0","id":"2","params":{}}""")
				addServerOutgoing(
					"""{"jsonrpc":"2.0","id":"2","result":{"resources":[{"uri":"file://hello.txt","name":"hello.txt","description":"File at hello.txt","mimeType":"text/plain"},{"uri":"file://sub/folder/bye.txt","name":"bye.txt","description":"File at sub/folder/bye.txt","mimeType":"text/plain"}]}}""",
				)
				addClientIncoming(
					"""{"jsonrpc":"2.0","id":"2","result":{"resources":[{"uri":"file://hello.txt","name":"hello.txt","description":"File at hello.txt","mimeType":"text/plain"},{"uri":"file://sub/folder/bye.txt","name":"bye.txt","description":"File at sub/folder/bye.txt","mimeType":"text/plain"}]}}""",
				)
			}
			assertLinesMatch(expectedListLogs, log, "list resources logs (partial check)")
			log.clear()

			// (C) Read "hello.txt"
			val readHelloResp = client.sendRequest { id ->
				ReadResourceRequest(
					id = id,
					params = ReadResourceParams(uri = "file://hello.txt"),
				)
			}
			advanceUntilIdle()

			val readHelloResult = readHelloResp.result?.deserializeResult<ReadResourceResult>()
			val helloContent = readHelloResult?.contents?.singleOrNull()
			assertNotNull(helloContent, "Expected to get the 'hello.txt' resource")
			when (helloContent) {
				is ResourceContents.Text -> assertEquals("Hello from top-level file!", helloContent.text)
				else -> error("Expected text content for hello.txt")
			}
			// Check logs for reading "hello.txt"
			val expectedReadHelloLogs = buildLog {
				addClientOutgoing("""{"method":"resources/read","jsonrpc":"2.0","id":"3","params":{"uri":"file://hello.txt"}}""")
				addServerIncoming("""{"method":"resources/read","jsonrpc":"2.0","id":"3","params":{"uri":"file://hello.txt"}}""")
				addServerOutgoing(
					"""{"jsonrpc":"2.0","id":"3","result":{"contents":[{"uri":"file://hello.txt","mimeType":"text/plain","text":"Hello from top-level file!"}]}}""",
				)
				addClientIncoming(
					"""{"jsonrpc":"2.0","id":"3","result":{"contents":[{"uri":"file://hello.txt","mimeType":"text/plain","text":"Hello from top-level file!"}]}}""",
				)
			}
			assertLinesMatch(expectedReadHelloLogs, log, "read hello.txt logs")
			log.clear()

			// (D) Read "sub/folder/bye.txt"
			val readByeResp = client.sendRequest { id ->
				ReadResourceRequest(
					id = id,
					params = ReadResourceParams(uri = "file://sub/folder/bye.txt"),
				)
			}
			advanceUntilIdle()

			val readByeResult = readByeResp.result?.deserializeResult<ReadResourceResult>()
			val byeContent = readByeResult?.contents?.singleOrNull()
			assertNotNull(byeContent, "Expected to get the 'bye.txt' resource")
			when (byeContent) {
				is ResourceContents.Text -> assertEquals("Bye from sub-folder!", byeContent.text)
				else -> error("Expected text content for bye.txt in sub-folder")
			}

			// Check logs for reading "sub/folder/bye.txt"
			val expectedReadByeLogs = buildLog {
				addClientOutgoing("""{"method":"resources/read","jsonrpc":"2.0","id":"4","params":{"uri":"file://sub/folder/bye.txt"}}""")
				addServerIncoming("""{"method":"resources/read","jsonrpc":"2.0","id":"4","params":{"uri":"file://sub/folder/bye.txt"}}""")
				addServerOutgoing(
					"""{"jsonrpc":"2.0","id":"4","result":{"contents":[{"uri":"file://sub/folder/bye.txt","mimeType":"text/plain","text":"Bye from sub-folder!"}]}}""",
				)
				addClientIncoming(
					"""{"jsonrpc":"2.0","id":"4","result":{"contents":[{"uri":"file://sub/folder/bye.txt","mimeType":"text/plain","text":"Bye from sub-folder!"}]}}""",
				)
			}
			assertLinesMatch(expectedReadByeLogs, log, "read sub/folder/bye.txt logs")
			log.clear()

			// (E) Attempt to read a nonexistent resource
			val invalidResp = client.sendRequest { id ->
				ReadResourceRequest(
					id = id,
					params = ReadResourceParams(uri = "file://sub/folder/no-such-file.txt"),
				)
			}
			advanceUntilIdle()
			val err = invalidResp.error
			assertNotNull(err, "Expected an error for a nonexistent resource")
			assertEquals(JsonRpcErrorCodes.RESOURCE_NOT_FOUND, err.code)

			// Check logs for reading nonexistent file
			val expectedNoSuchFileLogs = buildLog {
				addClientOutgoing("""{"method":"resources/read","jsonrpc":"2.0","id":"5","params":{"uri":"file://sub/folder/no-such-file.txt"}}""")
				addServerIncoming("""{"method":"resources/read","jsonrpc":"2.0","id":"5","params":{"uri":"file://sub/folder/no-such-file.txt"}}""")
				addServerOutgoing(
					"""{"jsonrpc":"2.0","id":"5","error":{"code":-32002,"message":"Resource not found: file://sub/folder/no-such-file.txt"}}""",
				)
				addClientIncoming(
					"""{"jsonrpc":"2.0","id":"5","error":{"code":-32002,"message":"Resource not found: file://sub/folder/no-such-file.txt"}}""",
				)
			}
			assertLinesMatch(expectedNoSuchFileLogs, log, "read nonexistent resource logs")
			log.clear()

			// (F) Subscribe to the sub/folder file
			val subReq = client.sendRequest { id ->
				SubscribeRequest(
					id = id,
					params = SubscribeRequest.SubscribeParams(uri = "file://sub/folder/bye.txt"),
				)
			}
			advanceUntilIdle()
			assertNotNull(subReq.result?.deserializeResult<EmptyResult>())

			// Check logs for subscribe
			val expectedSubscribeLogs = buildLog {
				addClientOutgoing("""{"method":"resources/subscribe","jsonrpc":"2.0","id":"6","params":{"uri":"file://sub/folder/bye.txt"}}""")
				addServerIncoming("""{"method":"resources/subscribe","jsonrpc":"2.0","id":"6","params":{"uri":"file://sub/folder/bye.txt"}}""")
				addServerOutgoing("""{"jsonrpc":"2.0","id":"6","result":{}}""")
				addClientIncoming("""{"jsonrpc":"2.0","id":"6","result":{}}""")
			}
			assertLinesMatch(expectedSubscribeLogs, log, "subscribe logs")
			log.clear()

			// (G) Notify update
			provider.onResourceChange("file://sub/folder/bye.txt")
			advanceUntilIdle()

			// Verify logs for the "resources/updated" notification, etc.
			val expectedNotificationLogs = buildLog {
				addServerOutgoing("""{"method":"notifications/resources/updated","jsonrpc":"2.0","params":{"uri":"file://sub/folder/bye.txt"}}""")
				addClientIncoming("""{"method":"notifications/resources/updated","jsonrpc":"2.0","params":{"uri":"file://sub/folder/bye.txt"}}""")
			}
			assertLinesMatch(expectedNotificationLogs, log, "resource updated notification logs")
			log.clear()

			// (H) Unsubscribe from the sub/folder file
			val unsubReq = client.sendRequest { id ->
				UnsubscribeRequest(
					id = id,
					params = UnsubscribeRequest.UnsubscribeParams(uri = "file://sub/folder/bye.txt"),
				)
			}
			advanceUntilIdle()
			val unsubResult = unsubReq.result?.deserializeResult<EmptyResult>()
			assertNotNull(unsubResult, "Expected an EmptyResult from unsubscribe")

			val expectedUnsubscribeLogs = buildLog {
				addClientOutgoing("""{"method":"resources/unsubscribe","jsonrpc":"2.0","id":"7","params":{"uri":"file://sub/folder/bye.txt"}}""")
				addServerIncoming("""{"method":"resources/unsubscribe","jsonrpc":"2.0","id":"7","params":{"uri":"file://sub/folder/bye.txt"}}""")
				addServerOutgoing("""{"jsonrpc":"2.0","id":"7","result":{}}""")
				addClientIncoming("""{"jsonrpc":"2.0","id":"7","result":{}}""")
			}
			assertLinesMatch(expectedUnsubscribeLogs, log, "unsubscribe logs")
			log.clear()

			// (I) Verify no notification is received after unsubscribing
			provider.onResourceChange("file://sub/folder/bye.txt")
			advanceUntilIdle()

			assertTrue(log.isEmpty(), "No further notifications should appear after unsubscribing.")
		}

	@OptIn(ExperimentalCoroutinesApi::class)
	@Test
	fun testListResourcesPaginated() =
		runTest {
			val dispatcher = StandardTestDispatcher(testScheduler)
			val log = mutableListOf<String>()

			// 1) Prepare a FakeFileSystem with 7 files under /discreteRoot
			val fs = FakeFileSystem()
			val rootDir = "/discreteRoot".toPath()
			fs.createDirectories(rootDir)

			// Create resource files: file0.txt ... file6.txt
			val initialFiles = buildList<File> {
				for (i in 0 until 7) {
					val filename = "file$i.txt"
					fs.write(rootDir.resolve(filename)) {
						writeUtf8("Contents of $filename")
					}
					add(
						File(
							relativePath = filename,
							name = filename,
							description = "File at $filename",
						),
					)
				}
			}

			val provider = DiscreteFileProvider(
				fileSystem = fs,
				rootDir = rootDir,
				initialFiles = initialFiles,
			)

			// 2) Build the server with pageSize=3 and the DiscreteFileProvider
			val clientTransport = ChannelTransport()
			val serverTransport = clientTransport.flip()
			val server = Server.Builder()
				.withDispatcher(dispatcher)
				.withTransportLogger(
					logIncoming = { msg -> log.add(serverIncoming(msg)) },
					logOutgoing = { msg -> log.add(serverOutgoing(msg)) },
				)
				.withTransport(serverTransport)
				.withResourceProvider(provider)
				.withPageSize(3)
				.build()
			server.start()

			// 3) Build the client
			val client = Client.Builder()
				.withTransport(clientTransport)
				.withDispatcher(dispatcher)
				.withTransportLogger(
					logIncoming = { msg -> log.add(clientIncoming(msg)) },
					logOutgoing = { msg -> log.add(clientOutgoing(msg)) },
				)
				.withClientInfo("TestClient", "1.0.0")
				.build()
			client.start()

			// (A) Initialization
			client.initialize()
			advanceUntilIdle()
			log.clear()

			// (B) Use fetchPagesAsFlow to list resources
			val allResources = mutableListOf<Resource>()
			var pageCount = 0

			client.fetchPagesAsFlow(ListResourcesRequest).collect { pageOfResources ->
				pageCount++
				allResources += pageOfResources
			}
			advanceUntilIdle()

			// We should get 3 pages with a total of 7 resources, i.e. [3, 3, 1]
			assertEquals(3, pageCount, "Expected exactly 3 pages (3 + 3 + 1).")
			assertEquals(7, allResources.size, "Should have 7 total resources listed.")

			val expectedLogs = buildLog {
				// 1st page
				addClientOutgoing("""{"method":"resources/list","jsonrpc":"2.0","id":"2"}""")
				addServerIncoming("""{"method":"resources/list","jsonrpc":"2.0","id":"2"}""")
				addServerOutgoing(
					"""{"jsonrpc":"2.0","id":"2","result":{"resources":[{"uri":"file://file0.txt","name":"file0.txt","description":"File at file0.txt","mimeType":"text/plain"},{"uri":"file://file1.txt","name":"file1.txt","description":"File at file1.txt","mimeType":"text/plain"},{"uri":"file://file2.txt","name":"file2.txt","description":"File at file2.txt","mimeType":"text/plain"}],"nextCursor":"eyJwYWdlIjoxLCJwYWdlU2l6ZSI6M30="}}""",
				)
				addClientIncoming(
					"""{"jsonrpc":"2.0","id":"2","result":{"resources":[{"uri":"file://file0.txt","name":"file0.txt","description":"File at file0.txt","mimeType":"text/plain"},{"uri":"file://file1.txt","name":"file1.txt","description":"File at file1.txt","mimeType":"text/plain"},{"uri":"file://file2.txt","name":"file2.txt","description":"File at file2.txt","mimeType":"text/plain"}],"nextCursor":"eyJwYWdlIjoxLCJwYWdlU2l6ZSI6M30="}}""",
				)

				// 2nd page
				addClientOutgoing("""{"method":"resources/list","jsonrpc":"2.0","id":"3","params":{"cursor":"eyJwYWdlIjoxLCJwYWdlU2l6ZSI6M30="}}""")
				addServerIncoming("""{"method":"resources/list","jsonrpc":"2.0","id":"3","params":{"cursor":"eyJwYWdlIjoxLCJwYWdlU2l6ZSI6M30="}}""")
				addServerOutgoing(
					"""{"jsonrpc":"2.0","id":"3","result":{"resources":[{"uri":"file://file3.txt","name":"file3.txt","description":"File at file3.txt","mimeType":"text/plain"},{"uri":"file://file4.txt","name":"file4.txt","description":"File at file4.txt","mimeType":"text/plain"},{"uri":"file://file5.txt","name":"file5.txt","description":"File at file5.txt","mimeType":"text/plain"}],"nextCursor":"eyJwYWdlIjoyLCJwYWdlU2l6ZSI6M30="}}""",
				)
				addClientIncoming(
					"""{"jsonrpc":"2.0","id":"3","result":{"resources":[{"uri":"file://file3.txt","name":"file3.txt","description":"File at file3.txt","mimeType":"text/plain"},{"uri":"file://file4.txt","name":"file4.txt","description":"File at file4.txt","mimeType":"text/plain"},{"uri":"file://file5.txt","name":"file5.txt","description":"File at file5.txt","mimeType":"text/plain"}],"nextCursor":"eyJwYWdlIjoyLCJwYWdlU2l6ZSI6M30="}}""",
				)

				// 3rd page
				addClientOutgoing("""{"method":"resources/list","jsonrpc":"2.0","id":"4","params":{"cursor":"eyJwYWdlIjoyLCJwYWdlU2l6ZSI6M30="}}""")
				addServerIncoming("""{"method":"resources/list","jsonrpc":"2.0","id":"4","params":{"cursor":"eyJwYWdlIjoyLCJwYWdlU2l6ZSI6M30="}}""")
				addServerOutgoing(
					"""{"jsonrpc":"2.0","id":"4","result":{"resources":[{"uri":"file://file6.txt","name":"file6.txt","description":"File at file6.txt","mimeType":"text/plain"}]}}""",
				)
				addClientIncoming(
					"""{"jsonrpc":"2.0","id":"4","result":{"resources":[{"uri":"file://file6.txt","name":"file6.txt","description":"File at file6.txt","mimeType":"text/plain"}]}}""",
				)
			}
			assertLinesMatch(expectedLogs, log, "paginated resources logs")
			log.clear()
		}
}
