package sh.ondr.mcp4k.test.integration

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import sh.ondr.mcp4k.runtime.Client
import sh.ondr.mcp4k.runtime.Server
import sh.ondr.mcp4k.runtime.serialization.deserializeResult
import sh.ondr.mcp4k.runtime.transport.ChannelTransport
import sh.ondr.mcp4k.schema.content.TextContent
import sh.ondr.mcp4k.schema.core.Role
import sh.ondr.mcp4k.schema.prompts.GetPromptRequest
import sh.ondr.mcp4k.schema.prompts.GetPromptRequest.GetPromptParams
import sh.ondr.mcp4k.schema.prompts.GetPromptResult
import sh.ondr.mcp4k.schema.prompts.ListPromptsRequest
import sh.ondr.mcp4k.schema.prompts.Prompt
import sh.ondr.mcp4k.test.assertLinesMatch
import sh.ondr.mcp4k.test.buildLog
import sh.ondr.mcp4k.test.clientIncoming
import sh.ondr.mcp4k.test.clientOutgoing
import sh.ondr.mcp4k.test.prompts.codeReviewPrompt
import sh.ondr.mcp4k.test.prompts.fifthPrompt
import sh.ondr.mcp4k.test.prompts.fourthPrompt
import sh.ondr.mcp4k.test.prompts.secondPrompt
import sh.ondr.mcp4k.test.prompts.thirdPrompt
import sh.ondr.mcp4k.test.serverIncoming
import sh.ondr.mcp4k.test.serverOutgoing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class PromptsTest {
	@OptIn(ExperimentalCoroutinesApi::class)
	@Test
	fun testPromptsList() =
		runTest {
			val testDispatcher = StandardTestDispatcher(testScheduler)
			val log = mutableListOf<String>()

			val clientTransport = ChannelTransport()
			val serverTransport = clientTransport.flip()
			val server = Server.Builder()
				.withDispatcher(testDispatcher)
				.withPageSize(2)
				.withPrompts(
					Server::codeReviewPrompt,
					::secondPrompt,
					::thirdPrompt,
					::fourthPrompt,
					::fifthPrompt,
				)
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

			// Send the prompts/list request
			val allPrompts = mutableListOf<Prompt>()
			var pageCount = 0

			client.fetchPagesAsFlow(ListPromptsRequest)
				.collect { prompts ->
					pageCount++
					allPrompts += prompts
				}

			advanceUntilIdle()

			// 6) We have 5 total prompts and a page size of 2 => 3 pages (2 + 2 + 1).
			assertEquals(3, pageCount, "Expected exactly 3 pages for 5 prompts with pageSize=2")
			assertEquals(5, allPrompts.size, "Should have exactly 5 prompts in total")

			val expected = buildLog {
				// 1st page
				addClientOutgoing("""{"method":"prompts/list","jsonrpc":"2.0","id":"2"}""")
				addServerIncoming("""{"method":"prompts/list","jsonrpc":"2.0","id":"2"}""")
				addServerOutgoing(
					"""{"jsonrpc":"2.0","id":"2","result":{"prompts":[{"name":"codeReviewPrompt","description":"Code review prompt for Server extension function testing","arguments":[{"name":"code","description":"The code to review","required":true}]},{"name":"secondPrompt","description":"Simple prompt for testing","arguments":[{"name":"code","description":"The code parameter","required":true}]}],"nextCursor":"eyJwYWdlIjoxLCJwYWdlU2l6ZSI6Mn0="}}""",
				)
				addClientIncoming(
					"""{"jsonrpc":"2.0","id":"2","result":{"prompts":[{"name":"codeReviewPrompt","description":"Code review prompt for Server extension function testing","arguments":[{"name":"code","description":"The code to review","required":true}]},{"name":"secondPrompt","description":"Simple prompt for testing","arguments":[{"name":"code","description":"The code parameter","required":true}]}],"nextCursor":"eyJwYWdlIjoxLCJwYWdlU2l6ZSI6Mn0="}}""",
				)

				// 2nd page
				addClientOutgoing("""{"method":"prompts/list","jsonrpc":"2.0","id":"3","params":{"cursor":"eyJwYWdlIjoxLCJwYWdlU2l6ZSI6Mn0="}}""")
				addServerIncoming("""{"method":"prompts/list","jsonrpc":"2.0","id":"3","params":{"cursor":"eyJwYWdlIjoxLCJwYWdlU2l6ZSI6Mn0="}}""")
				addServerOutgoing(
					"""{"jsonrpc":"2.0","id":"3","result":{"prompts":[{"name":"thirdPrompt","description":"Third test prompt","arguments":[{"name":"code","description":"The code parameter","required":true}]},{"name":"fourthPrompt","description":"Fourth test prompt","arguments":[{"name":"code","description":"The code parameter","required":true}]}],"nextCursor":"eyJwYWdlIjoyLCJwYWdlU2l6ZSI6Mn0="}}""",
				)
				addClientIncoming(
					"""{"jsonrpc":"2.0","id":"3","result":{"prompts":[{"name":"thirdPrompt","description":"Third test prompt","arguments":[{"name":"code","description":"The code parameter","required":true}]},{"name":"fourthPrompt","description":"Fourth test prompt","arguments":[{"name":"code","description":"The code parameter","required":true}]}],"nextCursor":"eyJwYWdlIjoyLCJwYWdlU2l6ZSI6Mn0="}}""",
				)

				// 3rd page
				addClientOutgoing("""{"method":"prompts/list","jsonrpc":"2.0","id":"4","params":{"cursor":"eyJwYWdlIjoyLCJwYWdlU2l6ZSI6Mn0="}}""")
				addServerIncoming("""{"method":"prompts/list","jsonrpc":"2.0","id":"4","params":{"cursor":"eyJwYWdlIjoyLCJwYWdlU2l6ZSI6Mn0="}}""")
				addServerOutgoing(
					"""{"jsonrpc":"2.0","id":"4","result":{"prompts":[{"name":"fifthPrompt","description":"Fifth test prompt","arguments":[{"name":"code","description":"The code parameter","required":true}]}]}}""",
				)
				addClientIncoming(
					"""{"jsonrpc":"2.0","id":"4","result":{"prompts":[{"name":"fifthPrompt","description":"Fifth test prompt","arguments":[{"name":"code","description":"The code parameter","required":true}]}]}}""",
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

			val clientTransport = ChannelTransport()
			val serverTransport = clientTransport.flip()
			val server = Server.Builder()
				.withDispatcher(testDispatcher)
				.withPrompt(Server::codeReviewPrompt)
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

			val expected = buildLog {
				addClientOutgoing(
					"""{"method":"prompts/get","jsonrpc":"2.0","id":"2","params":{"name":"codeReviewPrompt","arguments":{"code":"some code here"}}}""",
				)
				addServerIncoming(
					"""{"method":"prompts/get","jsonrpc":"2.0","id":"2","params":{"name":"codeReviewPrompt","arguments":{"code":"some code here"}}}""",
				)
				addServerOutgoing(
					"""{"jsonrpc":"2.0","id":"2","result":{"messages":[{"role":"user","content":{"type":"text","text":"Please review the following code:"}},{"role":"user","content":{"type":"text","text":"```\nsome code here\n```"}}]}}""",
				)
				addClientIncoming(
					"""{"jsonrpc":"2.0","id":"2","result":{"messages":[{"role":"user","content":{"type":"text","text":"Please review the following code:"}},{"role":"user","content":{"type":"text","text":"```\nsome code here\n```"}}]}}""",
				)
			}

			assertLinesMatch(expected, log, "prompts get test")

			// Optionally, verify the actual response object
			val getPromptResult = response.result?.deserializeResult<GetPromptResult>()
			assertNotNull(getPromptResult)
			assertEquals(2, getPromptResult.messages.size)
			val msg = getPromptResult.messages.first()
			assertEquals(Role.USER, msg.role)
			val text = (msg.content as? TextContent)?.text
			assertEquals("Please review the following code:", text)
		}
}
