package sh.ondr.mcp4k.schema.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class EmptyResult(
	override val _meta: Map<String, JsonElement>? = null,
) : Result
