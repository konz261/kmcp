package sh.ondr.kmcp.schema.requests.params

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class CallToolParams(
	val name: String,
	val arguments: Map<String, JsonElement>? = null,
)
