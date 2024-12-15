package sh.ondr.kmcp.schema.resources

import kotlinx.serialization.Serializable
import sh.ondr.kmcp.schema.annotations.Annotations

@Serializable
data class ResourceTemplate(
	val uriTemplate: String,
	val name: String,
	val description: String? = null,
	val mimeType: String? = null,
	val annotations: Annotations? = null,
)
