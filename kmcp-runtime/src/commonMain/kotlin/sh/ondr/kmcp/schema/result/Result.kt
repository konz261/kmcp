package sh.ondr.kmcp.schema.result

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Base interface for all result types from MCP responses.
 * The schema states `Result` can have an optional `_meta` field.
 * We'll model it as an optional Map.
 */
@Serializable
abstract class Result(
	val _meta: Map<String, JsonElement>? = null,
)
