package sh.ondr.kmcp.schema.prompts

import kotlinx.serialization.Serializable
import sh.ondr.kmcp.schema.content.PromptContent

@Serializable
data class PromptMessage(
	val role: String,
	val content: PromptContent,
)
