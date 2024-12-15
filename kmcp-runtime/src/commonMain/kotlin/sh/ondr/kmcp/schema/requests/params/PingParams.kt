package sh.ondr.kmcp.schema.requests.params

import kotlinx.serialization.Serializable

@Serializable
data class PingParams(
	val _meta: PingMeta? = null,
) {
	@Serializable
	data class PingMeta(
		val progressToken: String? = null,
	)
}
