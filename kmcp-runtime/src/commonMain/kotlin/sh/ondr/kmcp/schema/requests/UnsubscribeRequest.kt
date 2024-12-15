package sh.ondr.kmcp.schema.requests

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import sh.ondr.kmcp.schema.requests.params.UnsubscribeParams

@Serializable
@SerialName("resources/unsubscribe")
data class UnsubscribeRequest(
	override val id: String,
	override val method: String = "resources/unsubscribe",
	val params: UnsubscribeParams,
) : JsonRpcRequest()
