package sh.ondr.mcp4k.schema.tools

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import sh.ondr.mcp4k.schema.core.JsonRpcRequest

@Serializable
@SerialName("tools/call")
data class CallToolRequest(
	override val id: String,
	val params: CallToolParams,
) : JsonRpcRequest() {
	@Serializable
	data class CallToolParams(
		val name: String,
		val arguments: Map<String, JsonElement>? = null,
		val _meta: Map<String, JsonElement>? = null,
	)
}
