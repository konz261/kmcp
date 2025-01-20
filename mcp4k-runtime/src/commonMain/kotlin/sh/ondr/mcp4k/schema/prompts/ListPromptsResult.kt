package sh.ondr.mcp4k.schema.prompts

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import sh.ondr.mcp4k.schema.core.PaginatedResult

@Serializable
data class ListPromptsResult(
	val prompts: List<Prompt>,
	override val _meta: Map<String, JsonElement>? = null,
	override val nextCursor: String? = null,
) : PaginatedResult
