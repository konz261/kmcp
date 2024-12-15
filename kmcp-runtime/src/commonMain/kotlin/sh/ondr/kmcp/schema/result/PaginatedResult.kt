package sh.ondr.kmcp.schema.result

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * For paginated results, the schema defines `PaginatedResult` extending `Result`
 * with an optional `nextCursor` field.
 */
@Serializable
open class PaginatedResult(
	val nextCursor: String? = null,
	override val _meta: Map<String, JsonElement>? = null,
) : Result
