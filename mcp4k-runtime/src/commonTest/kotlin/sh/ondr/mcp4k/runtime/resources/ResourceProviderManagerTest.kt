package sh.ondr.mcp4k.runtime.resources

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import sh.ondr.mcp4k.assertLinesMatch
import sh.ondr.mcp4k.client
import sh.ondr.mcp4k.logLines
import sh.ondr.mcp4k.runtime.Client
import sh.ondr.mcp4k.runtime.Server
import sh.ondr.mcp4k.runtime.serialization.deserializeResult
import sh.ondr.mcp4k.runtime.transport.TestTransport
import sh.ondr.mcp4k.schema.core.EmptyResult
import sh.ondr.mcp4k.schema.core.JsonRpcErrorCodes
import sh.ondr.mcp4k.schema.resources.ListResourcesRequest
import sh.ondr.mcp4k.schema.resources.ListResourcesResult
import sh.ondr.mcp4k.schema.resources.ReadResourceRequest
import sh.ondr.mcp4k.schema.resources.ReadResourceResult
import sh.ondr.mcp4k.schema.resources.ResourceContents
import sh.ondr.mcp4k.schema.resources.SubscribeRequest
import sh.ondr.mcp4k.schema.resources.UnsubscribeRequest
import sh.ondr.mcp4k.schema.resources.UnsubscribeRequest.UnsubscribeParams
import sh.ondr.mcp4k.server
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@OptIn(ExperimentalCoroutinesApi::class)
class ResourceProviderManagerTest {
	@Test
	fun testMultipleDiscreteProvidersWithLogs() =
		runTest {
			val testDispatcher = StandardTestDispatcher(testScheduler)
			val log = mutableListOf<String>()

			val fs = FakeFileSystem()

			// Root A
			val rootA = "/rootA".toPath()
			fs.createDirectories(rootA)
			// File A
			val fileAName = "fileA.txt"
			fs.write(rootA.resolve(fileAName)) { writeUtf8("Hello from A!") }
			val fileA = File(
				relativePath = fileAName,
				name = fileAName,
				description = "File in rootA",
			)

			// Root B
			val rootB = "/rootB".toPath()
			fs.createDirectories(rootB)
			// File B
			val fileBName = "fileB.txt"
			fs.write(rootB.resolve(fileBName)) { writeUtf8("Hello from B!") }
			val fileB = File(
				relativePath = fileBName,
				name = fileBName,
				description = "File in rootB",
			)

			// Create providers
			val providerA = DiscreteFileProvider(
				fileSystem = fs,
				rootDir = rootA,
				initialFiles = listOf(fileA),
			)
			val providerB = DiscreteFileProvider(
				fileSystem = fs,
				rootDir = rootB,
				initialFiles = listOf(fileB),
			)

			// Create a server that has these two providers
			val (clientTransport, serverTransport) = TestTransport.createClientAndServerTransport()
			val server = Server.Builder()
				.withDispatcher(testDispatcher)
				.withLogger { line -> log.server(line) }
				.withTransport(serverTransport)
				.withResourceProvider(providerA)
				.withResourceProvider(providerB)
				.build()
			server.start()

			// Create client
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

			// 2) List resources
			val listRequestResp = client.sendRequest { reqId ->
				ListResourcesRequest(id = reqId, params = ListResourcesRequest.ListResourcesParams())
			}
			advanceUntilIdle()

			// Inspect the actual data
			val listResourcesResult = listRequestResp.result?.deserializeResult<ListResourcesResult>()
			assertNotNull(listResourcesResult)
			val resourceList = listResourcesResult.resources
			// Expect exactly 2 resources, one from each provider.
			assertEquals(2, resourceList.size)

			// 3) Check logs
			val expectedListLogs = logLines {
				clientOutgoing("""{"method":"resources/list","jsonrpc":"2.0","id":"2","params":{}}""")
				serverIncoming("""{"method":"resources/list","jsonrpc":"2.0","id":"2","params":{}}""")
				serverOutgoing(
					"""{"jsonrpc":"2.0","id":"2","result":{"resources":[{"uri":"file://fileA.txt","name":"fileA.txt","description":"File in rootA","mimeType":"text/plain"},{"uri":"file://fileB.txt","name":"fileB.txt","description":"File in rootB","mimeType":"text/plain"}]}}""",
				)
				clientIncoming(
					"""{"jsonrpc":"2.0","id":"2","result":{"resources":[{"uri":"file://fileA.txt","name":"fileA.txt","description":"File in rootA","mimeType":"text/plain"},{"uri":"file://fileB.txt","name":"fileB.txt","description":"File in rootB","mimeType":"text/plain"}]}}""",
				)
			}
			assertLinesMatch(expectedListLogs, log, "resources/list test")
			log.clear()

			// 4) Read resource fileA
			val readAResp = client.sendRequest { reqId ->
				ReadResourceRequest(
					id = reqId,
					params = ReadResourceRequest.ReadResourceParams(uri = "file://fileA.txt"),
				)
			}
			advanceUntilIdle()

			val readAResult = readAResp.result?.deserializeResult<ReadResourceResult>()
			assertNotNull(readAResult)
			val aContents = readAResult.contents
			assertEquals(1, aContents.size)
			val aContent = aContents.first()
			if (aContent is ResourceContents.Text) {
				// Check text
				assertEquals("Hello from A!", aContent.text)
			} else {
				error("Expected text for fileA.txt")
			}

			// Check logs for read request
			val expectedReadALogs = logLines {
				clientOutgoing("""{"method":"resources/read","jsonrpc":"2.0","id":"3","params":{"uri":"file://fileA.txt"}}""")
				serverIncoming("""{"method":"resources/read","jsonrpc":"2.0","id":"3","params":{"uri":"file://fileA.txt"}}""")
				serverOutgoing(
					"""{"jsonrpc":"2.0","id":"3","result":{"contents":[{"uri":"file://fileA.txt","mimeType":"text/plain","text":"Hello from A!"}]}}""",
				)
				clientIncoming(
					"""{"jsonrpc":"2.0","id":"3","result":{"contents":[{"uri":"file://fileA.txt","mimeType":"text/plain","text":"Hello from A!"}]}}""",
				)
			}
			assertLinesMatch(expectedReadALogs, log, "resources/read test")
			log.clear()

			// 5) Subscribe to fileB
			val subBResp = client.sendRequest { reqId ->
				SubscribeRequest(
					id = reqId,
					params = SubscribeRequest.SubscribeParams(uri = "file://fileB.txt"),
				)
			}
			advanceUntilIdle()

			val subBResult = subBResp.result?.deserializeResult<EmptyResult>()
			assertNotNull(subBResult)

			// check logs for subscribe
			val expectedSubscribeLogs = logLines {
				clientOutgoing("""{"method":"resources/subscribe","jsonrpc":"2.0","id":"4","params":{"uri":"file://fileB.txt"}}""")
				serverIncoming("""{"method":"resources/subscribe","jsonrpc":"2.0","id":"4","params":{"uri":"file://fileB.txt"}}""")
				serverOutgoing("""{"jsonrpc":"2.0","id":"4","result":{}}""")
				clientIncoming("""{"jsonrpc":"2.0","id":"4","result":{}}""")
			}
			assertLinesMatch(expectedSubscribeLogs, log, "subscribe test")
			log.clear()

			// 6) Make a small change to "fileB.txt" and notify
			fs.write(rootB.resolve(fileBName)) { writeUtf8("Modified B!") }
			providerB.onResourceChange("file://fileB.txt")
			advanceUntilIdle()

			// We should see "notifications/resources/updated" on the client side
			val expectedUpdateLogs = logLines {
				serverOutgoing("""{"method":"notifications/resources/updated","jsonrpc":"2.0","params":{"uri":"file://fileB.txt"}}""")
				clientIncoming("""{"method":"notifications/resources/updated","jsonrpc":"2.0","params":{"uri":"file://fileB.txt"}}""")
			}
			assertLinesMatch(expectedUpdateLogs, log, "resource updated test")
			log.clear()

			// 7) Unsubscribe from fileB
			val unsubBResp = client.sendRequest { reqId ->
				UnsubscribeRequest(
					id = reqId,
					params = UnsubscribeParams(uri = "file://fileB.txt"),
				)
			}
			advanceUntilIdle()

			val unsubBResult = unsubBResp.result?.deserializeResult<EmptyResult>()
			assertNotNull(unsubBResult)

			val expectedUnsubscribeLogs = logLines {
				clientOutgoing("""{"method":"resources/unsubscribe","jsonrpc":"2.0","id":"5","params":{"uri":"file://fileB.txt"}}""")
				serverIncoming("""{"method":"resources/unsubscribe","jsonrpc":"2.0","id":"5","params":{"uri":"file://fileB.txt"}}""")
				serverOutgoing("""{"jsonrpc":"2.0","id":"5","result":{}}""")
				clientIncoming("""{"jsonrpc":"2.0","id":"5","result":{}}""")
			}
			assertLinesMatch(expectedUnsubscribeLogs, log, "unsubscribe test")
		}

	@Test
	fun testNonexistentResourceError() =
		runTest {
			val testDispatcher = StandardTestDispatcher(testScheduler)
			val log = mutableListOf<String>()

			// Prepare a FakeFileSystem
			val fs = FakeFileSystem()

			// Root directory with one real file
			val rootA = "/rootA".toPath()
			fs.createDirectories(rootA)
			fs.write(rootA.resolve("fileA.txt")) { writeUtf8("Hello from A!") }

			// Create a discrete provider with known file "fileA.txt"
			val providerA = DiscreteFileProvider(
				fileSystem = fs,
				rootDir = rootA,
				initialFiles = listOf(
					File(
						relativePath = "fileA.txt",
						name = "fileA.txt",
						description = "Known file in rootA",
					),
				),
			)

			// Set up server and client with this single provider
			val (clientTransport, serverTransport) = TestTransport.createClientAndServerTransport()
			val server = Server.Builder()
				.withDispatcher(testDispatcher)
				.withLogger { line -> log.server(line) }
				.withTransport(serverTransport)
				.withResourceProvider(providerA)
				.build()
			server.start()

			val client = Client.Builder()
				.withTransport(clientTransport)
				.withDispatcher(testDispatcher)
				.withLogger { line -> log.client(line) }
				.withClientInfo("ErrorTestClient", "1.0.0")
				.build()

			client.start()

			// 1) Initialization
			client.initialize()
			advanceUntilIdle()
			log.clear()

			// 2) Attempt to read a resource that doesn't exist
			val response = client.sendRequest { reqId ->
				ReadResourceRequest(
					id = reqId,
					params = ReadResourceRequest.ReadResourceParams(
						uri = "file://nonexistent.txt",
					),
				)
			}
			advanceUntilIdle()

			// 3) The server should return a JSON-RPC error with code -32002 (resource not found).
			val errorResult = response.error
			assertNotNull(errorResult)
			assertEquals(JsonRpcErrorCodes.RESOURCE_NOT_FOUND, errorResult.code)

			// 4) Check logs
			val expectedLogs = logLines {
				clientOutgoing("""{"method":"resources/read","jsonrpc":"2.0","id":"2","params":{"uri":"file://nonexistent.txt"}}""")
				serverIncoming("""{"method":"resources/read","jsonrpc":"2.0","id":"2","params":{"uri":"file://nonexistent.txt"}}""")
				serverOutgoing("""{"jsonrpc":"2.0","id":"2","error":{"code":-32002,"message":"Resource not found: file://nonexistent.txt"}}""")
				clientIncoming("""{"jsonrpc":"2.0","id":"2","error":{"code":-32002,"message":"Resource not found: file://nonexistent.txt"}}""")
			}
			assertLinesMatch(expectedLogs, log, "nonexistent resource error test")
		}
}
