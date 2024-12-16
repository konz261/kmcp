package sh.ondr.kmcp.schema.resources

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import sh.ondr.kmcp.schema.core.PaginatedResult

@Serializable
data class ListResourcesResult(
	val resources: List<Resource>,
	override val _meta: Map<String, JsonElement>? = null,
	override val nextCursor: String? = null,
) : PaginatedResult
