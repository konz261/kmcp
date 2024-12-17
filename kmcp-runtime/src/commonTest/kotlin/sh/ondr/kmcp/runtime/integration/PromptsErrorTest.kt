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
import sh.ondr.kmcp.schema.content.TextContent
import sh.ondr.kmcp.schema.core.JsonRpcErrorCodes
import sh.ondr.kmcp.schema.core.Role
import sh.ondr.kmcp.schema.prompts.GetPromptRequest
import sh.ondr.kmcp.schema.prompts.GetPromptResult
import sh.ondr.kmcp.schema.prompts.PromptArgument
import sh.ondr.kmcp.schema.prompts.PromptMessage
import sh.ondr.kmcp.server
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PromptsErrorTest {
	@OptIn(ExperimentalCoroutinesApi::class)
	@Test
	fun testGetNonExistentPrompt() =
		runTest {
			val testDispatcher = StandardTestDispatcher(testScheduler)
			val log = mutableListOf<String>()

			// A server with no prompts at all
			val (clientTransport, serverTransport) = TestTransport.createClientAndServerTransport()

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

			// Initialize
			client.initialize()
			advanceUntilIdle()
			log.clear()

			// Attempt to get a non-existent prompt
			val response =
				client.sendRequest { id ->
					GetPromptRequest(
						id = id,
						params = GetPromptRequest.GetPromptParams(name = "does_not_exist"),
					)
				}
			advanceUntilIdle()
			assertNull(response.result)
			assertNotNull(response.error)

			// Expect a -32602 (Invalid params) error since prompt doesn't exist
			val expected =
				logLines {
					clientOutgoing("""{"method":"prompts/get","jsonrpc":"2.0","id":"2","params":{"name":"does_not_exist"}}""")
					serverIncoming("""{"method":"prompts/get","jsonrpc":"2.0","id":"2","params":{"name":"does_not_exist"}}""")
					serverOutgoing(
						"""{"jsonrpc":"2.0","id":"2","error":{"code":${JsonRpcErrorCodes.INVALID_PARAMS},"message":"Invalid params: Prompt not found: does_not_exist"}}""",
					)
					clientIncoming(
						"""{"jsonrpc":"2.0","id":"2","error":{"code":${JsonRpcErrorCodes.INVALID_PARAMS},"message":"Invalid params: Prompt not found: does_not_exist"}}""",
					)
				}

			assertLinesMatch(expected, log, "get non-existent prompt test")
		}

	@OptIn(ExperimentalCoroutinesApi::class)
	@Test
	fun testGetPromptMissingRequiredArgument() =
		runTest {
			val testDispatcher = StandardTestDispatcher(testScheduler)
			val log = mutableListOf<String>()

			// A server with one prompt that requires an argument "name"
			val (clientTransport, serverTransport) = TestTransport.createClientAndServerTransport()

			val server =
				Server.Builder()
					.withDispatcher(testDispatcher)
					.withTransport(serverTransport)
					.withLogger { line -> log.server(line) }
					.withPrompt(
						name = "hello_prompt",
						description = "Requires a 'name' argument",
						arguments = listOf(PromptArgument(name = "name", required = true)),
					) { args ->
						val name = args?.get("name") ?: error("Missing required argument: name")
						GetPromptResult(
							description = "Hello message",
							messages = listOf(PromptMessage(Role.ASSISTANT, TextContent("Hello, $name!"))),
						)
					}
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

			// Initialize
			client.initialize()
			advanceUntilIdle()
			log.clear()

			// Attempt to get the prompt without the required "name" argument
			val response =
				client.sendRequest { id ->
					GetPromptRequest(
						id = id,
						params = GetPromptRequest.GetPromptParams(name = "hello_prompt", arguments = emptyMap()),
					)
				}
			advanceUntilIdle()
			assertNull(response.result)
			assertNotNull(response.error)

			// Expect a -32602 (Invalid params) error for missing required argument
			val expected =
				logLines {
					clientOutgoing("""{"method":"prompts/get","jsonrpc":"2.0","id":"2","params":{"name":"hello_prompt","arguments":{}}}""")
					serverIncoming("""{"method":"prompts/get","jsonrpc":"2.0","id":"2","params":{"name":"hello_prompt","arguments":{}}}""")
					serverOutgoing(
						"""{"jsonrpc":"2.0","id":"2","error":{"code":${JsonRpcErrorCodes.INVALID_PARAMS},"message":"Invalid params: Missing required argument: name"}}""",
					)
					clientIncoming(
						"""{"jsonrpc":"2.0","id":"2","error":{"code":${JsonRpcErrorCodes.INVALID_PARAMS},"message":"Invalid params: Missing required argument: name"}}""",
					)
				}

			assertLinesMatch(expected, log, "prompt missing required argument test")
		}

	@OptIn(ExperimentalCoroutinesApi::class)
	@Test
	fun testGetPromptInternalError() =
		runTest {
			val testDispatcher = StandardTestDispatcher(testScheduler)
			val log = mutableListOf<String>()

			// A server with a prompt that throws an internal error
			val (clientTransport, serverTransport) = TestTransport.createClientAndServerTransport()

			val server =
				Server.Builder()
					.withDispatcher(testDispatcher)
					.withTransport(serverTransport)
					.withLogger { line -> log.server(line) }
					.withPrompt(
						name = "error_prompt",
						description = "Always throws internal error",
						arguments = null,
					) {
						// Deliberately throw some exception
						throw IllegalStateException("Something went wrong internally!")
					}
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

			// Initialize
			client.initialize()
			advanceUntilIdle()
			log.clear()

			// Attempt to get the prompt that throws an internal error
			val response =
				client.sendRequest { id ->
					GetPromptRequest(
						id = id,
						params = GetPromptRequest.GetPromptParams(name = "error_prompt"),
					)
				}
			advanceUntilIdle()
			assertNull(response.result)
			assertNotNull(response.error)

			// Expect a -32603 (Internal error)
			val expected =
				logLines {
					clientOutgoing("""{"method":"prompts/get","jsonrpc":"2.0","id":"2","params":{"name":"error_prompt"}}""")
					serverIncoming("""{"method":"prompts/get","jsonrpc":"2.0","id":"2","params":{"name":"error_prompt"}}""")
					serverOutgoing("""{"jsonrpc":"2.0","id":"2","error":{"code":-32603,"message":"Internal error: Something went wrong internally!"}}""")
					clientIncoming("""{"jsonrpc":"2.0","id":"2","error":{"code":-32603,"message":"Internal error: Something went wrong internally!"}}""")
				}

			assertLinesMatch(expected, log, "internal error test")
		}
}
