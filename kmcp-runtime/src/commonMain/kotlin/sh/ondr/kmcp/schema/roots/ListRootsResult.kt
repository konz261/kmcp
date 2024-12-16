package sh.ondr.kmcp.schema.roots

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import sh.ondr.kmcp.schema.core.Result

@Serializable
data class ListRootsResult(
	val roots: List<Root>,
	override val _meta: Map<String, JsonElement>? = null,
) : Result
