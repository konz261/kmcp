package sh.ondr.mcp4k.schema.resources

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import sh.ondr.mcp4k.schema.core.JsonRpcRequest

@Serializable
@SerialName("resources/unsubscribe")
data class UnsubscribeRequest(
	override val id: String,
	val params: UnsubscribeParams,
) : JsonRpcRequest() {
	@Serializable
	data class UnsubscribeParams(
		val uri: String,
		val _meta: Map<String, JsonElement>? = null,
	)
}
