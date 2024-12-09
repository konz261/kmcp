package sh.ondr.kmcp.runtime.content

import kotlinx.serialization.Serializable
import sh.ondr.kmcp.runtime.base.Annotations

@Serializable
data class Image(
	val type: String = "image",
	val data: String,
	val mimeType: String,
	val annotations: Annotations? = null,
) : PromptContent, ToolContent, SamplingContent
