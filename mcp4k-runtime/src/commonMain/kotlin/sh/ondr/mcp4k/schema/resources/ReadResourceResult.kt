package sh.ondr.mcp4k.schema.resources

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import sh.ondr.mcp4k.schema.core.Result

@Serializable
data class ReadResourceResult(
	val contents: List<ResourceContents>,
	override val _meta: Map<String, JsonElement>? = null,
) : Result
