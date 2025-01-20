package sh.ondr.mcp4k.schema.roots

import kotlinx.serialization.Serializable

@Serializable
data class Root(
	val uri: String,
	val name: String? = null,
)
