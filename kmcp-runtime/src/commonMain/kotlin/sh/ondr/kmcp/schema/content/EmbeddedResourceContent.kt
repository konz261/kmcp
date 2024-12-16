package sh.ondr.kmcp.schema.content

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import sh.ondr.kmcp.schema.core.Annotations
import sh.ondr.kmcp.schema.resources.ResourceContents

@Serializable
@SerialName("resource")
data class EmbeddedResourceContent(
	val resource: ResourceContents,
	val annotations: Annotations? = null,
) : PromptContent, ToolContent
