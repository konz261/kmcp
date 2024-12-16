@file:OptIn(ExperimentalCoroutinesApi::class)

package sh.ondr.kmcp.runtime

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import sh.ondr.kmcp.runtime.transport.TestTransport
import kotlin.test.Test
import kotlin.test.assertEquals

class McpInitializationTest {
	@Test
	fun testInitHandshakeExact() =
		runTest {
			val log = mutableListOf<String>()

			val (clientTransport, serverTransport) = TestTransport.createClientAndServerTransport()
			val server = McpServer(serverTransport) { line -> log.add("SERVER $line") }
			server.start()

			val client = McpClient(clientTransport) { line -> log.add("CLIENT $line") }
			client.start()

			client.initialize()

			advanceUntilIdle()

			val expected =
				listOf(
					"""CLIENT OUTGOING: {"method":"initialize","jsonrpc":"2.0","id":"1","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"TestClient","version":"1.0.0"}}}""",
					"""SERVER INCOMING: {"method":"initialize","jsonrpc":"2.0","id":"1","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"TestClient","version":"1.0.0"}}}""",
					"""SERVER OUTGOING: {"jsonrpc":"2.0","id":"1","result":{"protocolVersion":"2024-11-05","capabilities":{},"serverInfo":{"name":"TestServer","version":"1.0.0"}}}""",
					"""CLIENT INCOMING: {"jsonrpc":"2.0","id":"1","result":{"protocolVersion":"2024-11-05","capabilities":{},"serverInfo":{"name":"TestServer","version":"1.0.0"}}}""",
					"""CLIENT OUTGOING: {"method":"notifications/initialized","jsonrpc":"2.0"}""",
					"""SERVER INCOMING: {"method":"notifications/initialized","jsonrpc":"2.0"}""",
				)

			delay(100)
			assertEquals(expected.size, log.size, "Number of log lines does not match")

			for (i in expected.indices) {
				assertEquals(
					expected[i],
					log[i],
					"Line $i does not match.\nExpected: ${expected[i]}\nActual:   ${log[i]}",
				)
			}
		}
}
