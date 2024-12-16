package sh.ondr.kmcp.runtime.transport

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED

class TestTransport(
	private val incoming: Channel<String>,
	private val outgoing: Channel<String>,
) : Transport {
	override suspend fun readString(): String? {
		return incoming.receiveCatching().getOrNull()
	}

	override suspend fun writeString(message: String) {
		outgoing.send(message)
	}

	override suspend fun close() {
		// Closing channels signals no more messages
		incoming.close()
		outgoing.close()
	}

	companion object {
		/**
		 * Creates a pair of linked TestTransports. The first transport's output goes
		 * to the second transport's input and vice versa.
		 *
		 * Useful for simulating client-server tests:
		 *
		 * val (clientTransport, serverTransport) = TestTransport.createLinkedPair()
		 */
		fun createClientAndServerTransport(): Pair<TestTransport, TestTransport> {
			val c1 = Channel<String>(UNLIMITED)
			val c2 = Channel<String>(UNLIMITED)
			val t1 = TestTransport(incoming = c1, outgoing = c2)
			val t2 = TestTransport(incoming = c2, outgoing = c1)
			return t1 to t2
		}
	}
}
