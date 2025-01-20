package sh.ondr.mcp4k.schema.resources

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import sh.ondr.mcp4k.runtime.core.pagination.PaginatedEndpoint
import sh.ondr.mcp4k.runtime.core.pagination.PaginatedRequestFactory
import sh.ondr.mcp4k.schema.core.JsonRpcRequest

@Serializable
@SerialName("resources/templates/list")
data class ListResourceTemplatesRequest(
	override val id: String,
	val params: ListResourceTemplatesParams? = null,
) : JsonRpcRequest() {
	val cursor: String? get() = params?.cursor

	companion object : PaginatedEndpoint<ListResourceTemplatesRequest, ListResourceTemplatesResult, List<ResourceTemplate>> {
		override val requestFactory = PaginatedRequestFactory { id, cursor ->
			ListResourceTemplatesRequest(
				id = id,
				params = cursor?.let { ListResourceTemplatesParams(it) },
			)
		}

		override fun transform(result: ListResourceTemplatesResult): List<ResourceTemplate> {
			return result.resourceTemplates
		}
	}

	@Serializable
	data class ListResourceTemplatesParams(
		val cursor: String? = null,
		val _meta: Map<String, JsonElement>? = null,
	)
}
