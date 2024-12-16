package sh.ondr.kmcp.schema.content

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import sh.ondr.kmcp.schema.core.Annotations

@Serializable
@SerialName("image")
data class ImageContent(
	val data: String,
	val mimeType: String,
	val annotations: Annotations? = null,
) : PromptContent, ToolContent, SamplingContent
