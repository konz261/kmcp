package sh.ondr.kmcp.schema.requests

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import sh.ondr.kmcp.schema.JsonRpcRequest
import sh.ondr.kmcp.schema.PingParams

@Serializable
@SerialName("ping")
data class PingRequest(
	override val id: String,
	override val method: String = "ping",
	val params: PingParams? = null,
) : JsonRpcRequest()
