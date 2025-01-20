package sh.ondr.mcp4k.schema.resources

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import sh.ondr.mcp4k.schema.core.JsonRpcRequest

@Serializable
@SerialName("resources/read")
data class ReadResourceRequest(
	override val id: String,
	val params: ReadResourceParams,
) : JsonRpcRequest() {
	@Serializable
	data class ReadResourceParams(
		val uri: String,
		val _meta: Map<String, JsonElement>? = null,
	)
}
