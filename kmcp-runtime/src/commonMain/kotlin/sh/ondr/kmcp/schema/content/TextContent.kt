package sh.ondr.kmcp.schema.content

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import sh.ondr.kmcp.schema.core.Annotations

@Serializable
@SerialName("text")
data class TextContent(
	val text: String,
	val annotations: Annotations? = null,
) : PromptContent, ToolContent, SamplingContent
