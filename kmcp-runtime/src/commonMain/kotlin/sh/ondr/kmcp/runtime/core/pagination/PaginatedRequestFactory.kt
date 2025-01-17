package sh.ondr.kmcp.runtime.core.pagination

import sh.ondr.kmcp.schema.core.JsonRpcRequest

/**
 * Used to build each "list" request in a paginated flow, injecting
 * the current `cursor` between pages.
 *
 * @param R The concrete request class (e.g. `ListPromptsRequest`) that implements [JsonRpcRequest].
 */
fun interface PaginatedRequestFactory<R : JsonRpcRequest> {
	/**
	 * Creates a new paginated JSON-RPC request of type [R] given a unique [id] and an optional [cursor] string.
	 */
	fun create(
		id: String,
		cursor: String?,
	): R
}
