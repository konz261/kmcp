package sh.ondr.kmcp.runtime.content

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import sh.ondr.kmcp.runtime.base.Annotations

@Serializable
@SerialName("text")
data class TextContent(
	val text: String,
	val annotations: Annotations? = null,
) : PromptContent, ToolContent, SamplingContent
