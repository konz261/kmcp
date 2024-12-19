package sh.ondr.kmcp.schema.resources

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import sh.ondr.kmcp.schema.core.JsonRpcRequest

@Serializable
@SerialName("resources/subscribe")
data class SubscribeRequest(
	override val id: String,
	val params: SubscribeParams,
) : JsonRpcRequest() {
	@Serializable
	data class SubscribeParams(
		val uri: String,
		val _meta: Map<String, JsonElement>? = null,
	)
}
