package sh.ondr.mcp4k.schema.prompts

import kotlinx.serialization.Serializable

/**
 * Prompt related structures
 */
@Serializable
data class PromptArgument(
	val name: String,
	val description: String? = null,
	val required: Boolean = false,
)
