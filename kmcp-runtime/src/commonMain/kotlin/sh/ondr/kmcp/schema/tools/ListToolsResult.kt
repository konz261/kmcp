package sh.ondr.kmcp.schema.tools

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import sh.ondr.kmcp.schema.core.PaginatedResult

@Serializable
data class ListToolsResult(
	val tools: List<Tool>,
	override val _meta: Map<String, JsonElement>? = null,
	override val nextCursor: String? = null,
) : PaginatedResult
