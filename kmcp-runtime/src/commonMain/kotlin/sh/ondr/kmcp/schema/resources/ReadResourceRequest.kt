package sh.ondr.kmcp.schema.resources

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import sh.ondr.kmcp.schema.core.JsonRpcRequest

@Serializable
@SerialName("resources/read")
data class ReadResourceRequest(
	override val id: String,
	val params: ReadResourceParams,
) : JsonRpcRequest() {
	@Serializable
	data class ReadResourceParams(
		val uri: String,
	)
}
