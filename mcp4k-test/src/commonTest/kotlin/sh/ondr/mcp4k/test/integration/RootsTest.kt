package sh.ondr.mcp4k.test.integration

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import sh.ondr.mcp4k.runtime.Client
import sh.ondr.mcp4k.runtime.Server
import sh.ondr.mcp4k.runtime.serialization.deserializeResult
import sh.ondr.mcp4k.runtime.transport.ChannelTransport
import sh.ondr.mcp4k.schema.core.JsonRpcResponse
import sh.ondr.mcp4k.schema.roots.ListRootsRequest
import sh.ondr.mcp4k.schema.roots.ListRootsResult
import sh.ondr.mcp4k.schema.roots.Root
import sh.ondr.mcp4k.test.assertLinesMatch
import sh.ondr.mcp4k.test.buildLog
import sh.ondr.mcp4k.test.clientIncoming
import sh.ondr.mcp4k.test.clientOutgoing
import sh.ondr.mcp4k.test.serverIncoming
import sh.ondr.mcp4k.test.serverOutgoing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class RootsTest {
	@OptIn(ExperimentalCoroutinesApi::class)
	@Test
	fun testRootsWorkflow() =
		runTest {
			val testDispatcher = StandardTestDispatcher(testScheduler)
			val log = mutableListOf<String>()

			// 1) Create test transport
			val clientTransport = ChannelTransport()
			val serverTransport = clientTransport.flip()

			// 2) Build the server
			val server = Server.Builder()
				.withDispatcher(testDispatcher)
				.withTransport(serverTransport)
				.withTransportLogger(
					logIncoming = { msg -> log.add(serverIncoming(msg)) },
					logOutgoing = { msg -> log.add(serverOutgoing(msg)) },
				)
				.build()
			server.start()

			// 3) Build the client with two initial roots
			val client = Client.Builder()
				.withTransport(clientTransport)
				.withDispatcher(testDispatcher)
				.withTransportLogger(
					logIncoming = { msg -> log.add(clientIncoming(msg)) },
					logOutgoing = { msg -> log.add(clientOutgoing(msg)) },
				)
				.withClientInfo("RootsTestClient", "1.0.0")
				.withRoot(Root(uri = "file:///home/user/projectA", name = "Project A"))
				.withRoot(Root(uri = "file:///home/user/projectB", name = "Project B"))
				.build()
			client.start()

			// 4) Perform MCP initialization
			client.initialize()
			advanceUntilIdle()
			log.clear()

			// 5) Server sends a roots/list request
			val listRootsResponse1: JsonRpcResponse = server.sendRequest { id ->
				ListRootsRequest(id)
			}
			advanceUntilIdle()

			// Confirm there's no error
			assertNotNull(listRootsResponse1.result, "Expected a result from roots/list request.")
			assertEquals(null, listRootsResponse1.error, "Should not produce an error for roots/list.")
			val listRootsResult1 = listRootsResponse1.result.deserializeResult<ListRootsResult>()
			assertNotNull(listRootsResult1, "Expected a valid ListRootsResult")

			// Ensure the client responded with the two initial roots
			assertEquals(2, listRootsResult1.roots.size)
			val rootUris = listRootsResult1.roots
			assertEquals(
				listOf(
					Root(uri = "file:///home/user/projectA", name = "Project A"),
					Root(uri = "file:///home/user/projectB", name = "Project B"),
				),
				rootUris,
				"Should return the two initial roots.",
			)
			log.clear()

			// 6) Remove one root by name from the client, ensuring we get a notification
			val removedA = client.removeRootByName("Project A")
			assertEquals(true, removedA, "Should be able to remove existing root")
			advanceUntilIdle()

			// The client should have sent a notifications/roots/list_changed to the server
			val expectedNotification1 = buildLog {
				addClientOutgoing(
					"""{"method":"notifications/roots/list_changed","jsonrpc":"2.0"}""",
				)
				addServerIncoming(
					"""{"method":"notifications/roots/list_changed","jsonrpc":"2.0"}""",
				)
			}
			assertLinesMatch(expectedNotification1, log, "Check removal notification logs")
			log.clear()

			// 7) Now add a new root
			val rootC = Root(uri = "file:///home/user/projectC", name = "Project C")
			client.addRoot(rootC)
			advanceUntilIdle()

			// That again triggers the notification
			val expectedNotification2 = buildLog {
				addClientOutgoing(
					"""{"method":"notifications/roots/list_changed","jsonrpc":"2.0"}""",
				)
				addServerIncoming(
					"""{"method":"notifications/roots/list_changed","jsonrpc":"2.0"}""",
				)
			}
			assertLinesMatch(expectedNotification2, log, "Check addition notification logs")
			log.clear()

			// 8) Another roots/list request from the server to confirm the current set of roots
			val listRootsResponse2: JsonRpcResponse = server.sendRequest { id ->
				ListRootsRequest(id)
			}
			advanceUntilIdle()

			assertNotNull(listRootsResponse2.result, "Expected a result from second roots/list request.")
			assertEquals(null, listRootsResponse2.error, "Should not produce an error on second roots/list.")
			val listRootsResult2 = listRootsResponse2.result.deserializeResult<ListRootsResult>()
			assertNotNull(listRootsResult2, "Expected a valid ListRootsResult after updates")

			// Should now have 2 roots: projectB (since we removed projectA) and projectC
			assertEquals(2, listRootsResult2.roots.size)
			val updatedUris = listRootsResult2.roots
			assertEquals(
				listOf(
					Root(uri = "file:///home/user/projectB", name = "Project B"),
					rootC,
				),
				updatedUris,
				"Should have removed projectA, added projectC",
			)
			log.clear()

			// 9) Try to remove in-existing root
			val removeFail = client.removeRootByName("Project A")
			assertEquals(false, removeFail, "Should not be able to remove non-existing root")

			// 10) Remove root by URI
			val removedB = client.removeRootByUri("file:///home/user/projectB")
			assertEquals(true, removedB, "Should be able to remove existing root")
			advanceUntilIdle()
			assertEquals(2, log.size, "Should have sent a notification for the removal")
			log.clear()

			// 11) Remove root by providing copy instance (should work because we're using data classes)
			val removedC = client.removeRoot(rootC.copy())
			assertEquals(true, removedC, "Should be able to remove existing root")
			advanceUntilIdle()
			assertEquals(2, log.size, "Should have sent a notification for the removal")
			log.clear()
		}
}
