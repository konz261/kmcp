package sh.ondr.kmcp.schema.requests

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import sh.ondr.kmcp.schema.InitializeParams
import sh.ondr.kmcp.schema.JsonRpcRequest

@Serializable
@SerialName("initialize")
data class InitializeRequest(
	override val id: String,
	override val method: String = "initialize",
	val params: InitializeParams,
) : JsonRpcRequest()
