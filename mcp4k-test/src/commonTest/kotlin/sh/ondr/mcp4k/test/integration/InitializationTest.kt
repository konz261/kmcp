@file:OptIn(ExperimentalCoroutinesApi::class)

package sh.ondr.mcp4k.test.integration

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import sh.ondr.mcp4k.generated.initializer.Mcp4kInitializer
import sh.ondr.mcp4k.runtime.Client
import sh.ondr.mcp4k.runtime.Server
import sh.ondr.mcp4k.runtime.transport.ChannelTransport
import sh.ondr.mcp4k.schema.capabilities.InitializeResult
import sh.ondr.mcp4k.schema.core.PingRequest
import sh.ondr.mcp4k.test.assertLinesMatch
import sh.ondr.mcp4k.test.buildLog
import sh.ondr.mcp4k.test.clientIncoming
import sh.ondr.mcp4k.test.clientOutgoing
import sh.ondr.mcp4k.test.prompts.simpleCodeReviewPrompt
import sh.ondr.mcp4k.test.prompts.simpleGreet
import sh.ondr.mcp4k.test.serverIncoming
import sh.ondr.mcp4k.test.serverOutgoing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class InitializationTest {
	companion object {
		init {
			// Force initialization
			Mcp4kInitializer.toString()
		}
	}

	@Test
	fun testInitHandshakeExact() =
		runTest {
			val testDispatcher = StandardTestDispatcher(testScheduler)
			val log = mutableListOf<String>()

			val clientTransport = ChannelTransport()
			val serverTransport = clientTransport.flip()
			val server = Server.Builder()
				.withServerInfo("TestServer", "1.0.0")
				.withDispatcher(testDispatcher)
				.withTransport(serverTransport)
				.withTransportLogger(
					logIncoming = { msg -> log.add(serverIncoming(msg)) },
					logOutgoing = { msg -> log.add(serverOutgoing(msg)) },
				)
				.build()

			server.start()

			val client = Client.Builder()
				.withClientInfo("TestClient", "1.0.0")
				.withTransport(clientTransport)
				.withDispatcher(testDispatcher)
				.withTransportLogger(
					logIncoming = { msg -> log.add(clientIncoming(msg)) },
					logOutgoing = { msg -> log.add(clientOutgoing(msg)) },
				)
				.build()
			client.start()

			// Capture the InitializeResult
			val initResult: InitializeResult? = client.initialize()
			advanceUntilIdle()

			// Assert the initialization result is correct
			assertNotNull(initResult, "Initialization result was null!")
			assertEquals("2024-11-05", initResult.protocolVersion, "MCP version mismatch")
			assertEquals("TestServer", initResult.serverInfo.name, "Server name mismatch")
			assertEquals("1.0.0", initResult.serverInfo.version, "Server version mismatch")

			// Verify the exact message sequence over the transport
			val expected = buildLog {
				addClientOutgoing(
					"""{"method":"initialize","jsonrpc":"2.0","id":"1","params":{"protocolVersion":"2024-11-05","capabilities":{"roots":{"listChanged":true}},"clientInfo":{"name":"TestClient","version":"1.0.0"}}}""",
				)
				addServerIncoming(
					"""{"method":"initialize","jsonrpc":"2.0","id":"1","params":{"protocolVersion":"2024-11-05","capabilities":{"roots":{"listChanged":true}},"clientInfo":{"name":"TestClient","version":"1.0.0"}}}""",
				)
				addServerOutgoing(
					"""{"jsonrpc":"2.0","id":"1","result":{"protocolVersion":"2024-11-05","capabilities":{},"serverInfo":{"name":"TestServer","version":"1.0.0"}}}""",
				)
				addClientIncoming(
					"""{"jsonrpc":"2.0","id":"1","result":{"protocolVersion":"2024-11-05","capabilities":{},"serverInfo":{"name":"TestServer","version":"1.0.0"}}}""",
				)
				addClientOutgoing("""{"method":"notifications/initialized","jsonrpc":"2.0"}""")
				addServerIncoming("""{"method":"notifications/initialized","jsonrpc":"2.0"}""")
			}

			assertLinesMatch(expected, log, "Initialization handshake test")
		}

	@Test
	fun testPing() =
		runTest {
			val testDispatcher = StandardTestDispatcher(testScheduler)
			val log = mutableListOf<String>()

			val clientTransport = ChannelTransport()
			val serverTransport = clientTransport.flip()
			val server = Server.Builder()
				.withServerInfo("TestServer", "1.0.0")
				.withDispatcher(testDispatcher)
				.withTransport(serverTransport)
				.withTransportLogger(
					logIncoming = { msg -> log.add(serverIncoming(msg)) },
					logOutgoing = { msg -> log.add(serverOutgoing(msg)) },
				)
				.build()
			server.start()

			val client = Client.Builder()
				.withClientInfo("TestClient", "1.0.0")
				.withDispatcher(testDispatcher)
				.withTransport(clientTransport)
				.withTransportLogger(
					logIncoming = { msg -> log.add(clientIncoming(msg)) },
					logOutgoing = { msg -> log.add(clientOutgoing(msg)) },
				)
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

			val expected = buildLog {
				addClientOutgoing("""{"method":"ping","jsonrpc":"2.0","id":"2"}""")
				addServerIncoming("""{"method":"ping","jsonrpc":"2.0","id":"2"}""")
				addServerOutgoing("""{"jsonrpc":"2.0","id":"2","result":{}}""")
				addClientIncoming("""{"jsonrpc":"2.0","id":"2","result":{}}""")
			}

			assertLinesMatch(expected, log, "ping test")
		}

	@OptIn(ExperimentalCoroutinesApi::class)
	@Test
	fun testInitWithCapabilities() =
		runTest {
			val testDispatcher = StandardTestDispatcher(testScheduler)
			val log = mutableListOf<String>()

			val clientTransport = ChannelTransport()
			val serverTransport = clientTransport.flip()
			val server = Server.Builder()
				.withServerInfo("TestServer", "1.0.0")
				.withDispatcher(testDispatcher)
				.withPrompt(::simpleCodeReviewPrompt)
				.withTool(::simpleGreet)
				.withTransport(serverTransport)
				.withTransportLogger(
					logIncoming = { msg -> log.add(serverIncoming(msg)) },
					logOutgoing = { msg -> log.add(serverOutgoing(msg)) },
				)
				.build()
			server.start()

			val client = Client.Builder()
				.withClientInfo("TestClient", "1.0.0")
				.withTransport(clientTransport)
				.withDispatcher(testDispatcher)
				.withTransportLogger(
					logIncoming = { msg -> log.add(clientIncoming(msg)) },
					logOutgoing = { msg -> log.add(clientOutgoing(msg)) },
				)
				.build()
			client.start()

			client.initialize()
			advanceUntilIdle()

			val expected = buildLog {
				addClientOutgoing(
					"""{"method":"initialize","jsonrpc":"2.0","id":"1","params":{"protocolVersion":"2024-11-05","capabilities":{"roots":{"listChanged":true}},"clientInfo":{"name":"TestClient","version":"1.0.0"}}}""",
				)
				addServerIncoming(
					"""{"method":"initialize","jsonrpc":"2.0","id":"1","params":{"protocolVersion":"2024-11-05","capabilities":{"roots":{"listChanged":true}},"clientInfo":{"name":"TestClient","version":"1.0.0"}}}""",
				)
				addServerOutgoing(
					"""{"jsonrpc":"2.0","id":"1","result":{"protocolVersion":"2024-11-05","capabilities":{"prompts":{},"tools":{}},"serverInfo":{"name":"TestServer","version":"1.0.0"}}}""",
				)
				addClientIncoming(
					"""{"jsonrpc":"2.0","id":"1","result":{"protocolVersion":"2024-11-05","capabilities":{"prompts":{},"tools":{}},"serverInfo":{"name":"TestServer","version":"1.0.0"}}}""",
				)
				addClientOutgoing("""{"method":"notifications/initialized","jsonrpc":"2.0"}""")
				addServerIncoming("""{"method":"notifications/initialized","jsonrpc":"2.0"}""")
			}

			assertLinesMatch(expected, log, "Initialization with capabilities test")
		}
}
