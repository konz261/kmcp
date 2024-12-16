package sh.ondr.kmcp.schema.core

/**
 * For paginated results, the schema defines `PaginatedResult` extending `Result`
 * with an optional `nextCursor` field.
 */
interface PaginatedResult : Result {
	val nextCursor: String?
}
