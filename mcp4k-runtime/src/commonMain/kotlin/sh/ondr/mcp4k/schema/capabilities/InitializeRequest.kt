package sh.ondr.mcp4k.schema.capabilities

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import sh.ondr.mcp4k.schema.core.JsonRpcRequest

@Serializable
@SerialName("initialize")
data class InitializeRequest(
	override val id: String,
	val params: InitializeParams,
) : JsonRpcRequest() {
	@Serializable
	data class InitializeParams(
		val protocolVersion: String,
		val capabilities: ClientCapabilities,
		val clientInfo: Implementation,
		val _meta: Map<String, JsonElement>? = null,
	)
}
