package sh.ondr.kmcp.schema.prompts

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import sh.ondr.kmcp.schema.core.JsonRpcRequest
import sh.ondr.kmcp.schema.core.Paginated

@Serializable
@SerialName("prompts/list")
data class ListPromptsRequest(
	override val id: String,
	val params: ListPromptsParams? = null,
) : JsonRpcRequest(), Paginated {
	override val cursor: String? get() = params?.cursor

	@Serializable
	data class ListPromptsParams(
		val cursor: String? = null,
	)
}
