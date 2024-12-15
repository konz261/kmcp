package sh.ondr.kmcp.schema.requests.params

import kotlinx.serialization.Serializable

@Serializable
data class ListPromptsParams(
	val cursor: String? = null,
)
