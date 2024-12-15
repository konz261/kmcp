package sh.ondr.kmcp.schema.requests

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import sh.ondr.kmcp.schema.JsonRpcRequest
import sh.ondr.kmcp.schema.Paginated
import sh.ondr.kmcp.schema.requests.params.ListPromptsParams

@Serializable
@SerialName("prompts/list")
data class ListPromptsRequest(
	override val id: String,
	override val method: String = "prompts/list",
	val params: ListPromptsParams? = null,
) : JsonRpcRequest(), Paginated {
	override val cursor: String? get() = params?.cursor
}
