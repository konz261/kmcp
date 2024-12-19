package sh.ondr.kmcp.schema.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
@SerialName("ping")
data class PingRequest(
	override val id: String,
	val params: PingParams? = null,
) : JsonRpcRequest() {
	@Serializable
	data class PingParams(
		val _meta: Map<String, JsonElement>? = null,
	)
}
