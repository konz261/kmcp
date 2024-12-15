package sh.ondr.kmcp.schema.requests

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import sh.ondr.kmcp.schema.JsonRpcRequest
import sh.ondr.kmcp.schema.Paginated
import sh.ondr.kmcp.schema.requests.params.ListToolsParams

@Serializable
@SerialName("tools/list")
data class ListToolsRequest(
	override val id: String,
	override val method: String = "tools/list",
	val params: ListToolsParams? = null,
) : JsonRpcRequest(), Paginated {
	override val cursor: String? get() = params?.cursor
}
