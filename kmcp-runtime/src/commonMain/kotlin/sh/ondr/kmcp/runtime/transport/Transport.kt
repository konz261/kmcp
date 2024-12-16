package sh.ondr.kmcp.runtime.transport

sealed interface Transport {
	suspend fun connect() {} // no-op by default

	suspend fun close() {} // no-op by default

	suspend fun readString(): String?

	suspend fun writeString(message: String)
}
