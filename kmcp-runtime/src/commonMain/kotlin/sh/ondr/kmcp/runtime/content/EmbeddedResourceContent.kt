package sh.ondr.kmcp.runtime.content

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import sh.ondr.kmcp.runtime.base.Annotations
import sh.ondr.kmcp.runtime.resources.ResourceContents

@Serializable
@SerialName("resource")
data class EmbeddedResourceContent(
	val resource: ResourceContents,
	val annotations: Annotations? = null,
) : PromptContent, ToolContent
