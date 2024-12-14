package sh.ondr.kmcp.schema.requests

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import sh.ondr.kmcp.schema.CreateMessageParams
import sh.ondr.kmcp.schema.JsonRpcRequest

@Serializable
@SerialName("sampling/createMessage")
data class CreateMessageRequest(
	override val id: String,
	override val method: String = "sampling/createMessage",
	val params: CreateMessageParams,
) : JsonRpcRequest()
