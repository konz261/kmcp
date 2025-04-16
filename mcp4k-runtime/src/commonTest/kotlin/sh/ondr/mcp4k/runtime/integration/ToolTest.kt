package sh.ondr.mcp4k.runtime.integration

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import sh.ondr.koja.JsonSchema
import sh.ondr.mcp4k.assertLinesMatch
import sh.ondr.mcp4k.buildLog
import sh.ondr.mcp4k.clientIncoming
import sh.ondr.mcp4k.clientOutgoing
import sh.ondr.mcp4k.runtime.Client
import sh.ondr.mcp4k.runtime.Server
import sh.ondr.mcp4k.runtime.annotation.McpTool
import sh.ondr.mcp4k.runtime.core.toTextContent
import sh.ondr.mcp4k.runtime.serialization.deserializeResult
import sh.ondr.mcp4k.runtime.transport.ChannelTransport
import sh.ondr.mcp4k.schema.content.TextContent
import sh.ondr.mcp4k.schema.content.ToolContent
import sh.ondr.mcp4k.schema.core.JsonRpcErrorCodes
import sh.ondr.mcp4k.schema.tools.CallToolRequest
import sh.ondr.mcp4k.schema.tools.CallToolResult
import sh.ondr.mcp4k.schema.tools.ListToolsRequest
import sh.ondr.mcp4k.schema.tools.Tool
import sh.ondr.mcp4k.serverIncoming
import sh.ondr.mcp4k.serverOutgoing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * @param title The email's title
 * @param body The email's body
 */
@Serializable @JsonSchema
data class Email(
	val title: String,
	val body: String?,
)

/**
 * Sends an email to [recipients] with the given [email]
 */
@McpTool
fun sendEmail(
	recipients: List<String>,
	email: Email,
) = "Sending email to $recipients with title '${email.title}' and body '${email.body}'".toTextContent()

/**
 * Greets a user by [name], optionally specifying an [age].
 */
@McpTool
suspend fun Server.greet(
	name: String,
	age: Int = 25,
): ToolContent {
	return TextContent("Hello, $name, you are $age years old!")
}

/**
 * Reverses a given string [s].
 */
@McpTool
fun reverseString(s: String) = "Reversed: ${s.reversed()}".toTextContent()

@McpTool
suspend fun noParamTool(): ToolContent {
	return TextContent("Hi!")
}

@McpTool
fun neverRegisteredTool(value: String) = "Should never be called with param: $value".toTextContent()

class ToolsTest {
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
					"""{"jsonrpc":"2.0","id":"2","result":{"tools":[{"name":"greet","description":"Greets a user by [name], optionally specifying an [age].","inputSchema":{"type":"object","properties":{"name":{"type":"string"},"age":{"type":"number"}},"required":["name"]}},{"name":"sendEmail","description":"Sends an email to [recipients] with the given [email]","inputSchema":{"type":"object","properties":{"recipients":{"type":"array","items":{"type":"string"}},"email":{"type":"object","description":"null","properties":{"title":{"type":"string","description":"The email's title"},"body":{"type":"string","description":"The email's body"}},"required":["title"]}},"required":["recipients","email"]}}],"nextCursor":"eyJwYWdlIjoxLCJwYWdlU2l6ZSI6Mn0="}}""",
				)
				addClientIncoming(
					"""{"jsonrpc":"2.0","id":"2","result":{"tools":[{"name":"greet","description":"Greets a user by [name], optionally specifying an [age].","inputSchema":{"type":"object","properties":{"name":{"type":"string"},"age":{"type":"number"}},"required":["name"]}},{"name":"sendEmail","description":"Sends an email to [recipients] with the given [email]","inputSchema":{"type":"object","properties":{"recipients":{"type":"array","items":{"type":"string"}},"email":{"type":"object","description":"null","properties":{"title":{"type":"string","description":"The email's title"},"body":{"type":"string","description":"The email's body"}},"required":["title"]}},"required":["recipients","email"]}}],"nextCursor":"eyJwYWdlIjoxLCJwYWdlU2l6ZSI6Mn0="}}""",
				)

				// 2nd page
				addClientOutgoing("""{"method":"tools/list","jsonrpc":"2.0","id":"3","params":{"cursor":"eyJwYWdlIjoxLCJwYWdlU2l6ZSI6Mn0="}}""")
				addServerIncoming("""{"method":"tools/list","jsonrpc":"2.0","id":"3","params":{"cursor":"eyJwYWdlIjoxLCJwYWdlU2l6ZSI6Mn0="}}""")
				addServerOutgoing(
					"""{"jsonrpc":"2.0","id":"3","result":{"tools":[{"name":"reverseString","description":"Reverses a given string [s].","inputSchema":{"type":"object","properties":{"s":{"type":"string"}},"required":["s"]}},{"name":"noParamTool","inputSchema":{"type":"object","properties":{}}}]}}""",
				)
				addClientIncoming(
					"""{"jsonrpc":"2.0","id":"3","result":{"tools":[{"name":"reverseString","description":"Reverses a given string [s].","inputSchema":{"type":"object","properties":{"s":{"type":"string"}},"required":["s"]}},{"name":"noParamTool","inputSchema":{"type":"object","properties":{}}}]}}""",
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
					"""{"jsonrpc":"2.0","id":"2","result":{"tools":[{"name":"greet","description":"Greets a user by [name], optionally specifying an [age].","inputSchema":{"type":"object","properties":{"name":{"type":"string"},"age":{"type":"number"}},"required":["name"]}},{"name":"sendEmail","description":"Sends an email to [recipients] with the given [email]","inputSchema":{"type":"object","properties":{"recipients":{"type":"array","items":{"type":"string"}},"email":{"type":"object","description":"null","properties":{"title":{"type":"string","description":"The email's title"},"body":{"type":"string","description":"The email's body"}},"required":["title"]}},"required":["recipients","email"]}}],"nextCursor":"eyJwYWdlIjoxLCJwYWdlU2l6ZSI6Mn0="}}""",
				)
				addClientIncoming(
					"""{"jsonrpc":"2.0","id":"2","result":{"tools":[{"name":"greet","description":"Greets a user by [name], optionally specifying an [age].","inputSchema":{"type":"object","properties":{"name":{"type":"string"},"age":{"type":"number"}},"required":["name"]}},{"name":"sendEmail","description":"Sends an email to [recipients] with the given [email]","inputSchema":{"type":"object","properties":{"recipients":{"type":"array","items":{"type":"string"}},"email":{"type":"object","description":"null","properties":{"title":{"type":"string","description":"The email's title"},"body":{"type":"string","description":"The email's body"}},"required":["title"]}},"required":["recipients","email"]}}],"nextCursor":"eyJwYWdlIjoxLCJwYWdlU2l6ZSI6Mn0="}}""",
				)

				// 2nd page
				addClientOutgoing("""{"method":"tools/list","jsonrpc":"2.0","id":"3","params":{"cursor":"eyJwYWdlIjoxLCJwYWdlU2l6ZSI6Mn0="}}""")
				addServerIncoming("""{"method":"tools/list","jsonrpc":"2.0","id":"3","params":{"cursor":"eyJwYWdlIjoxLCJwYWdlU2l6ZSI6Mn0="}}""")
				addServerOutgoing(
					"""{"jsonrpc":"2.0","id":"3","result":{"tools":[{"name":"reverseString","description":"Reverses a given string [s].","inputSchema":{"type":"object","properties":{"s":{"type":"string"}},"required":["s"]}},{"name":"noParamTool","inputSchema":{"type":"object","properties":{}}}]}}""",
				)
				addClientIncoming(
					"""{"jsonrpc":"2.0","id":"3","result":{"tools":[{"name":"reverseString","description":"Reverses a given string [s].","inputSchema":{"type":"object","properties":{"s":{"type":"string"}},"required":["s"]}},{"name":"noParamTool","inputSchema":{"type":"object","properties":{}}}]}}""",
				)
			}
			assertLinesMatch(expected, log, "tools list (getAllTools) test")
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
			val response = client.sendRequest { id ->
				CallToolRequest(
					id = id,
					params = CallToolRequest.CallToolParams(
						name = "greet",
						arguments = mapOf("name" to JsonPrimitive("Alice")),
					),
				)
			}
			advanceUntilIdle()

			val expected = buildLog {
				addClientOutgoing("""{"method":"tools/call","jsonrpc":"2.0","id":"2","params":{"name":"greet","arguments":{"name":"Alice"}}}""")
				addServerIncoming("""{"method":"tools/call","jsonrpc":"2.0","id":"2","params":{"name":"greet","arguments":{"name":"Alice"}}}""")
				addServerOutgoing(
					"""{"jsonrpc":"2.0","id":"2","result":{"content":[{"type":"text","text":"Hello, Alice, you are 25 years old!"}]}}""",
				)
				addClientIncoming(
					"""{"jsonrpc":"2.0","id":"2","result":{"content":[{"type":"text","text":"Hello, Alice, you are 25 years old!"}]}}""",
				)
			}
			assertLinesMatch(expected, log, "tools call greet test")

			// Optionally, verify the actual response
			val callToolResult = response.result?.deserializeResult<CallToolResult>()
			assertNotNull(callToolResult)
			assertEquals(1, callToolResult.content.size)
			val text = (callToolResult.content.first() as? TextContent)?.text
			assertEquals("Hello, Alice, you are 25 years old!", text)
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
