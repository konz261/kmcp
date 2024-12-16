package sh.ondr.kmcp.schema.roots

import kotlinx.serialization.Serializable

@Serializable
data class Root(
	val uri: String,
	val name: String? = null,
)
