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
import sh.ondr.mcp4k.runtime.annotation.McpPrompt
import sh.ondr.mcp4k.runtime.transport.ChannelTransport
import sh.ondr.mcp4k.schema.core.JsonRpcErrorCodes
import sh.ondr.mcp4k.schema.prompts.GetPromptRequest
import sh.ondr.mcp4k.schema.prompts.GetPromptRequest.GetPromptParams
import sh.ondr.mcp4k.schema.prompts.GetPromptResult
import sh.ondr.mcp4k.serverIncoming
import sh.ondr.mcp4k.serverOutgoing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * A prompt that requires a 'code' argument.
 * Without it, or if arguments are malformed, it should fail.
 *
 * @param code The code to review.
 */
@McpPrompt
fun strictReviewPrompt(code: String) =
	GetPromptResult(
		description = "Strict code review prompt",
		messages = emptyList(),
	)

class PromptErrorTest {
	@OptIn(ExperimentalCoroutinesApi::class)
	@Test
	fun testUnknownPromptName() =
		runTest {
			val testDispatcher = StandardTestDispatcher(testScheduler)
			val log = mutableListOf<String>()

			val clientTransport = ChannelTransport()
			val serverTransport = clientTransport.flip()
			val server = Server.Builder()
				.withDispatcher(testDispatcher)
				.withPrompt(::strictReviewPrompt)
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

			// Perform initialization
			client.initialize()
			advanceUntilIdle()
			log.clear()

			// Request a non-existent prompt
			val response = client.sendRequest { id ->
				GetPromptRequest(
					id = id,
					params = GetPromptParams(
						name = "unknownPrompt",
						arguments = mapOf("code" to "print('hello')"),
					),
				)
			}
			advanceUntilIdle()

			val expected = buildLog {
				addClientOutgoing(
					"""{"method":"prompts/get","jsonrpc":"2.0","id":"2","params":{"name":"unknownPrompt","arguments":{"code":"print('hello')"}}}""",
				)
				addServerIncoming(
					"""{"method":"prompts/get","jsonrpc":"2.0","id":"2","params":{"name":"unknownPrompt","arguments":{"code":"print('hello')"}}}""",
				)
				addServerOutgoing(
					"""{"jsonrpc":"2.0","id":"2","error":{"code":${JsonRpcErrorCodes.METHOD_NOT_FOUND},"message":"Prompt 'unknownPrompt' not registered on this server."}}""",
				)
				addClientIncoming(
					"""{"jsonrpc":"2.0","id":"2","error":{"code":${JsonRpcErrorCodes.METHOD_NOT_FOUND},"message":"Prompt 'unknownPrompt' not registered on this server."}}""",
				)
			}

			assertLinesMatch(expected, log, "unknown prompt name test")
			assertNotNull(response.error)
			assertEquals(JsonRpcErrorCodes.METHOD_NOT_FOUND, response.error!!.code)
		}

	@OptIn(ExperimentalCoroutinesApi::class)
	@Test
	fun testMissingRequiredArgument() =
		runTest {
			val testDispatcher = StandardTestDispatcher(testScheduler)
			val log = mutableListOf<String>()

			val clientTransport = ChannelTransport()
			val serverTransport = clientTransport.flip()
			val server = Server.Builder()
				.withDispatcher(testDispatcher)
				.withPrompt(::strictReviewPrompt)
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

			// Perform initialization
			client.initialize()
			advanceUntilIdle()
			log.clear()

			// Omit the 'code' argument
			val response = client.sendRequest { id ->
				GetPromptRequest(
					id = id,
					params = GetPromptParams(
						name = "strictReviewPrompt",
						arguments = emptyMap(), // no 'code'
					),
				)
			}
			advanceUntilIdle()

			val expected = buildLog {
				addClientOutgoing("""{"method":"prompts/get","jsonrpc":"2.0","id":"2","params":{"name":"strictReviewPrompt","arguments":{}}}""")
				addServerIncoming("""{"method":"prompts/get","jsonrpc":"2.0","id":"2","params":{"name":"strictReviewPrompt","arguments":{}}}""")
				// Expect an error: missing required argument 'code'.
				// Server returns INVALID_PARAMS.
				addServerOutgoing(
					"""{"jsonrpc":"2.0","id":"2","error":{"code":${JsonRpcErrorCodes.INVALID_PARAMS},"message":"Missing required argument 'code'"}}""",
				)
				addClientIncoming(
					"""{"jsonrpc":"2.0","id":"2","error":{"code":${JsonRpcErrorCodes.INVALID_PARAMS},"message":"Missing required argument 'code'"}}""",
				)
			}

			assertLinesMatch(expected, log, "missing required argument test")
			assertNotNull(response.error)
			assertEquals(JsonRpcErrorCodes.INVALID_PARAMS, response.error.code)
		}

	@OptIn(ExperimentalCoroutinesApi::class)
	@Test
	fun testUnknownArgument() =
		runTest {
			val testDispatcher = StandardTestDispatcher(testScheduler)
			val log = mutableListOf<String>()

			val clientTransport = ChannelTransport()
			val serverTransport = clientTransport.flip()
			val server = Server.Builder()
				.withDispatcher(testDispatcher)
				.withPrompt(::strictReviewPrompt)
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

			// Perform initialization
			client.initialize()
			advanceUntilIdle()
			log.clear()

			// Provide an unknown argument 'foo'.
			val response = client.sendRequest { id ->
				GetPromptRequest(
					id = id,
					params = GetPromptParams(
						name = "strictReviewPrompt",
						arguments = mapOf("foo" to "bar"),
					),
				)
			}
			advanceUntilIdle()

			val expected = buildLog {
				addClientOutgoing(
					"""{"method":"prompts/get","jsonrpc":"2.0","id":"2","params":{"name":"strictReviewPrompt","arguments":{"foo":"bar"}}}""",
				)
				addServerIncoming(
					"""{"method":"prompts/get","jsonrpc":"2.0","id":"2","params":{"name":"strictReviewPrompt","arguments":{"foo":"bar"}}}""",
				)
				// Expect an error: unknown argument 'foo'.
				// Server returns INVALID_PARAMS again:
				addServerOutgoing(
					"""{"jsonrpc":"2.0","id":"2","error":{"code":${JsonRpcErrorCodes.INVALID_PARAMS},"message":"Unknown argument 'foo' for prompt 'strictReviewPrompt'"}}""",
				)
				addClientIncoming(
					"""{"jsonrpc":"2.0","id":"2","error":{"code":${JsonRpcErrorCodes.INVALID_PARAMS},"message":"Unknown argument 'foo' for prompt 'strictReviewPrompt'"}}""",
				)
			}

			assertLinesMatch(expected, log, "unknown argument test")
			assertNotNull(response.error)
			assertEquals(JsonRpcErrorCodes.INVALID_PARAMS, response.error.code)
		}

	@OptIn(ExperimentalCoroutinesApi::class)
	@Test
	fun testNoNameFieldInGetRequest() =
		runTest {
			val testDispatcher = StandardTestDispatcher(testScheduler)
			val log = mutableListOf<String>()

			val clientTransport = ChannelTransport()
			val serverTransport = clientTransport.flip()
			val server = Server.Builder()
				.withDispatcher(testDispatcher)
				.withPrompt(::strictReviewPrompt)
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

			// Perform initialization
			client.initialize()
			advanceUntilIdle()
			log.clear()

			// Manually send a malformed request (no 'name' field)
			val malformedJson = """{"method":"prompts/get","jsonrpc":"2.0","id":"2","params":{"arguments":{"code":"print('hello')"}}}"""
			clientTransport.writeString(malformedJson)

			advanceUntilIdle()

			val expected = buildLog {
				// We don't expect client outgoing because we are manually sending the request.
				addServerIncoming(malformedJson)
				// Expect an error: missing 'name' field in params.
				addServerOutgoing(
					"""{"jsonrpc":"2.0","id":"2","error":{"code":${JsonRpcErrorCodes.INVALID_PARAMS},"message":"Missing required field 'name' in prompts/get request"}}""",
				)
				addClientIncoming(
					"""{"jsonrpc":"2.0","id":"2","error":{"code":${JsonRpcErrorCodes.INVALID_PARAMS},"message":"Missing required field 'name' in prompts/get request"}}""",
				)
			}

			assertLinesMatch(expected, log, "no name field in get request test")
		}
}
