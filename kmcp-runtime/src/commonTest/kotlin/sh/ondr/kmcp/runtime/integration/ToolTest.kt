package sh.ondr.kmcp.runtime.integration

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import sh.ondr.kmcp.assertLinesMatch
import sh.ondr.kmcp.client
import sh.ondr.kmcp.logLines
import sh.ondr.kmcp.runtime.Client
import sh.ondr.kmcp.runtime.Server
import sh.ondr.kmcp.runtime.annotation.Tool
import sh.ondr.kmcp.runtime.core.toTextContent
import sh.ondr.kmcp.runtime.serialization.deserializeResult
import sh.ondr.kmcp.runtime.transport.TestTransport
import sh.ondr.kmcp.schema.content.TextContent
import sh.ondr.kmcp.schema.content.ToolContent
import sh.ondr.kmcp.schema.tools.CallToolRequest
import sh.ondr.kmcp.schema.tools.CallToolResult
import sh.ondr.kmcp.schema.tools.ListToolsRequest
import sh.ondr.kmcp.server
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@Tool
fun sendEmail(
	recipients: List<String>,
	title: String,
	body: String?,
) = "Sending email to $recipients with title $title and body $body".toTextContent()

/**
 * This function greets the user
 */
@Tool
suspend fun greet(
	name: String,
	age: Int = 25,
): ToolContent {
	return TextContent("Hello, $name, you are $age years old!")
}

class ToolsTest {
	@OptIn(ExperimentalCoroutinesApi::class)
	@Test
	fun testToolsList() =
		runTest {
			val testDispatcher = StandardTestDispatcher(testScheduler)
			val log = mutableListOf<String>()

			val (clientTransport, serverTransport) = TestTransport.createClientAndServerTransport()
			val server = Server.Builder()
				.withDispatcher(testDispatcher)
				.withTools(::greet, ::sendEmail)
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

			// Send the tools/list request
			val response = client.sendRequest { id -> ListToolsRequest(id = id) }
			advanceUntilIdle()

			// Construct the expected lines using the DSL
			val expected = logLines {
				clientOutgoing("""{"method":"tools/list","jsonrpc":"2.0","id":"2"}""")
				serverIncoming("""{"method":"tools/list","jsonrpc":"2.0","id":"2"}""")
				serverOutgoing(
					"""{"jsonrpc":"2.0","id":"2","result":{"tools":[{"name":"greet","description":"This function greets the user","inputSchema":{"type":"object","properties":{"name":{"type":"string"},"age":{"type":"number"}},"required":["name"]}},{"name":"sendEmail","inputSchema":{"type":"object","properties":{"recipients":{"type":"array","items":{"type":"string"}},"title":{"type":"string"},"body":{"type":"string"}},"required":["recipients","title"]}}]}}""",
				)
				clientIncoming(
					"""{"jsonrpc":"2.0","id":"2","result":{"tools":[{"name":"greet","description":"This function greets the user","inputSchema":{"type":"object","properties":{"name":{"type":"string"},"age":{"type":"number"}},"required":["name"]}},{"name":"sendEmail","inputSchema":{"type":"object","properties":{"recipients":{"type":"array","items":{"type":"string"}},"title":{"type":"string"},"body":{"type":"string"}},"required":["recipients","title"]}}]}}""",
				)
			}
			assertLinesMatch(expected, log, "tools list test")
		}

	@OptIn(ExperimentalCoroutinesApi::class)
	@Test
	fun testCallToolGreet() =
		runTest {
			val testDispatcher = StandardTestDispatcher(testScheduler)
			val log = mutableListOf<String>()

			val (clientTransport, serverTransport) = TestTransport.createClientAndServerTransport()
			val server = Server.Builder()
				.withDispatcher(testDispatcher)
				.withTool(::greet)
				.withTool(::sendEmail)
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

			// Now call the 'greet' tool
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

			val expected = logLines {
				clientOutgoing("""{"method":"tools/call","jsonrpc":"2.0","id":"2","params":{"name":"greet","arguments":{"name":"Alice"}}}""")
				serverIncoming("""{"method":"tools/call","jsonrpc":"2.0","id":"2","params":{"name":"greet","arguments":{"name":"Alice"}}}""")
				serverOutgoing(
					"""{"jsonrpc":"2.0","id":"2","result":{"content":[{"type":"text","text":"Hello, Alice, you are 25 years old!"}]}}""",
				)
				clientIncoming(
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
}
