package sh.ondr.kmcp.runtime.content

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import sh.ondr.kmcp.runtime.base.Annotations

@Serializable
@SerialName("image")
data class ImageContent(
	val data: String,
	val mimeType: String,
	val annotations: Annotations? = null,
) : PromptContent, ToolContent, SamplingContent
