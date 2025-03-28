package sh.ondr.mcp4k.schema.content

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import sh.ondr.mcp4k.schema.core.Annotations

@Serializable
@SerialName("image")
data class ImageContent(
	val data: String,
	val mimeType: String,
	val annotations: Annotations? = null,
) : PromptContent, ToolContent, SamplingContent
