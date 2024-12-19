package sh.ondr.kmcp.schema.tools

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import sh.ondr.kmcp.schema.core.JsonRpcRequest
import sh.ondr.kmcp.schema.core.Paginated

@Serializable
@SerialName("tools/list")
data class ListToolsRequest(
	override val id: String,
	val params: ListToolsParams? = null,
) : JsonRpcRequest(), Paginated {
	override val cursor: String? get() = params?.cursor

	@Serializable
	data class ListToolsParams(
		val cursor: String? = null,
		val _meta: Map<String, JsonElement>? = null,
	)
}
