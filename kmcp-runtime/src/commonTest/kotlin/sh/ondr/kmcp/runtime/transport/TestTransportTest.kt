package sh.ondr.kmcp.runtime.transport

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class TestTransportTest {
	@Test
	fun testCommunication() =
		runTest {
			val (client, server) = TestTransport.createClientAndServerTransport()

			// Client writes a message
			client.writeString("Hello, Server!")

			// Server reads the message
			val msg = server.readString()
			assertEquals("Hello, Server!", msg)

			// Server responds
			server.writeString("Hello, Client!")
			val response = client.readString()
			assertEquals("Hello, Client!", response)

			// Close both
			client.close()
			server.close()
		}
}
