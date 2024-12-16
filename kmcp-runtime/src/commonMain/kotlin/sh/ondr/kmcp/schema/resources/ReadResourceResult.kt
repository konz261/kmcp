package sh.ondr.kmcp.schema.resources

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import sh.ondr.kmcp.schema.core.Result

@Serializable
data class ReadResourceResult(
	val contents: List<ResourceContents>,
	override val _meta: Map<String, JsonElement>? = null,
) : Result
