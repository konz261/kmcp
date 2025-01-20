package sh.ondr.mcp4k.schema.capabilities

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ServerCapabilities(
	val experimental: Map<String, JsonElement>? = null,
	val logging: Map<String, JsonElement>? = null,
	val prompts: PromptsCapability? = null,
	val resources: ResourcesCapability? = null,
	val tools: ToolsCapability? = null,
)
