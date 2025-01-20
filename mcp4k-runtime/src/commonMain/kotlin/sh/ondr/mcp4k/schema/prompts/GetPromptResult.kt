package sh.ondr.mcp4k.schema.prompts

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import sh.ondr.mcp4k.schema.core.Result

@Serializable
data class GetPromptResult(
	val description: String? = null,
	val messages: List<PromptMessage>,
	override val _meta: Map<String, JsonElement>? = null,
) : Result
