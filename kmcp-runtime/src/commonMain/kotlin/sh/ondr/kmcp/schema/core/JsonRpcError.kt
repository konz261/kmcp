package sh.ondr.kmcp.schema.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class JsonRpcError(
	val code: Int,
	val message: String,
	val data: JsonElement? = null,
)
