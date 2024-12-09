package sh.ondr.kmcp.runtime.content

import kotlinx.serialization.Serializable
import sh.ondr.kmcp.runtime.base.Annotations

@Serializable
data class Text(
	val type: String = "text",
	val text: String,
	val annotations: Annotations? = null,
) : PromptContent, ToolContent, SamplingContent
