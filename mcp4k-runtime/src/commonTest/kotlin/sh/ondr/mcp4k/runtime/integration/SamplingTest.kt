package sh.ondr.mcp4k.runtime.integration

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import sh.ondr.mcp4k.assertLinesMatch
import sh.ondr.mcp4k.buildLog
import sh.ondr.mcp4k.clientIncoming
import sh.ondr.mcp4k.clientOutgoing
import sh.ondr.mcp4k.runtime.Client
import sh.ondr.mcp4k.runtime.Server
import sh.ondr.mcp4k.runtime.sampling.SamplingProvider
import sh.ondr.mcp4k.runtime.serialization.deserializeResult
import sh.ondr.mcp4k.runtime.transport.ChannelTransport
import sh.ondr.mcp4k.schema.content.TextContent
import sh.ondr.mcp4k.schema.core.Role
import sh.ondr.mcp4k.schema.sampling.CreateMessageRequest
import sh.ondr.mcp4k.schema.sampling.CreateMessageRequest.CreateMessageParams
import sh.ondr.mcp4k.schema.sampling.CreateMessageResult
import sh.ondr.mcp4k.schema.sampling.SamplingMessage
import sh.ondr.mcp4k.serverIncoming
import sh.ondr.mcp4k.serverOutgoing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SamplingTest {
	@OptIn(ExperimentalCoroutinesApi::class)
	@Test
	fun testSamplingRequestFlow() =
		runTest {
			val testDispatcher = StandardTestDispatcher(testScheduler)
			val log = mutableListOf<String>()

			// 1) Build a dummy sampling provider that just returns a fixed text
			val dummyProvider = SamplingProvider { params ->
				CreateMessageResult(
					model = "dummy-model",
					role = Role.ASSISTANT,
					content = TextContent("Dummy completion result"),
					stopReason = "endTurn",
				)
			}

			// 2) Create test transport
			val clientTransport = ChannelTransport()
			val serverTransport = clientTransport.flip()

			// 3) Build the server
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

			// 4) Build the client, with sampling provider & sampling capabilities
			val client = Client.Builder()
				.withClientInfo("TestClient", "1.0.0")
				.withDispatcher(testDispatcher)
				.withPermissionCallback { userApprovable -> true }
				.withSamplingProvider(dummyProvider)
				.withTransport(clientTransport)
				.withTransportLogger(
					logIncoming = { msg -> log.add(clientIncoming(msg)) },
					logOutgoing = { msg -> log.add(clientOutgoing(msg)) },
				)
				.build()
			client.start()

			// 5) Perform MCP initialization and check capabilities
			client.initialize()
			advanceUntilIdle()

			val expectedInitLogs = buildLog {
				addClientOutgoing(
					"""{"method":"initialize","jsonrpc":"2.0","id":"1","params":{"protocolVersion":"2024-11-05","capabilities":{"roots":{"listChanged":true},"sampling":{}},"clientInfo":{"name":"TestClient","version":"1.0.0"}}}""",
				)
				addServerIncoming(
					"""{"method":"initialize","jsonrpc":"2.0","id":"1","params":{"protocolVersion":"2024-11-05","capabilities":{"roots":{"listChanged":true},"sampling":{}},"clientInfo":{"name":"TestClient","version":"1.0.0"}}}""",
				)
				addServerOutgoing(
					"""{"jsonrpc":"2.0","id":"1","result":{"protocolVersion":"2024-11-05","capabilities":{},"serverInfo":{"name":"TestServer","version":"1.0.0"}}}""",
				)
				addClientIncoming(
					"""{"jsonrpc":"2.0","id":"1","result":{"protocolVersion":"2024-11-05","capabilities":{},"serverInfo":{"name":"TestServer","version":"1.0.0"}}}""",
				)
				addClientOutgoing(
					"""{"method":"notifications/initialized","jsonrpc":"2.0"}""",
				)
				addServerIncoming(
					"""{"method":"notifications/initialized","jsonrpc":"2.0"}""",
				)
			}
			assertLinesMatch(expectedInitLogs, log, "Check sampling in init handshake")
			log.clear()

			// 6) Request sampling from client
			val samplingRequest = server.sendRequest { id ->
				CreateMessageRequest(
					id = id,
					params = CreateMessageParams(
						messages = listOf(
							SamplingMessage(
								role = Role.USER,
								content = TextContent("Hello from the server test"),
							),
						),
						maxTokens = 50,
					),
				)
			}
			advanceUntilIdle()

			// 7) Check that the server received a response with no error
			assertNotNull(samplingRequest.result, "Expected a result from sampling request.")
			assertEquals(null, samplingRequest.error, "Sampling request should not produce an error.")

			// 8) Parse the result into CreateMessageResult
			val createMsgResult = samplingRequest.result.deserializeResult<CreateMessageResult>()
			assertNotNull(createMsgResult, "Expected a valid CreateMessageResult.")
			assertEquals("dummy-model", createMsgResult.model)
			val text = (createMsgResult.content as? TextContent)?.text
			assertEquals("Dummy completion result", text)

			val expected = buildLog {
				addServerOutgoing(
					"""{"method":"sampling/createMessage","jsonrpc":"2.0","id":"1","params":{"messages":[{"role":"user","content":{"type":"text","text":"Hello from the server test"}}],"maxTokens":50}}""",
				)
				addClientIncoming(
					"""{"method":"sampling/createMessage","jsonrpc":"2.0","id":"1","params":{"messages":[{"role":"user","content":{"type":"text","text":"Hello from the server test"}}],"maxTokens":50}}""",
				)
				addClientOutgoing(
					"""{"jsonrpc":"2.0","id":"1","result":{"content":{"type":"text","text":"Dummy completion result"},"model":"dummy-model","role":"assistant","stopReason":"endTurn"}}""",
				)
				addServerIncoming(
					"""{"jsonrpc":"2.0","id":"1","result":{"content":{"type":"text","text":"Dummy completion result"},"model":"dummy-model","role":"assistant","stopReason":"endTurn"}}""",
				)
			}

			assertLinesMatch(expected, log, "sampling test")
		}
}
