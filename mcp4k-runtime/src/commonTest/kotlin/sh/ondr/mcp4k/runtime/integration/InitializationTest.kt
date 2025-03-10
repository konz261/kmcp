@file:OptIn(ExperimentalCoroutinesApi::class)

package sh.ondr.mcp4k.runtime.integration

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import sh.ondr.mcp4k.assertLinesMatch
import sh.ondr.mcp4k.client
import sh.ondr.mcp4k.logLines
import sh.ondr.mcp4k.runtime.Client
import sh.ondr.mcp4k.runtime.Server
import sh.ondr.mcp4k.runtime.annotation.McpPrompt
import sh.ondr.mcp4k.runtime.annotation.McpTool
import sh.ondr.mcp4k.runtime.transport.ChannelTransport
import sh.ondr.mcp4k.schema.content.TextContent
import sh.ondr.mcp4k.schema.content.ToolContent
import sh.ondr.mcp4k.schema.core.PingRequest
import sh.ondr.mcp4k.schema.core.Role
import sh.ondr.mcp4k.schema.prompts.GetPromptResult
import sh.ondr.mcp4k.schema.prompts.PromptMessage
import sh.ondr.mcp4k.server
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InitializationTest {
	@Test
	fun testInitHandshakeExact() =
		runTest {
			val testDispatcher = StandardTestDispatcher(testScheduler)
			val log = mutableListOf<String>()

			val clientTransport = ChannelTransport()
			val serverTransport = clientTransport.flip()
			val server = Server.Builder()
				.withDispatcher(testDispatcher)
				.withTransport(serverTransport)
				.withLogger { line -> log.server(line) }
				.build()

			server.start()

			val client = Client.Builder()
				.withTransport(clientTransport)
				.withDispatcher(testDispatcher)
				.withLogger { line -> log.add("CLIENT $line") }
				.withClientInfo("TestClient", "1.0.0")
				.build()
			client.start()

			client.initialize()
			advanceUntilIdle()

			val expected = logLines {
				clientOutgoing(
					"""{"method":"initialize","jsonrpc":"2.0","id":"1","params":{"protocolVersion":"2024-11-05","capabilities":{"roots":{"listChanged":true}},"clientInfo":{"name":"TestClient","version":"1.0.0"}}}""",
				)
				serverIncoming(
					"""{"method":"initialize","jsonrpc":"2.0","id":"1","params":{"protocolVersion":"2024-11-05","capabilities":{"roots":{"listChanged":true}},"clientInfo":{"name":"TestClient","version":"1.0.0"}}}""",
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

			val clientTransport = ChannelTransport()
			val serverTransport = clientTransport.flip()
			val server = Server.Builder()
				.withDispatcher(testDispatcher)
				.withTransport(serverTransport)
				.withLogger { line -> log.server(line) }
				.build()
			server.start()

			val client = Client.Builder()
				.withTransport(clientTransport)
				.withDispatcher(testDispatcher)
				.withLogger { line -> log.client(line) }
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

			val expected = logLines {
				clientOutgoing("""{"method":"ping","jsonrpc":"2.0","id":"2"}""")
				serverIncoming("""{"method":"ping","jsonrpc":"2.0","id":"2"}""")
				serverOutgoing("""{"jsonrpc":"2.0","id":"2","result":{}}""")
				clientIncoming("""{"jsonrpc":"2.0","id":"2","result":{}}""")
			}

			assertLinesMatch(expected, log, "ping test")
		}

	@OptIn(ExperimentalCoroutinesApi::class)
	@Test
	fun testInitWithCapabilities() =
		runTest {
			val testDispatcher = StandardTestDispatcher(testScheduler)
			val log = mutableListOf<String>()

			// We'll define a simple @McpTool and @McpPrompt right here, or assume they exist in the same package.
			@McpTool
			fun greet(name: String): ToolContent = TextContent("Hello, $name!")

			@McpPrompt
			fun codeReviewPrompt(code: String): GetPromptResult {
				return GetPromptResult(
					description = "A code review prompt",
					messages = listOf(
						PromptMessage(
							role = Role.USER,
							content = TextContent("Review this code: $code"),
						),
					),
				)
			}

			val clientTransport = ChannelTransport()
			val serverTransport = clientTransport.flip()
			val server = Server.Builder()
				.withDispatcher(testDispatcher)
				.withTool(::greet)
				.withPrompt(::codeReviewPrompt)
				.withTransport(serverTransport)
				.withLogger { line -> log.server(line) }
				.build()
			server.start()

			val client = Client.Builder()
				.withTransport(clientTransport)
				.withDispatcher(testDispatcher)
				.withLogger { line -> log.client(line) }
				.withClientInfo("TestClient", "1.0.0")
				.build()
			client.start()

			client.initialize()
			advanceUntilIdle()

			val expected = logLines {
				clientOutgoing(
					"""{"method":"initialize","jsonrpc":"2.0","id":"1","params":{"protocolVersion":"2024-11-05","capabilities":{"roots":{"listChanged":true}},"clientInfo":{"name":"TestClient","version":"1.0.0"}}}""",
				)
				serverIncoming(
					"""{"method":"initialize","jsonrpc":"2.0","id":"1","params":{"protocolVersion":"2024-11-05","capabilities":{"roots":{"listChanged":true}},"clientInfo":{"name":"TestClient","version":"1.0.0"}}}""",
				)
				serverOutgoing(
					"""{"jsonrpc":"2.0","id":"1","result":{"protocolVersion":"2024-11-05","capabilities":{"prompts":{},"tools":{}},"serverInfo":{"name":"TestServer","version":"1.0.0"}}}""",
				)
				clientIncoming(
					"""{"jsonrpc":"2.0","id":"1","result":{"protocolVersion":"2024-11-05","capabilities":{"prompts":{},"tools":{}},"serverInfo":{"name":"TestServer","version":"1.0.0"}}}""",
				)
				clientOutgoing("""{"method":"notifications/initialized","jsonrpc":"2.0"}""")
				serverIncoming("""{"method":"notifications/initialized","jsonrpc":"2.0"}""")
			}

			assertLinesMatch(expected, log, "Initialization with capabilities test")
		}
}
