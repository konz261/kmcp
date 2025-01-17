package sh.ondr.kmcp.schema.tools

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import sh.ondr.kmcp.runtime.core.pagination.PaginatedEndpoint
import sh.ondr.kmcp.runtime.core.pagination.PaginatedRequestFactory
import sh.ondr.kmcp.schema.core.JsonRpcRequest

@Serializable
@SerialName("tools/list")
data class ListToolsRequest(
	override val id: String,
	val params: ListToolsParams? = null,
) : JsonRpcRequest() {
	val cursor: String? get() = params?.cursor

	companion object : PaginatedEndpoint<ListToolsRequest, ListToolsResult, List<Tool>> {
		override val requestFactory = PaginatedRequestFactory { id, cursor ->
			ListToolsRequest(
				id = id,
				params = cursor?.let { ListToolsParams(it) },
			)
		}

		override fun transform(result: ListToolsResult): List<Tool> {
			return result.tools
		}
	}

	@Serializable
	data class ListToolsParams(
		val cursor: String? = null,
		val _meta: Map<String, JsonElement>? = null,
	)
}
