package sh.ondr.kmcp.runtime.base

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class JsonRpcError(
	val code: Int,
	val message: String,
	val data: JsonElement? = null,
)
