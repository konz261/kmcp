package sh.ondr.kmcp.schema.requests

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import sh.ondr.kmcp.schema.JsonRpcRequest
import sh.ondr.kmcp.schema.ListPromptsParams
import sh.ondr.kmcp.schema.Paginated

@Serializable
@SerialName("prompts/list")
data class ListPromptsRequest(
	override val id: String,
	override val method: String = "prompts/list",
	val params: ListPromptsParams? = null,
) : JsonRpcRequest(), Paginated {
	override val cursor: String? get() = params?.cursor
}
