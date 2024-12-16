package sh.ondr.kmcp.schema.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("ping")
data class PingRequest(
	override val id: String,
	val params: PingParams? = null,
) : JsonRpcRequest() {
	@Serializable
	data class PingParams(
		val _meta: PingMeta? = null,
	) {
		@Serializable
		data class PingMeta(
			val progressToken: String? = null,
		)
	}
}
