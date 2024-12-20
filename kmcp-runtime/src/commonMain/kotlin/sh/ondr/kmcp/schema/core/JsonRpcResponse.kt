package sh.ondr.kmcp.schema.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import sh.ondr.kmcp.runtime.core.JSON_RPC_VERSION

@Serializable
data class JsonRpcResponse(
	val jsonrpc: String = JSON_RPC_VERSION,
	val id: String,
	val result: JsonElement? = null,
	val error: JsonRpcError? = null,
) : JsonRpcMessage {
	init {
		require((result == null) xor (error == null)) {
			"A JSON-RPC response must have either 'result' or 'error', but not both."
		}
	}
}
