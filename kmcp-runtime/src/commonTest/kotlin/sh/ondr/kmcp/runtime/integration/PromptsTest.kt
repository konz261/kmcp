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
import sh.ondr.kmcp.schema.core.Role
import sh.ondr.kmcp.schema.prompts.GetPromptRequest
import sh.ondr.kmcp.schema.prompts.GetPromptResult
import sh.ondr.kmcp.schema.prompts.ListPromptsRequest
import sh.ondr.kmcp.schema.prompts.PromptArgument
import sh.ondr.kmcp.schema.prompts.PromptMessage
import sh.ondr.kmcp.server
import kotlin.test.Test

class PromptsTest {
	@OptIn(ExperimentalCoroutinesApi::class)
	@Test
	fun testPromptsListAndGet() =
		runTest {
			val testDispatcher = StandardTestDispatcher(testScheduler)
			val log = mutableListOf<String>()

			val (clientTransport, serverTransport) = TestTransport.createClientAndServerTransport()

			val server =
				Server.Builder()
					.withDispatcher(testDispatcher)
					.withTransport(serverTransport)
					.withLogger { line -> log.server(line) }
					// First prompt: no arguments, returns a static message
					.withPrompt(
						name = "simple_greeting",
						description = "A simple static greeting prompt",
					) { _ ->
						GetPromptResult(
							description = "A simple static greeting",
							messages =
								listOf(
									PromptMessage(
										role = Role.USER,
										content = TextContent("Hello from simple_greeting!"),
									),
								),
						)
					}
					// Second prompt: has two arguments: firstName (required) and lastName (not required)
					.withPrompt(
						name = "personal_greeting",
						description = "Greet the user by first and last name",
						arguments =
							listOf(
								PromptArgument(name = "firstName", description = "User's first name", required = true),
								PromptArgument(name = "lastName", description = "User's last name"),
							),
					) { args ->
						val firstName = args?.get("firstName") ?: error("Missing firstName")
						val lastName = args["lastName"] ?: error("Missing lastName")
						GetPromptResult(
							description = "A personalized greeting",
							messages =
								listOf(
									PromptMessage(
										role = Role.ASSISTANT,
										content = TextContent("Hello, $firstName $lastName! Welcome to the system."),
									),
								),
						)
					}
					.withServerInfo("PromptServer", "2.0.0")
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

			// First, do the initialization dance
			client.initialize()
			advanceUntilIdle()

			log.clear()

			// 1) Test prompts/list
			val listResponse =
				client.sendRequest { id ->
					ListPromptsRequest(id = id)
				}
			advanceUntilIdle()

			// Construct the expected schema for prompts
			// We know we have 2 prompts: simple_greeting (no arguments), personal_greeting (2 arguments)
			val expectedListResult = """{"prompts":[{"name":"simple_greeting","description":"A simple static greeting prompt"},{"name":"personal_greeting","description":"Greet the user by first and last name","arguments":[{"name":"firstName","description":"User's first name","required":true},{"name":"lastName","description":"User's last name","required":false}]}]}"""

			val expectedListLog =
				logLines {
					clientOutgoing("""{"method":"prompts/list","jsonrpc":"2.0","id":"2"}""")
					serverIncoming("""{"method":"prompts/list","jsonrpc":"2.0","id":"2"}""")
					serverOutgoing("""{"jsonrpc":"2.0","id":"2","result":$expectedListResult}""")
					clientIncoming("""{"jsonrpc":"2.0","id":"2","result":$expectedListResult}""")
				}

			assertLinesMatch(expectedListLog, log, "prompts list test")

			// Clear log before next request
			log.clear()

			// 2) Test prompts/get with arguments
			val getResponse =
				client.sendRequest { id ->
					GetPromptRequest(
						id = id,
						params =
							GetPromptRequest.GetPromptParams(
								name = "personal_greeting",
								arguments = mapOf("firstName" to "Jane", "lastName" to "Doe"),
							),
					)
				}
			advanceUntilIdle()

			val expectedGetResult =
				"""{"description":"A personalized greeting","messages":[{"role":"assistant","content":{"type":"text","text":"Hello, Jane Doe! Welcome to the system."}}]}"""

			val expectedGetLog =
				logLines {
					clientOutgoing(
						"""{"method":"prompts/get","jsonrpc":"2.0","id":"3","params":{"name":"personal_greeting","arguments":{"firstName":"Jane","lastName":"Doe"}}}""",
					)
					serverIncoming(
						"""{"method":"prompts/get","jsonrpc":"2.0","id":"3","params":{"name":"personal_greeting","arguments":{"firstName":"Jane","lastName":"Doe"}}}""",
					)
					serverOutgoing("""{"jsonrpc":"2.0","id":"3","result":$expectedGetResult}""")
					clientIncoming("""{"jsonrpc":"2.0","id":"3","result":$expectedGetResult}""")
				}

			assertLinesMatch(expectedGetLog, log, "prompts get test")
			println(getResponse)
		}
}
