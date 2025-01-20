package sh.ondr.mcp4k.schema.capabilities

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import sh.ondr.mcp4k.schema.core.Result

@Serializable
data class InitializeResult(
	val protocolVersion: String,
	val capabilities: ServerCapabilities,
	val serverInfo: Implementation,
	val instructions: String? = null,
	override val _meta: Map<String, JsonElement>? = null,
) : Result
