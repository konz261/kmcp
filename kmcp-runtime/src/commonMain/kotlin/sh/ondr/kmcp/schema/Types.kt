package sh.ondr.kmcp.schema

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * References used by some requests
 */
@Serializable
data class PromptReference(
	val type: String = "ref/prompt",
	val name: String,
)

@Serializable
data class ResourceReference(
	val type: String = "ref/resource",
	val uri: String,
)

/**
 * ClientCapabilities, Implementation, ModelPreferences, etc.
 * As per the schema, these appear in various requests.
 */
@Serializable
data class ClientCapabilities(
	val experimental: Map<String, JsonElement>? = null,
	val roots: RootsCapability? = null,
	val sampling: Map<String, JsonElement>? = null,
)

@Serializable
data class RootsCapability(
	val listChanged: Boolean? = null,
)

@Serializable
data class Implementation(
	val name: String,
	val version: String,
)

/**
 * ModelPreferences and related classes
 */
@Serializable
data class ModelPreferences(
	val hints: List<ModelHint>? = null,
	val costPriority: Double? = null,
	val speedPriority: Double? = null,
	val intelligencePriority: Double? = null,
)

@Serializable
data class ModelHint(
	val name: String? = null,
)

/**
 * SamplingMessage for CreateMessageRequest
 *
 * @param role "user" or "assistant"
 */
@Serializable
data class SamplingMessage(
	val role: String,
	val content: SamplingContent,
)

@Serializable
sealed class SamplingContent {
	@Serializable
	@SerialName("text")
	data class TextContent(
		val text: String,
		val annotations: Annotations? = null,
	) : SamplingContent()

	@Serializable
	@SerialName("image")
	data class ImageContent(
		val data: String,
		val mimeType: String,
		val annotations: Annotations? = null,
	) : SamplingContent()
}

/**
 * @param audience user or
 */
@Serializable
data class Annotations(
	/**
	 * Describes who the intended customer of this object or data is.
	 *
	 * It can include multiple entries to indicate content useful for multiple audiences (e.g., `["user", "assistant"]`).
	 */
	val audience: List<String>? = null,
	val priority: Double? = null,
)

/**
 * All request parameter data classes
 */
@Serializable
data class InitializeParams(
	val protocolVersion: String,
	val capabilities: ClientCapabilities,
	val clientInfo: Implementation,
)

@Serializable
data class PingParams(
	val _meta: Map<String, JsonElement>? = null,
)

/** Completion/Complete request params */
@Serializable
data class CompleteParams(
	val ref: RefUnion,
	val argument: CompletionArgument,
)

@Serializable
data class CompletionArgument(
	val name: String,
	val value: String,
)

@Serializable
sealed class RefUnion {
	@Serializable
	@SerialName("ref/prompt")
	data class PromptRef(val name: String) : RefUnion()

	@Serializable
	@SerialName("ref/resource")
	data class ResourceRef(val uri: String) : RefUnion()
}

/**
 * A request from the client to the server, to enable or adjust logging.
 */
@Serializable
data class LoggingLevel(
	/**
	 * The level of logging that the client wants to receive from the server.
	 * The server should send all logs at this level and higher (i.e., more severe)
	 * to the client as notifications/logging/message.
	 */
	val level: String,
)

/** Prompts/Get request params */
@Serializable
data class GetPromptParams(
	val name: String,
	val arguments: Map<String, String>? = null,
)

/** Prompts/List request params (paginated) */
@Serializable
data class PaginatedParams(
	val cursor: String? = null,
)

/** Resources/List request params (paginated) */
@Serializable
data class ListResourcesParams(
	val cursor: String? = null,
)

/** Resources/Read request params */
@Serializable
data class ReadResourceParams(
	val uri: String,
)

/** Resources/Subscribe request params */
@Serializable
data class SubscribeParams(
	val uri: String,
)

/** Resources/Unsubscribe request params */
@Serializable
data class UnsubscribeParams(
	val uri: String,
)

/** Tools/Call request params */
@Serializable
data class CallToolParams(
	val name: String,
	val arguments: Map<String, JsonElement>? = null,
)

/** Tools/List request params (paginated) */
@Serializable
data class ListToolsParams(
	val cursor: String? = null,
)

/** Prompts/List request params (paginated) */
typealias ListPromptsParams = PaginatedParams

/** Resources/Templates/List request params (paginated) */
@Serializable
data class ListResourceTemplatesParams(
	val cursor: String? = null,
)

/** Roots/List request params (no fields but let's define for consistency) */
@Serializable
data class EmptyParams(
	val _meta: Map<String, JsonElement>? = null,
)

/** Sampling/CreateMessage request params */
@Serializable
data class CreateMessageParams(
	val messages: List<SamplingMessage>,
	val modelPreferences: ModelPreferences? = null,
	val systemPrompt: String? = null,
	val includeContext: String? = null,
	val temperature: Double? = null,
	val maxTokens: Int,
	val stopSequences: List<String>? = null,
	val metadata: Map<String, JsonElement>? = null,
)
