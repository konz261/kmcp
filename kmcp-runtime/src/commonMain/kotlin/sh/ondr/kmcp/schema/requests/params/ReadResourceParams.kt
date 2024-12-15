package sh.ondr.kmcp.schema.requests.params

import kotlinx.serialization.Serializable

@Serializable
data class ReadResourceParams(
	val uri: String,
)
