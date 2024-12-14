package sh.ondr.kmcp.schema.capabilities

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Some of the result types depend on structures not yet defined:
 * - ServerCapabilities
 * - Resource, ResourceTemplate
 * - Prompt, PromptMessage, PromptArgument
 * - Tool
 * - TextResourceContents, BlobResourceContents, EmbeddedResource
 *
 * We'll define these according to the schema.
 */
@Serializable
data class ServerCapabilities(
	val experimental: Map<String, JsonElement>? = null,
	val logging: Map<String, JsonElement>? = null,
	val prompts: PromptsCapability? = null,
	val resources: ResourcesCapability? = null,
	val tools: ToolsCapability? = null,
)

@Serializable
data class PromptsCapability(
	val listChanged: Boolean? = null,
)

@Serializable
data class ResourcesCapability(
	val subscribe: Boolean? = null,
	val listChanged: Boolean? = null,
)

@Serializable
data class ToolsCapability(
	val listChanged: Boolean? = null,
)
