package sh.ondr.kmcp.schema.requests

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import sh.ondr.kmcp.schema.JsonRpcRequest
import sh.ondr.kmcp.schema.requests.params.CreateMessageParams

@Serializable
@SerialName("sampling/createMessage")
data class CreateMessageRequest(
	override val id: String,
	override val method: String = "sampling/createMessage",
	val params: CreateMessageParams,
) : JsonRpcRequest()
