package sh.ondr.kmcp.schema.requests

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import sh.ondr.kmcp.schema.GetPromptParams
import sh.ondr.kmcp.schema.JsonRpcRequest

@Serializable
@SerialName("prompts/get")
data class GetPromptRequest(
	override val id: String,
	override val method: String = "prompts/get",
	val params: GetPromptParams,
) : JsonRpcRequest()
