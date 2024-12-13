package sh.ondr.kmcp.runtime.base

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class JsonRpcRequest(
	val id: String,
	val method: String,
	val params: JsonElement? = null,
) : JsonRpcMessage()
