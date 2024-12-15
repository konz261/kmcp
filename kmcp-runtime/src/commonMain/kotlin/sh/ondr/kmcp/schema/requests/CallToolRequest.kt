package sh.ondr.kmcp.schema.requests

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import sh.ondr.kmcp.schema.requests.params.CallToolParams

@Serializable
@SerialName("tools/call")
data class CallToolRequest(
	override val id: String,
	override val method: String = "tools/call",
	val params: CallToolParams,
) : JsonRpcRequest()
