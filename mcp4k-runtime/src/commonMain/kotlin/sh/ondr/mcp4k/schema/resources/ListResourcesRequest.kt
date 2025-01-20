package sh.ondr.mcp4k.schema.resources

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import sh.ondr.mcp4k.runtime.core.pagination.PaginatedEndpoint
import sh.ondr.mcp4k.runtime.core.pagination.PaginatedRequestFactory
import sh.ondr.mcp4k.schema.core.JsonRpcRequest

@Serializable
@SerialName("resources/list")
data class ListResourcesRequest(
	override val id: String,
	val params: ListResourcesParams? = null,
) : JsonRpcRequest() {
	val cursor: String? get() = params?.cursor

	companion object : PaginatedEndpoint<ListResourcesRequest, ListResourcesResult, List<Resource>> {
		override val requestFactory = PaginatedRequestFactory { id, cursor ->
			ListResourcesRequest(
				id = id,
				params = cursor?.let { ListResourcesParams(it) },
			)
		}

		override fun transform(result: ListResourcesResult): List<Resource> {
			return result.resources
		}
	}

	@Serializable
	data class ListResourcesParams(
		val cursor: String? = null,
		val _meta: Map<String, JsonElement>? = null,
	)
}
