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
import sh.ondr.mcp4k.runtime.transport.ChannelTransport
import sh.ondr.mcp4k.schema.core.JsonRpcErrorCodes
import sh.ondr.mcp4k.schema.prompts.GetPromptRequest
import sh.ondr.mcp4k.schema.prompts.GetPromptRequest.GetPromptParams
import sh.ondr.mcp4k.schema.prompts.GetPromptResult
import sh.ondr.mcp4k.server
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

			val expected = logLines {
				clientOutgoing(
					"""{"method":"prompts/get","jsonrpc":"2.0","id":"2","params":{"name":"unknownPrompt","arguments":{"code":"print('hello')"}}}""",
				)
				serverIncoming(
					"""{"method":"prompts/get","jsonrpc":"2.0","id":"2","params":{"name":"unknownPrompt","arguments":{"code":"print('hello')"}}}""",
				)
				serverOutgoing(
					"""{"jsonrpc":"2.0","id":"2","error":{"code":${JsonRpcErrorCodes.METHOD_NOT_FOUND},"message":"Prompt 'unknownPrompt' not registered on this server."}}""",
				)
				clientIncoming(
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

			val expected = logLines {
				clientOutgoing("""{"method":"prompts/get","jsonrpc":"2.0","id":"2","params":{"name":"strictReviewPrompt","arguments":{}}}""")
				serverIncoming("""{"method":"prompts/get","jsonrpc":"2.0","id":"2","params":{"name":"strictReviewPrompt","arguments":{}}}""")
				// Expect an error: missing required argument 'code'.
				// Server returns INVALID_PARAMS.
				serverOutgoing(
					"""{"jsonrpc":"2.0","id":"2","error":{"code":${JsonRpcErrorCodes.INVALID_PARAMS},"message":"Missing required argument 'code'"}}""",
				)
				clientIncoming(
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

			val expected = logLines {
				clientOutgoing(
					"""{"method":"prompts/get","jsonrpc":"2.0","id":"2","params":{"name":"strictReviewPrompt","arguments":{"foo":"bar"}}}""",
				)
				serverIncoming(
					"""{"method":"prompts/get","jsonrpc":"2.0","id":"2","params":{"name":"strictReviewPrompt","arguments":{"foo":"bar"}}}""",
				)
				// Expect an error: unknown argument 'foo'.
				// Server returns INVALID_PARAMS again:
				serverOutgoing(
					"""{"jsonrpc":"2.0","id":"2","error":{"code":${JsonRpcErrorCodes.INVALID_PARAMS},"message":"Unknown argument 'foo' for prompt 'strictReviewPrompt'"}}""",
				)
				clientIncoming(
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

			// Perform initialization
			client.initialize()
			advanceUntilIdle()
			log.clear()

			// Manually send a malformed request (no 'name' field)
			val malformedJson = """{"method":"prompts/get","jsonrpc":"2.0","id":"2","params":{"arguments":{"code":"print('hello')"}}}"""
			clientTransport.writeString(malformedJson)

			advanceUntilIdle()

			val expected = logLines {
				// We don't expect client outgoing because we are manually sending the request.
				serverIncoming(malformedJson)
				// Expect an error: missing 'name' field in params.
				serverOutgoing(
					"""{"jsonrpc":"2.0","id":"2","error":{"code":${JsonRpcErrorCodes.INVALID_PARAMS},"message":"Missing required field 'name' in prompts/get request"}}""",
				)
				clientIncoming(
					"""{"jsonrpc":"2.0","id":"2","error":{"code":${JsonRpcErrorCodes.INVALID_PARAMS},"message":"Missing required field 'name' in prompts/get request"}}""",
				)
			}

			assertLinesMatch(expected, log, "no name field in get request test")
		}
}
