package sh.ondr.kmcp.schema.requests.params

import kotlinx.serialization.Serializable

@Serializable
data class ListToolsParams(
	val cursor: String? = null,
)
