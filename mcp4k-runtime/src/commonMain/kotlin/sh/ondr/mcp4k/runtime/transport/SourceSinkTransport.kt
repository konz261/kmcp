package sh.ondr.mcp4k.runtime.transport

import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.io.readLine
import kotlinx.io.writeString

class SourceSinkTransport(
	private val source: Source,
	private val sink: Sink,
	private val onClose: () -> Unit = {},
) : Transport {
	override suspend fun readString(): String? {
		return if (source.exhausted()) {
			// End-of-stream, no more lines
			null
		} else {
			// readLine() from kotlinx-io: reads until "\n" or EOF
			// If the source is closed mid-line, this may throw EOFException
			source.readLine()
		}
	}

	override suspend fun writeString(message: String) {
		// Write the message
		sink.writeString(message)
		sink.writeByte('\n'.code.toByte())
		sink.flush()
	}

	override suspend fun connect() {
		// No-op by default
	}

	override suspend fun close() {
		runCatching { onClose() }
	}
}
