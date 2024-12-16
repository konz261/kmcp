package sh.ondr.kmcp.schema.prompts

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import sh.ondr.kmcp.schema.core.JsonRpcRequest

@Serializable
@SerialName("prompts/get")
data class GetPromptRequest(
	override val id: String,
	override val method: String = "prompts/get",
	val params: GetPromptParams,
) : JsonRpcRequest() {
	@Serializable
	data class GetPromptParams(
		val name: String,
		val arguments: Map<String, String>? = null,
	)
}
