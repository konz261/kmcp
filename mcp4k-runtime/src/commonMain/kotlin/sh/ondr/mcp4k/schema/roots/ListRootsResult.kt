package sh.ondr.mcp4k.schema.roots

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import sh.ondr.mcp4k.schema.core.Result

@Serializable
data class ListRootsResult(
	val roots: List<Root>,
	override val _meta: Map<String, JsonElement>? = null,
) : Result
