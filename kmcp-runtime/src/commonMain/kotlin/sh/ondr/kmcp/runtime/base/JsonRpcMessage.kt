package sh.ondr.kmcp.runtime.base

sealed interface JsonRpcMessage {
	val jsonrpc: String
}
