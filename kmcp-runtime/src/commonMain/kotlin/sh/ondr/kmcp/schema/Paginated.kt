package sh.ondr.kmcp.schema

/**
 * An interface for paginated requests, which have an optional `cursor`.
 * This `cursor` is usually found in the params object.
 * It's a computed property and won't be serialized directly.
 */
interface Paginated {
	val cursor: String?
}
