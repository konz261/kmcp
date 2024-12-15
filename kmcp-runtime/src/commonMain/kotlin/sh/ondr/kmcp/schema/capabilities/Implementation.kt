package sh.ondr.kmcp.schema.capabilities

import kotlinx.serialization.Serializable

@Serializable
data class Implementation(
	val name: String,
	val version: String,
)
