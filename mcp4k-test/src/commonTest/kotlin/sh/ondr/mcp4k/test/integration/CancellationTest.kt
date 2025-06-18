package sh.ondr.mcp4k.test.integration

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import sh.ondr.mcp4k.runtime.Client
import sh.ondr.mcp4k.runtime.Server
import sh.ondr.mcp4k.runtime.transport.ChannelTransport
import sh.ondr.mcp4k.schema.tools.CallToolRequest
import sh.ondr.mcp4k.test.assertLinesMatch
import sh.ondr.mcp4k.test.buildLog
import sh.ondr.mcp4k.test.clientIncoming
import sh.ondr.mcp4k.test.clientOutgoing
import sh.ondr.mcp4k.test.serverIncoming
import sh.ondr.mcp4k.test.serverOutgoing
import sh.ondr.mcp4k.test.tools.slowToolOperation
import kotlin.test.Test
import kotlin.test.fail

class CancellationTest {
	companion object {
		init {
			// Force initialization
			sh.ondr.mcp4k.generated.initializer.Mcp4kInitializer.toString()
		}
	}

	@OptIn(ExperimentalCoroutinesApi::class)
	@Test
	fun testCancellingSlowToolCall() =
		runTest {
			val testDispatcher = StandardTestDispatcher(testScheduler)
			val log = mutableListOf<String>()

			val clientTransport = ChannelTransport()
			val serverTransport = clientTransport.flip()

			val server = Server.Builder()
				.withDispatcher(testDispatcher)
				.withTool(::slowToolOperation)
				.withTransport(serverTransport)
				.withTransportLogger(
					logIncoming = { msg -> log.add(serverIncoming(msg)) },
					logOutgoing = { msg -> log.add(serverOutgoing(msg)) },
				)
				.build()
			server.start()

			val client = Client.Builder()
				.withTransport(clientTransport)
				.withDispatcher(testDispatcher)
				.withTransportLogger(
					logIncoming = { msg -> log.add(clientIncoming(msg)) },
					logOutgoing = { msg -> log.add(clientOutgoing(msg)) },
				)
				.withClientInfo("TestClient", "1.0.0")
				.build()
			client.start()

			client.initialize()
			advanceUntilIdle()
			log.clear()

			val requestJob = launch {
				try {
					client.sendRequest { id ->
						CallToolRequest(
							id = id,
							params = CallToolRequest.CallToolParams(
								name = "slowToolOperation",
								arguments = mapOf("iterations" to JsonPrimitive(20)),
							),
						)
					}
					fail("We expected to see a cancellation, but the request returned without error!")
				} catch (e: CancellationException) {
					// This is the normal, expected outcome
				}
			}

			// Let the server do partial work
			advanceTimeBy(600)

			// Cancel from the client side
			requestJob.cancel("Client doesn't want to wait anymore")
			advanceUntilIdle()

			val expected = buildLog {
				addClientOutgoing(
					"""{"method":"tools/call","jsonrpc":"2.0","id":"2","params":{"name":"slowToolOperation","arguments":{"iterations":20}}}""",
				)
				addServerIncoming(
					"""{"method":"tools/call","jsonrpc":"2.0","id":"2","params":{"name":"slowToolOperation","arguments":{"iterations":20}}}""",
				)
				addClientOutgoing(
					"""{"method":"notifications/cancelled","jsonrpc":"2.0","params":{"requestId":"2","reason":"Client doesn't want to wait anymore"}}""",
				)
				addServerIncoming(
					"""{"method":"notifications/cancelled","jsonrpc":"2.0","params":{"requestId":"2","reason":"Client doesn't want to wait anymore"}}""",
				)
			}

			assertLinesMatch(
				expected,
				log,
				"Cancelling slow tool request test",
			)
		}
}
