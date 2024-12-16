package sh.ondr.kmcp.schema.capabilities

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import sh.ondr.kmcp.schema.core.JsonRpcRequest

@Serializable
@SerialName("initialize")
data class InitializeRequest(
	override val id: String,
	override val method: String = "initialize",
	val params: InitializeParams,
) : JsonRpcRequest() {
	@Serializable
	data class InitializeParams(
		val protocolVersion: String,
		val capabilities: ClientCapabilities,
		val clientInfo: Implementation,
	)
}
