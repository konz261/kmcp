package sh.ondr.kmcp.schema.resources

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import sh.ondr.kmcp.schema.core.JsonRpcRequest

@Serializable
@SerialName("resources/subscribe")
data class SubscribeRequest(
	override val id: String,
	override val method: String = "resources/subscribe",
	val params: SubscribeParams,
) : JsonRpcRequest() {
	@Serializable
	data class SubscribeParams(
		val uri: String,
	)
}
