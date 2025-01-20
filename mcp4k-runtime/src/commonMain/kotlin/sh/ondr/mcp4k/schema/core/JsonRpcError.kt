package sh.ondr.mcp4k.schema.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class JsonRpcError(
	val code: Int,
	val message: String,
	val data: JsonElement? = null,
)
