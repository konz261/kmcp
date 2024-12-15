package sh.ondr.kmcp.schema.capabilities

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ClientCapabilities(
	val experimental: Map<String, JsonElement>? = null,
	val roots: RootsCapability? = null,
	val sampling: Map<String, JsonElement>? = null,
)
