package sh.ondr.kmcp.schema.resources

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import sh.ondr.kmcp.schema.core.JsonRpcRequest
import sh.ondr.kmcp.schema.core.Paginated

@Serializable
@SerialName("resources/templates/list")
data class ListResourceTemplatesRequest(
	override val id: String,
	val params: ListResourceTemplatesParams? = null,
) : JsonRpcRequest(), Paginated {
	override val cursor: String? get() = params?.cursor

	@Serializable
	data class ListResourceTemplatesParams(
		val cursor: String? = null,
	)
}
