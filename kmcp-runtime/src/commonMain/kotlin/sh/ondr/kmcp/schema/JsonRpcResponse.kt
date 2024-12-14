package sh.ondr.kmcp.schema

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class JsonRpcResponse(
	val id: String,
	val result: JsonElement? = null,
	val error: JsonRpcError? = null,
) : JsonRpcMessage() {
	init {
		require((result == null) xor (error == null)) {
			"A JSON-RPC response must have either 'result' or 'error', but not both."
		}
	}
}
