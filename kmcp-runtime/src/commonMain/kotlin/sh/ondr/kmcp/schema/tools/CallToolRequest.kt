package sh.ondr.kmcp.schema.tools

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import sh.ondr.kmcp.schema.core.JsonRpcRequest

@Serializable
@SerialName("tools/call")
data class CallToolRequest(
	override val id: String,
	override val method: String = "tools/call",
	val params: CallToolParams,
) : JsonRpcRequest() {
	@Serializable
	data class CallToolParams(
		val name: String,
		val arguments: Map<String, JsonElement>? = null,
	)
}
