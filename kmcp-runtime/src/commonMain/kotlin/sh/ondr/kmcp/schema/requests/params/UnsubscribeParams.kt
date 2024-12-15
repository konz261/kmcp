package sh.ondr.kmcp.schema.requests.params

import kotlinx.serialization.Serializable

@Serializable
data class UnsubscribeParams(
	val uri: String,
)
