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
import sh.ondr.kmcp.runtime.annotation.Prompt
import sh.ondr.kmcp.runtime.serialization.deserializeResult
import sh.ondr.kmcp.runtime.transport.TestTransport
import sh.ondr.kmcp.schema.content.TextContent
import sh.ondr.kmcp.schema.core.Role
import sh.ondr.kmcp.schema.prompts.GetPromptRequest
import sh.ondr.kmcp.schema.prompts.GetPromptRequest.GetPromptParams
import sh.ondr.kmcp.schema.prompts.GetPromptResult
import sh.ondr.kmcp.schema.prompts.ListPromptsRequest
import sh.ondr.kmcp.schema.prompts.PromptMessage
import sh.ondr.kmcp.server
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * This function prompts the user to review some code
 */
@Prompt
fun codeReviewPrompt(code: String): GetPromptResult {
	return GetPromptResult(
		description = "Code review prompt",
		messages = listOf(
			PromptMessage(
				role = Role.USER,
				content = TextContent("Please review the code: $code"),
			),
		),
	)
}

class PromptsTest {
	@OptIn(ExperimentalCoroutinesApi::class)
	@Test
	fun testPromptsList() =
		runTest {
			val testDispatcher = StandardTestDispatcher(testScheduler)
			val log = mutableListOf<String>()

			val (clientTransport, serverTransport) = TestTransport.createClientAndServerTransport()
			val server = Server.Builder()
				.withDispatcher(testDispatcher)
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

			// Perform initialization
			client.initialize()
			advanceUntilIdle()
			log.clear()

			// Send the prompts/list request
			val response = client.sendRequest { id -> ListPromptsRequest(id = id) }
			advanceUntilIdle()

			val expected = logLines {
				clientOutgoing("""{"method":"prompts/list","jsonrpc":"2.0","id":"2"}""")
				serverIncoming("""{"method":"prompts/list","jsonrpc":"2.0","id":"2"}""")
				serverOutgoing(
					"""{"jsonrpc":"2.0","id":"2","result":{"prompts":[{"name":"codeReviewPrompt","description":"This function prompts the user to review some code","arguments":[{"name":"code","required":true}]}]}}""",
				)
				clientIncoming(
					"""{"jsonrpc":"2.0","id":"2","result":{"prompts":[{"name":"codeReviewPrompt","description":"This function prompts the user to review some code","arguments":[{"name":"code","required":true}]}]}}""",
				)
			}

			assertLinesMatch(expected, log, "prompts list test")
		}

	@OptIn(ExperimentalCoroutinesApi::class)
	@Test
	fun testGetPrompt() =
		runTest {
			val testDispatcher = StandardTestDispatcher(testScheduler)
			val log = mutableListOf<String>()

			val (clientTransport, serverTransport) = TestTransport.createClientAndServerTransport()
			val server = Server.Builder()
				.withDispatcher(testDispatcher)
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

			// Perform initialization
			client.initialize()
			advanceUntilIdle()
			log.clear()

			// Now send the prompts/get request
			val response = client.sendRequest { id ->
				// This matches the MCP spec for prompts/get
				GetPromptRequest(
					id = id,
					params = GetPromptParams(
						name = "codeReviewPrompt",
						arguments = mapOf("code" to "some code here"),
					),
				)
			}
			advanceUntilIdle()

			val expected = logLines {
				clientOutgoing(
					"""{"method":"prompts/get","jsonrpc":"2.0","id":"2","params":{"name":"codeReviewPrompt","arguments":{"code":"some code here"}}}""",
				)
				serverIncoming(
					"""{"method":"prompts/get","jsonrpc":"2.0","id":"2","params":{"name":"codeReviewPrompt","arguments":{"code":"some code here"}}}""",
				)
				serverOutgoing(
					"""{"jsonrpc":"2.0","id":"2","result":{"description":"Code review prompt","messages":[{"role":"user","content":{"type":"text","text":"Please review the code: some code here"}}]}}""",
				)
				clientIncoming(
					"""{"jsonrpc":"2.0","id":"2","result":{"description":"Code review prompt","messages":[{"role":"user","content":{"type":"text","text":"Please review the code: some code here"}}]}}""",
				)
			}

			assertLinesMatch(expected, log, "prompts get test")

			// Optionally, verify the actual response object
			val getPromptResult = response.result?.deserializeResult<GetPromptResult>()
			assertNotNull(getPromptResult)
			assertEquals("Code review prompt", getPromptResult.description)
			assertEquals(1, getPromptResult.messages.size)
			val msg = getPromptResult.messages.first()
			assertEquals(Role.USER, msg.role)
			val text = (msg.content as? TextContent)?.text
			assertEquals("Please review the code: some code here", text)
		}
}
