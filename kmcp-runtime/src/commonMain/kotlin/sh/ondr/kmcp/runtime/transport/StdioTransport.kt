package sh.ondr.kmcp.runtime.transport

// TODO create non-blocking version
class StdioTransport : Transport {
	// Blocks until a line is read or EOF is reached.
	override suspend fun readString(): String? = readlnOrNull()

	override suspend fun writeString(message: String) {
		println(message)
	}
}
