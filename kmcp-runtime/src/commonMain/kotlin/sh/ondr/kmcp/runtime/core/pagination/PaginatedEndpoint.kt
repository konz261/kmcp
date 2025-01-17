package sh.ondr.kmcp.runtime.core.pagination

import sh.ondr.kmcp.schema.core.JsonRpcRequest
import sh.ondr.kmcp.schema.core.PaginatedResult

/**
 * A descriptor for a paginated RPC endpoint, tying together:
 *
 *  1. A paginated [REQ] request type (e.g. [sh.ondr.kmcp.schema.prompts.ListPromptsRequest]),
 *  2. A [RES] result type (e.g. [sh.ondr.kmcp.schema.prompts.ListPromptsResult]),
 *  3. A [requestFactory] function to build each request page by page,
 *  4. A [transform] function converting the returned [RES] into higher-level [ITEMS].
 *
 * To see an example implementation of this interface, see [sh.ondr.kmcp.schema.prompts.ListPromptsRequest.Companion].
 *
 * @param REQ The concrete request class that implements [JsonRpcRequest].
 * @param RES The paginated result class that implements [PaginatedResult].
 * @param ITEMS The type of items your application wants to consume or emit for each page.
 */
interface PaginatedEndpoint<REQ : JsonRpcRequest, RES : PaginatedResult, ITEMS> {
	val requestFactory: PaginatedRequestFactory<REQ>

	/**
	 * Takes the [RES] object (the server's result) and
	 * transforms it into the items to be emitted, typically a List.
	 */
	fun transform(result: RES): ITEMS
}
