package sh.ondr.kmcp.schema.requests

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import sh.ondr.kmcp.schema.CallToolParams
import sh.ondr.kmcp.schema.JsonRpcRequest

@Serializable
@SerialName("tools/call")
data class CallToolRequest(
	override val id: String,
	override val method: String = "tools/call",
	val params: CallToolParams,
) : JsonRpcRequest()
