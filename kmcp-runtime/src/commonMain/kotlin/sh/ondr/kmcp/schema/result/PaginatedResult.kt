package sh.ondr.kmcp.schema.result

import kotlinx.serialization.Serializable

/**
 * For paginated results, the schema defines `PaginatedResult` extending `Result`
 * with an optional `nextCursor` field.
 */
@Serializable
open class PaginatedResult(
	val nextCursor: String? = null,
) : Result()
