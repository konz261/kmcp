package sh.ondr.kmcp.runtime.content

import kotlinx.serialization.Serializable
import sh.ondr.kmcp.runtime.base.Annotations
import sh.ondr.kmcp.runtime.resources.ResourceContents

@Serializable
data class EmbeddedResource(
	val type: String = "resource",
	val resource: ResourceContents,
	val annotations: Annotations? = null,
) : PromptContent, ToolContent
