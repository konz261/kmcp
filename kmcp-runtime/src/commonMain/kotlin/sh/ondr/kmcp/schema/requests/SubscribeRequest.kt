package sh.ondr.kmcp.schema.requests

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import sh.ondr.kmcp.schema.requests.params.SubscribeParams

@Serializable
@SerialName("resources/subscribe")
data class SubscribeRequest(
	override val id: String,
	override val method: String = "resources/subscribe",
	val params: SubscribeParams,
) : JsonRpcRequest()
