package sh.ondr.mcp4k.runtime.transport

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ChannelTransportTest {
	@Test
	fun testCommunication() =
		runTest {
			val clientTransport = ChannelTransport()
			val serverTransport = clientTransport.flip()

			// Client writes a message
			clientTransport.writeString("Hello, Server!")

			// Server reads the message
			val msg = serverTransport.readString()
			assertEquals("Hello, Server!", msg)

			// Server responds
			serverTransport.writeString("Hello, Client!")
			val response = clientTransport.readString()
			assertEquals("Hello, Client!", response)

			// Close both
			clientTransport.close()
			serverTransport.close()
		}
}
