package sh.ondr.mcp4k.schema.content

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import sh.ondr.mcp4k.schema.core.Annotations
import sh.ondr.mcp4k.schema.resources.ResourceContents

@Serializable
@SerialName("resource")
data class EmbeddedResourceContent(
	val resource: ResourceContents,
	val annotations: Annotations? = null,
) : PromptContent, ToolContent
