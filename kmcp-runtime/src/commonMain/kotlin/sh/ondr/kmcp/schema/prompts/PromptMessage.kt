package sh.ondr.kmcp.schema.prompts

import kotlinx.serialization.Serializable
import sh.ondr.kmcp.schema.content.PromptContent
import sh.ondr.kmcp.schema.core.Role

@Serializable
data class PromptMessage(
	val role: Role,
	val content: PromptContent,
)
