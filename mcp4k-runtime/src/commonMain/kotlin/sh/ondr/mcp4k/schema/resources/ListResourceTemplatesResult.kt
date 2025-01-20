package sh.ondr.mcp4k.schema.resources

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import sh.ondr.mcp4k.schema.core.PaginatedResult

@Serializable
data class ListResourceTemplatesResult(
	val resourceTemplates: List<ResourceTemplate>,
	override val _meta: Map<String, JsonElement>? = null,
	override val nextCursor: String? = null,
) : PaginatedResult
