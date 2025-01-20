package sh.ondr.mcp4k.schema.capabilities

import kotlinx.serialization.Serializable

@Serializable
data class Implementation(
	val name: String,
	val version: String,
)
