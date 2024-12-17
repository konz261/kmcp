@file:OptIn(ExperimentalCoroutinesApi::class)

package sh.ondr.kmcp.runtime.integration

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import sh.ondr.kmcp.assertLinesMatch
import sh.ondr.kmcp.client
import sh.ondr.kmcp.logLines
import sh.ondr.kmcp.runtime.Client
import sh.ondr.kmcp.runtime.Server
import sh.ondr.kmcp.runtime.transport.TestTransport
import sh.ondr.kmcp.schema.core.PingRequest
import sh.ondr.kmcp.server
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class McpInitializationTest {
	@Test
	fun testInitHandshakeExact() =
		runTest {
			val testDispatcher = StandardTestDispatcher(testScheduler)
			val log = mutableListOf<String>()

			val (clientTransport, serverTransport) = TestTransport.Companion.createClientAndServerTransport()
			val server =
				Server.Builder()
					.withDispatcher(testDispatcher)
					.withTransport(serverTransport)
					.withLogger { line -> log.server(line) }
					.build()

			server.start()

			val client =
				Client.Builder()
					.withTransport(clientTransport)
					.withDispatcher(testDispatcher)
					.withRawLogger { line -> log.add("CLIENT $line") }
					.withClientInfo("TestClient", "1.0.0")
					.build()
			client.start()

			client.initialize()
			advanceUntilIdle()

			val expected =
				logLines {
					clientOutgoing(
						"""{"method":"initialize","jsonrpc":"2.0","id":"1","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"TestClient","version":"1.0.0"}}}""",
					)
					serverIncoming(
						"""{"method":"initialize","jsonrpc":"2.0","id":"1","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"TestClient","version":"1.0.0"}}}""",
					)
					serverOutgoing(
						"""{"jsonrpc":"2.0","id":"1","result":{"protocolVersion":"2024-11-05","capabilities":{},"serverInfo":{"name":"TestServer","version":"1.0.0"}}}""",
					)
					clientIncoming(
						"""{"jsonrpc":"2.0","id":"1","result":{"protocolVersion":"2024-11-05","capabilities":{},"serverInfo":{"name":"TestServer","version":"1.0.0"}}}""",
					)
					clientOutgoing("""{"method":"notifications/initialized","jsonrpc":"2.0"}""")
					serverIncoming("""{"method":"notifications/initialized","jsonrpc":"2.0"}""")
				}

			assertLinesMatch(expected, log, "Initialization handshake test")
		}

	@Test
	fun testPing() =
		runTest {
			val testDispatcher = StandardTestDispatcher(testScheduler)
			val log = mutableListOf<String>()

			val (clientTransport, serverTransport) = TestTransport.Companion.createClientAndServerTransport()
			val server =
				Server.Builder()
					.withDispatcher(testDispatcher)
					.withTransport(serverTransport)
					.withLogger { line -> log.server(line) }
					.build()
			server.start()

			val client =
				Client.Builder()
					.withTransport(clientTransport)
					.withDispatcher(testDispatcher)
					.withRawLogger { line -> log.client(line) }
					.withClientInfo("TestClient", "1.0.0")
					.build()
			client.start()

			// First, do initialization dance
			client.initialize()
			advanceUntilIdle()

			// Clear the log now that initialization is done to focus on ping
			log.clear()

			// Send a ping request from the client to the server
			val response = client.sendRequest { id -> PingRequest(id = id) }
			advanceUntilIdle()

			// Check that response has no error and has an empty object result "{}"
			assertNull(response.error, "Ping should not return an error.")
			assertEquals("{}", response.result.toString(), "Ping result should be an empty object.")

			val expected =
				logLines {
					clientOutgoing("""{"method":"ping","jsonrpc":"2.0","id":"2"}""")
					serverIncoming("""{"method":"ping","jsonrpc":"2.0","id":"2"}""")
					serverOutgoing("""{"jsonrpc":"2.0","id":"2","result":{}}""")
					clientIncoming("""{"jsonrpc":"2.0","id":"2","result":{}}""")
				}

			assertLinesMatch(expected, log, "ping test")
		}
}
