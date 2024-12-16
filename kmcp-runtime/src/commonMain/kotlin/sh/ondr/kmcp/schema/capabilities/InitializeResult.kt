package sh.ondr.kmcp.schema.capabilities

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import sh.ondr.kmcp.schema.core.Result

@Serializable
data class InitializeResult(
	val protocolVersion: String,
	val capabilities: ServerCapabilities,
	val serverInfo: Implementation,
	val instructions: String? = null,
	override val _meta: Map<String, JsonElement>? = null,
) : Result
