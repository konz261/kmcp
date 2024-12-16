package sh.ondr.kmcp.schema.resources

import kotlinx.serialization.Serializable

@Serializable
data class Resource(
	val uri: String,
	val name: String,
	val description: String? = null,
	val mimeType: String? = null,
)
