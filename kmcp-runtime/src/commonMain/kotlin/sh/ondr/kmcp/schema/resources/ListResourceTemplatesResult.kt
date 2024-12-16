package sh.ondr.kmcp.schema.resources

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import sh.ondr.kmcp.schema.core.PaginatedResult

@Serializable
data class ListResourceTemplatesResult(
	val resourceTemplates: List<ResourceTemplate>,
	override val _meta: Map<String, JsonElement>? = null,
	override val nextCursor: String? = null,
) : PaginatedResult
