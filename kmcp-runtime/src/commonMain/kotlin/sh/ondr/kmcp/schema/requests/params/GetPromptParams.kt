package sh.ondr.kmcp.schema.requests.params

import kotlinx.serialization.Serializable

@Serializable
data class GetPromptParams(
	val name: String,
	val arguments: Map<String, String>? = null,
)
