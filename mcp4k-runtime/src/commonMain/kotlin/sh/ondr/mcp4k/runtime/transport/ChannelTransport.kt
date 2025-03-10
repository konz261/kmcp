package sh.ondr.mcp4k.runtime.transport

import kotlinx.coroutines.channels.Channel

class ChannelTransport(
	private val incoming: Channel<String> = Channel(Channel.UNLIMITED),
	private val outgoing: Channel<String> = Channel(Channel.UNLIMITED),
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

	fun flip() =
		ChannelTransport(
			incoming = outgoing,
			outgoing = incoming,
		)
}
