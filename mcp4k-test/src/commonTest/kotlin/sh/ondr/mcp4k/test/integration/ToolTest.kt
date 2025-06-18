package sh.ondr.mcp4k.test.integration

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import sh.ondr.mcp4k.runtime.Client
import sh.ondr.mcp4k.runtime.Server
import sh.ondr.mcp4k.runtime.serialization.deserializeResult
import sh.ondr.mcp4k.runtime.transport.ChannelTransport
import sh.ondr.mcp4k.schema.content.TextContent
import sh.ondr.mcp4k.schema.core.JsonRpcErrorCodes
import sh.ondr.mcp4k.schema.tools.CallToolRequest
import sh.ondr.mcp4k.schema.tools.CallToolResult
import sh.ondr.mcp4k.schema.tools.ListToolsRequest
import sh.ondr.mcp4k.schema.tools.Tool
import sh.ondr.mcp4k.test.assertLinesMatch
import sh.ondr.mcp4k.test.buildLog
import sh.ondr.mcp4k.test.clientIncoming
import sh.ondr.mcp4k.test.clientOutgoing
import sh.ondr.mcp4k.test.serverIncoming
import sh.ondr.mcp4k.test.serverOutgoing
import sh.ondr.mcp4k.test.tools.greet
import sh.ondr.mcp4k.test.tools.noParamTool
import sh.ondr.mcp4k.test.tools.reverseString
import sh.ondr.mcp4k.test.tools.sendEmail
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ToolsTest {
	companion object {
		init {
			// Force initialization
			sh.ondr.mcp4k.generated.initializer.Mcp4kInitializer.toString()
		}
	}

	@OptIn(ExperimentalCoroutinesApi::class)
	@Test
	fun testToolsList() =
		runTest {
			val testDispatcher = StandardTestDispatcher(testScheduler)
			val log = mutableListOf<String>()

			val serverTransport = ChannelTransport()
			val clientTransport = serverTransport.flip()

			val server = Server.Builder()
				.withDispatcher(testDispatcher)
				.withPageSize(2)
				.withTools(
					Server::greet,
					::sendEmail,
					::reverseString,
					::noParamTool,
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

			// Use fetchPagesAsFlow to retrieve tools across multiple pages
			val allTools = mutableListOf<Tool>()
			var pageCount = 0

			client.fetchPagesAsFlow(ListToolsRequest).collect { pageOfTools ->
				pageCount++
				allTools += pageOfTools
			}
			advanceUntilIdle()

			// With 4 total tools and pageSize=2 => 2 pages
			assertEquals(2, pageCount, "Expected 2 pages of tools")
			assertEquals(4, allTools.size, "Expected a total of 4 tools")

			val expected = buildLog {
				// 1st page
				addClientOutgoing("""{"method":"tools/list","jsonrpc":"2.0","id":"2"}""")
				addServerIncoming("""{"method":"tools/list","jsonrpc":"2.0","id":"2"}""")
				addServerOutgoing(
					"""{"jsonrpc":"2.0","id":"2","result":{"tools":[{"name":"greet","description":"Greets a user with an optional age","inputSchema":{"type":"object","properties":{"name":{"type":"string","description":"The name of the user"},"age":{"type":"number","description":"The age of the user (defaults to 25)"}},"required":["name"]}},{"name":"sendEmail","description":"Sends an email to multiple recipients","inputSchema":{"type":"object","properties":{"recipients":{"type":"array","description":"The list of recipients","items":{"type":"string"}},"email":{"type":"object","description":"Email data class for testing complex parameters","properties":{"title":{"type":"string","description":"The title of the email"},"body":{"type":"string","description":"The body of the email"}},"required":["title"]}},"required":["recipients","email"]}}],"nextCursor":"eyJwYWdlIjoxLCJwYWdlU2l6ZSI6Mn0="}}""",
				)
				addClientIncoming(
					"""{"jsonrpc":"2.0","id":"2","result":{"tools":[{"name":"greet","description":"Greets a user with an optional age","inputSchema":{"type":"object","properties":{"name":{"type":"string","description":"The name of the user"},"age":{"type":"number","description":"The age of the user (defaults to 25)"}},"required":["name"]}},{"name":"sendEmail","description":"Sends an email to multiple recipients","inputSchema":{"type":"object","properties":{"recipients":{"type":"array","description":"The list of recipients","items":{"type":"string"}},"email":{"type":"object","description":"Email data class for testing complex parameters","properties":{"title":{"type":"string","description":"The title of the email"},"body":{"type":"string","description":"The body of the email"}},"required":["title"]}},"required":["recipients","email"]}}],"nextCursor":"eyJwYWdlIjoxLCJwYWdlU2l6ZSI6Mn0="}}""",
				)

				// 2nd page
				addClientOutgoing("""{"method":"tools/list","jsonrpc":"2.0","id":"3","params":{"cursor":"eyJwYWdlIjoxLCJwYWdlU2l6ZSI6Mn0="}}""")
				addServerIncoming("""{"method":"tools/list","jsonrpc":"2.0","id":"3","params":{"cursor":"eyJwYWdlIjoxLCJwYWdlU2l6ZSI6Mn0="}}""")
				addServerOutgoing(
					"""{"jsonrpc":"2.0","id":"3","result":{"tools":[{"name":"reverseString","description":"Reverses a string","inputSchema":{"type":"object","properties":{"s":{"type":"string","description":"The string to reverse"}},"required":["s"]}},{"name":"noParamTool","description":"Tool with no parameters","inputSchema":{"type":"object","properties":{}}}]}}""",
				)
				addClientIncoming(
					"""{"jsonrpc":"2.0","id":"3","result":{"tools":[{"name":"reverseString","description":"Reverses a string","inputSchema":{"type":"object","properties":{"s":{"type":"string","description":"The string to reverse"}},"required":["s"]}},{"name":"noParamTool","description":"Tool with no parameters","inputSchema":{"type":"object","properties":{}}}]}}""",
				)
			}
			assertLinesMatch(expected, log, "tools list paginated test")
		}

	@OptIn(ExperimentalCoroutinesApi::class)
	@Test
	fun testToolsListSimple() =
		runTest {
			val testDispatcher = StandardTestDispatcher(testScheduler)
			val log = mutableListOf<String>()

			val serverTransport = ChannelTransport()
			val clientTransport = serverTransport.flip()

			val server = Server.Builder()
				.withDispatcher(testDispatcher)
				.withPageSize(2)
				.withTools(
					Server::greet,
					::sendEmail,
					::reverseString,
					::noParamTool,
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

			// Use the helper function that internally does the pagination
			val allTools = client.getAllTools()
			advanceUntilIdle()

			// We know there are 4 tools total, with pageSize=2 => 2 pages.
			// The 'getAllTools' helper should retrieve all of them.
			assertEquals(4, allTools.size, "Expected a total of 4 tools")

			// Verify we saw exactly two requests in the logs (one per page).
			val expected = buildLog {
				// 1st page
				addClientOutgoing("""{"method":"tools/list","jsonrpc":"2.0","id":"2"}""")
				addServerIncoming("""{"method":"tools/list","jsonrpc":"2.0","id":"2"}""")
				addServerOutgoing(
					"""{"jsonrpc":"2.0","id":"2","result":{"tools":[{"name":"greet","description":"Greets a user with an optional age","inputSchema":{"type":"object","properties":{"name":{"type":"string","description":"The name of the user"},"age":{"type":"number","description":"The age of the user (defaults to 25)"}},"required":["name"]}},{"name":"sendEmail","description":"Sends an email to multiple recipients","inputSchema":{"type":"object","properties":{"recipients":{"type":"array","description":"The list of recipients","items":{"type":"string"}},"email":{"type":"object","description":"Email data class for testing complex parameters","properties":{"title":{"type":"string","description":"The title of the email"},"body":{"type":"string","description":"The body of the email"}},"required":["title"]}},"required":["recipients","email"]}}],"nextCursor":"eyJwYWdlIjoxLCJwYWdlU2l6ZSI6Mn0="}}""",
				)
				addClientIncoming(
					"""{"jsonrpc":"2.0","id":"2","result":{"tools":[{"name":"greet","description":"Greets a user with an optional age","inputSchema":{"type":"object","properties":{"name":{"type":"string","description":"The name of the user"},"age":{"type":"number","description":"The age of the user (defaults to 25)"}},"required":["name"]}},{"name":"sendEmail","description":"Sends an email to multiple recipients","inputSchema":{"type":"object","properties":{"recipients":{"type":"array","description":"The list of recipients","items":{"type":"string"}},"email":{"type":"object","description":"Email data class for testing complex parameters","properties":{"title":{"type":"string","description":"The title of the email"},"body":{"type":"string","description":"The body of the email"}},"required":["title"]}},"required":["recipients","email"]}}],"nextCursor":"eyJwYWdlIjoxLCJwYWdlU2l6ZSI6Mn0="}}""",
				)

				// 2nd page
				addClientOutgoing("""{"method":"tools/list","jsonrpc":"2.0","id":"3","params":{"cursor":"eyJwYWdlIjoxLCJwYWdlU2l6ZSI6Mn0="}}""")
				addServerIncoming("""{"method":"tools/list","jsonrpc":"2.0","id":"3","params":{"cursor":"eyJwYWdlIjoxLCJwYWdlU2l6ZSI6Mn0="}}""")
				addServerOutgoing(
					"""{"jsonrpc":"2.0","id":"3","result":{"tools":[{"name":"reverseString","description":"Reverses a string","inputSchema":{"type":"object","properties":{"s":{"type":"string","description":"The string to reverse"}},"required":["s"]}},{"name":"noParamTool","description":"Tool with no parameters","inputSchema":{"type":"object","properties":{}}}]}}""",
				)
				addClientIncoming(
					"""{"jsonrpc":"2.0","id":"3","result":{"tools":[{"name":"reverseString","description":"Reverses a string","inputSchema":{"type":"object","properties":{"s":{"type":"string","description":"The string to reverse"}},"required":["s"]}},{"name":"noParamTool","description":"Tool with no parameters","inputSchema":{"type":"object","properties":{}}}]}}""",
				)
			}
			assertLinesMatch(expected, log, "tools list (getAllTools) test")
		}

	@OptIn(ExperimentalCoroutinesApi::class)
	@Test
	fun changeToolsTest() =
		runTest {
			val testDispatcher = StandardTestDispatcher(testScheduler)
			val log = mutableListOf<String>()
			val updatedToolsSnapshots = mutableListOf<List<Tool>>()

			val serverTransport = ChannelTransport()
			val clientTransport = serverTransport.flip()

			val server = Server.Builder()
				.withDispatcher(testDispatcher)
				.withTransport(serverTransport)
				.withTransportLogger(
					logIncoming = { msg -> log.add(serverIncoming(msg)) },
					logOutgoing = { msg -> log.add(serverOutgoing(msg)) },
				)
				.build()
			server.start()

			val client = Client.Builder()
				.withDispatcher(testDispatcher)
				.withTransport(clientTransport)
				.withClientInfo("TestClient", "1.0.0")
				.withTransportLogger(
					logIncoming = { msg -> log.add(clientIncoming(msg)) },
					logOutgoing = { msg -> log.add(clientOutgoing(msg)) },
				)
				.withOnToolsChanged { newTools ->
					updatedToolsSnapshots += newTools
				}
				.build()
			client.start()

			// Initialize
			client.initialize()
			advanceUntilIdle()
			log.clear()

			// Add tool
			server.addTool(::reverseString)
			advanceUntilIdle()

			// Remove tool
			server.removeTool(::reverseString)
			advanceUntilIdle()

			// Check updatedTools callback
			assertEquals(2, updatedToolsSnapshots.size, "Expected exactly two updates")
			val firstUpdate = updatedToolsSnapshots[0]
			assertEquals(1, firstUpdate.size, "First update should contain one tool")
			assertEquals("reverseString", firstUpdate[0].name)

			val secondUpdate = updatedToolsSnapshots[1]
			assertEquals(0, secondUpdate.size, "Second update should be empty after removal")

			// Check raw log
			val expected = buildLog {
				// add‑tool cycle
				addServerOutgoing("""{"method":"notifications/tools/list_changed","jsonrpc":"2.0"}""")
				addClientIncoming("""{"method":"notifications/tools/list_changed","jsonrpc":"2.0"}""")
				addClientOutgoing("""{"method":"tools/list","jsonrpc":"2.0","id":"2"}""")
				addServerIncoming("""{"method":"tools/list","jsonrpc":"2.0","id":"2"}""")
				addServerOutgoing(
					"""{"jsonrpc":"2.0","id":"2","result":{"tools":[{"name":"reverseString","description":"Reverses a string","inputSchema":{"type":"object","properties":{"s":{"type":"string","description":"The string to reverse"}},"required":["s"]}}]}}""",
				)
				addClientIncoming(
					"""{"jsonrpc":"2.0","id":"2","result":{"tools":[{"name":"reverseString","description":"Reverses a string","inputSchema":{"type":"object","properties":{"s":{"type":"string","description":"The string to reverse"}},"required":["s"]}}]}}""",
				)

				// remove‑tool cycle
				addServerOutgoing("""{"method":"notifications/tools/list_changed","jsonrpc":"2.0"}""")
				addClientIncoming("""{"method":"notifications/tools/list_changed","jsonrpc":"2.0"}""")
				addClientOutgoing("""{"method":"tools/list","jsonrpc":"2.0","id":"3"}""")
				addServerIncoming("""{"method":"tools/list","jsonrpc":"2.0","id":"3"}""")
				addServerOutgoing("""{"jsonrpc":"2.0","id":"3","result":{"tools":[]}}""")
				addClientIncoming("""{"jsonrpc":"2.0","id":"3","result":{"tools":[]}}""")
			}
			assertLinesMatch(expected, log, "changeToolsTest log check")
		}

	@OptIn(ExperimentalCoroutinesApi::class)
	@Test
	fun testCallToolGreet() =
		runTest {
			val testDispatcher = StandardTestDispatcher(testScheduler)
			val log = mutableListOf<String>()

			val clientTransport = ChannelTransport()
			val serverTransport = clientTransport.flip()
			val server = Server.Builder()
				.withDispatcher(testDispatcher)
				.withTool(Server::greet)
				.withTool(::sendEmail)
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

			// Call the 'greet' tool
			val response = client.callTool(
				name = "greet",
				arguments = buildMap {
					put("name", JsonPrimitive("Alice"))
				},
			)
			advanceUntilIdle()

			val expected = buildLog {
				addClientOutgoing("""{"method":"tools/call","jsonrpc":"2.0","id":"2","params":{"name":"greet","arguments":{"name":"Alice"}}}""")
				addServerIncoming("""{"method":"tools/call","jsonrpc":"2.0","id":"2","params":{"name":"greet","arguments":{"name":"Alice"}}}""")
				addServerOutgoing(
					"""{"jsonrpc":"2.0","id":"2","result":{"content":[{"type":"text","text":"Hello Alice! You are 25 years old."}]}}""",
				)
				addClientIncoming(
					"""{"jsonrpc":"2.0","id":"2","result":{"content":[{"type":"text","text":"Hello Alice! You are 25 years old."}]}}""",
				)
			}
			assertLinesMatch(expected, log, "tools call greet test")

			// Optionally, verify the actual response
			val callToolResult = response.result?.deserializeResult<CallToolResult>()
			assertNotNull(callToolResult)
			assertEquals(1, callToolResult.content.size)
			val text = (callToolResult.content.first() as? TextContent)?.text
			assertEquals("Hello Alice! You are 25 years old.", text)
		}

	@OptIn(ExperimentalCoroutinesApi::class)
	@Test
	fun testCallToolNotRegistered() =
		runTest {
			val testDispatcher = StandardTestDispatcher(testScheduler)
			val log = mutableListOf<String>()

			val clientTransport = ChannelTransport()
			val serverTransport = clientTransport.flip()

			// 1) Build a server that does NOT register `neverRegisteredTool`.
			//    We only register e.g. "greet" and "sendEmail" to prove that "neverRegisteredTool" is absent from `tools`.
			val server = Server.Builder()
				.withDispatcher(testDispatcher)
				.withTool(Server::greet)
				.withTool(::sendEmail)
				.withTransport(serverTransport)
				.withTransportLogger(
					logIncoming = { msg -> log.add(serverIncoming(msg)) },
					logOutgoing = { msg -> log.add(serverOutgoing(msg)) },
				)
				.build()
			server.start()

			// 2) Build the client
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

			// 3) Perform initialization
			client.initialize()
			advanceUntilIdle()
			log.clear()

			// 4) Attempt to call the "neverRegisteredTool" (which is annotated with @McpTool but never registered)
			val resp = client.sendRequest { reqId ->
				CallToolRequest(
					id = reqId,
					params = CallToolRequest.CallToolParams(
						name = "neverRegisteredTool",
						arguments = mapOf("value" to JsonPrimitive("Should fail")),
					),
				)
			}
			advanceUntilIdle()

			// 5) Confirm there's an error in the response
			val error = resp.error
			assertNotNull(error, "Expected an error because 'neverRegisteredTool' isn't registered.")
			assertEquals(error.code, JsonRpcErrorCodes.METHOD_NOT_FOUND)

			// 6) Check logs to ensure we see a server-side error
			val expected = buildLog {
				addClientOutgoing(
					"""{"method":"tools/call","jsonrpc":"2.0","id":"2","params":{"name":"neverRegisteredTool","arguments":{"value":"Should fail"}}}""",
				)
				addServerIncoming(
					"""{"method":"tools/call","jsonrpc":"2.0","id":"2","params":{"name":"neverRegisteredTool","arguments":{"value":"Should fail"}}}""",
				)
				addServerOutgoing(
					"""{"jsonrpc":"2.0","id":"2","error":{"code":${JsonRpcErrorCodes.METHOD_NOT_FOUND},"message":"Tool 'neverRegisteredTool' not registered on this server."}}""",
				)
				addClientIncoming(
					"""{"jsonrpc":"2.0","id":"2","error":{"code":${JsonRpcErrorCodes.METHOD_NOT_FOUND},"message":"Tool 'neverRegisteredTool' not registered on this server."}}""",
				)
			}

			// If your actual error JSON differs in code or message, update accordingly.
			assertLinesMatch(expected, log, "Check logs for unregistered tool call")
		}
}
