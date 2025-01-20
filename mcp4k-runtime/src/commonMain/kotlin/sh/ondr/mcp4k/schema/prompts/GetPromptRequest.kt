package sh.ondr.mcp4k.schema.prompts

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import sh.ondr.mcp4k.schema.core.JsonRpcRequest

@Serializable
@SerialName("prompts/get")
data class GetPromptRequest(
	override val id: String,
	val params: GetPromptParams,
) : JsonRpcRequest() {
	@Serializable
	data class GetPromptParams(
		val name: String,
		val arguments: Map<String, String>? = null,
		val _meta: Map<String, JsonElement>? = null,
	)
}
