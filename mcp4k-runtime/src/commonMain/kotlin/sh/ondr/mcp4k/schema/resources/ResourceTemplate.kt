package sh.ondr.mcp4k.schema.resources

import kotlinx.serialization.Serializable
import sh.ondr.mcp4k.schema.core.Annotations

@Serializable
data class ResourceTemplate(
	val uriTemplate: String,
	val name: String,
	val description: String? = null,
	val mimeType: String? = null,
	val annotations: Annotations? = null,
)
