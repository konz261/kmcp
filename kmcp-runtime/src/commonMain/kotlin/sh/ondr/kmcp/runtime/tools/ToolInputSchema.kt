package sh.ondr.kmcp.runtime.tools

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class ToolInputSchema(
	val type: String = "object",
	val properties: Map<String, JsonObject>? = null,
	val required: List<String>? = null,
)
