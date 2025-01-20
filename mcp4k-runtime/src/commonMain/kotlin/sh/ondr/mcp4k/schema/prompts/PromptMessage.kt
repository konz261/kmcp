package sh.ondr.mcp4k.schema.prompts

import kotlinx.serialization.Serializable
import sh.ondr.mcp4k.schema.content.PromptContent
import sh.ondr.mcp4k.schema.core.Role

@Serializable
data class PromptMessage(
	val role: Role,
	val content: PromptContent,
)
