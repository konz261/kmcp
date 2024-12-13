package sh.ondr.kmcp.runtime.content

import kotlinx.serialization.Serializable
import sh.ondr.kmcp.runtime.base.Annotations

@Serializable
data class ImageContent(
	val data: String,
	val mimeType: String,
	val annotations: Annotations? = null,
) : PromptContent, ToolContent, SamplingContent {
	override val type: String = "image"
}
