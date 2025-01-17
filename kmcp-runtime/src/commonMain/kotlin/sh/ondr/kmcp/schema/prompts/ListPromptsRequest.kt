package sh.ondr.kmcp.schema.prompts

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import sh.ondr.kmcp.runtime.core.pagination.PaginatedEndpoint
import sh.ondr.kmcp.runtime.core.pagination.PaginatedRequestFactory
import sh.ondr.kmcp.schema.core.JsonRpcRequest

@Serializable
@SerialName("prompts/list")
data class ListPromptsRequest(
	override val id: String,
	val params: ListPromptsParams? = null,
) : JsonRpcRequest() {
	val cursor: String? get() = params?.cursor

	companion object : PaginatedEndpoint<ListPromptsRequest, ListPromptsResult, List<Prompt>> {
		override val requestFactory = PaginatedRequestFactory { id, cursor ->
			ListPromptsRequest(
				id = id,
				params = cursor?.let { ListPromptsParams(it) },
			)
		}

		override fun transform(result: ListPromptsResult): List<Prompt> {
			return result.prompts
		}
	}

	@Serializable
	data class ListPromptsParams(
		val cursor: String? = null,
		val _meta: Map<String, JsonElement>? = null,
	)
}
