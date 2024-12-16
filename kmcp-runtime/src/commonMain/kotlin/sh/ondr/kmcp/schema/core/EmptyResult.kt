package sh.ondr.kmcp.schema.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class EmptyResult(
	override val _meta: Map<String, JsonElement>? = null,
) : Result
