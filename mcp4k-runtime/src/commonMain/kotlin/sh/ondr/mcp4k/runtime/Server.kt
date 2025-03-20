@file:OptIn(InternalSerializationApi::class)

package sh.ondr.mcp4k.runtime

import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.serializer
import sh.ondr.koja.Schema
import sh.ondr.koja.toSchema
import sh.ondr.mcp4k.runtime.core.MCP_VERSION
import sh.ondr.mcp4k.runtime.core.mcpPromptHandlers
import sh.ondr.mcp4k.runtime.core.mcpPromptParams
import sh.ondr.mcp4k.runtime.core.mcpToolHandlers
import sh.ondr.mcp4k.runtime.core.pagination.paginate
import sh.ondr.mcp4k.runtime.error.MethodNotFoundException
import sh.ondr.mcp4k.runtime.error.ResourceNotFoundException
import sh.ondr.mcp4k.runtime.resources.ResourceProvider
import sh.ondr.mcp4k.runtime.resources.ResourceProviderManager
import sh.ondr.mcp4k.runtime.serialization.toJsonObject
import sh.ondr.mcp4k.runtime.tools.getMcpTool
import sh.ondr.mcp4k.runtime.transport.Transport
import sh.ondr.mcp4k.schema.capabilities.Implementation
import sh.ondr.mcp4k.schema.capabilities.InitializeRequest.InitializeParams
import sh.ondr.mcp4k.schema.capabilities.InitializeResult
import sh.ondr.mcp4k.schema.capabilities.PromptsCapability
import sh.ondr.mcp4k.schema.capabilities.ResourcesCapability
import sh.ondr.mcp4k.schema.capabilities.ServerCapabilities
import sh.ondr.mcp4k.schema.capabilities.ToolsCapability
import sh.ondr.mcp4k.schema.core.EmptyResult
import sh.ondr.mcp4k.schema.prompts.GetPromptRequest.GetPromptParams
import sh.ondr.mcp4k.schema.prompts.GetPromptResult
import sh.ondr.mcp4k.schema.prompts.ListPromptsRequest.ListPromptsParams
import sh.ondr.mcp4k.schema.prompts.ListPromptsResult
import sh.ondr.mcp4k.schema.prompts.Prompt
import sh.ondr.mcp4k.schema.prompts.PromptArgument
import sh.ondr.mcp4k.schema.resources.ListResourceTemplatesRequest.ListResourceTemplatesParams
import sh.ondr.mcp4k.schema.resources.ListResourceTemplatesResult
import sh.ondr.mcp4k.schema.resources.ListResourcesRequest.ListResourcesParams
import sh.ondr.mcp4k.schema.resources.ListResourcesResult
import sh.ondr.mcp4k.schema.resources.ReadResourceRequest.ReadResourceParams
import sh.ondr.mcp4k.schema.resources.ReadResourceResult
import sh.ondr.mcp4k.schema.resources.ResourceListChangedNotification
import sh.ondr.mcp4k.schema.resources.ResourceUpdatedNotification
import sh.ondr.mcp4k.schema.resources.ResourceUpdatedNotification.ResourceUpdatedParams
import sh.ondr.mcp4k.schema.resources.SubscribeRequest.SubscribeParams
import sh.ondr.mcp4k.schema.resources.UnsubscribeRequest.UnsubscribeParams
import sh.ondr.mcp4k.schema.tools.CallToolRequest.CallToolParams
import sh.ondr.mcp4k.schema.tools.CallToolResult
import sh.ondr.mcp4k.schema.tools.ListToolsRequest.ListToolsParams
import sh.ondr.mcp4k.schema.tools.ListToolsResult
import kotlin.coroutines.CoroutineContext
import kotlin.reflect.KFunction

/**
 * A Model Context Protocol (MCP) server implementation that handles
 * MCP requests from a connected client via a specified [Transport].
 *
 * Example usage:
 * ```kotlin
 * val server = Server.Builder()
 *     .withPageSize(25)
 *     .withPrompt(::myPromptFunction)
 *     .withResourceProvider(myLocalFileProvider)
 *     .withServerInfo("MyServer", "1.0.0")
 *     .withTool(::myToolFunction)
 *     .withTransport(myTransport)
 *     .withTransportLogger(
 *       logIncoming = { msg -> println("SERVER INCOMING: $msg") },
 *       logOutgoing = { msg -> println("SERVER OUTGOING: $msg") },
 *     )
 *     .build()
 *
 * server.start()
 * ```
 *
 * Tools and prompts can be added at build time via [Builder.withTool], [Builder.withTools], [Builder.withPrompt], and [Builder.withPrompts].
 * They can also be added or removed dynamically at runtime if needed.
 */
class Server private constructor(
	builderResourceProviders: List<ResourceProvider>,
	@PublishedApi
	internal val serverContext: ServerContext?,
	private val dispatcher: CoroutineContext,
	private val logIncoming: suspend (String) -> Unit,
	private val logOutgoing: suspend (String) -> Unit,
	private val pageSize: Int,
	private val prompts: MutableList<String>,
	private val serverName: String,
	private val serverVersion: String,
	private val tools: MutableList<String>,
	private val transport: Transport,
) : McpComponent(
		transport = transport,
		logIncoming = logIncoming,
		logOutgoing = logOutgoing,
		coroutineContext = dispatcher,
	) {
	inline fun <reified T> getContextAs(): T = serverContext as T

	/**
	 * Manages one or more ResourceProviders, merging resource lists and
	 * routing resource-change callbacks into server notifications.
	 */
	private val resourceManager = ResourceProviderManager(
		notifyResourceChanged = { uri ->
			val notification = ResourceUpdatedNotification(
				ResourceUpdatedParams(uri = uri),
			)
			sendNotification(notification)
		},
		notifyResourcesListChanged = {
			val notification = ResourceListChangedNotification()
			sendNotification(notification)
		},
		builderResourceProviders = builderResourceProviders,
	)

	/**
	 * Dynamically adds a new tool to the server at runtime.
	 *
	 * @param tool The @McpTool-annotated function reference.
	 * @return `true` if the tool was added, `false` if it was already present.
	 */
	fun addTool(tool: KFunction<*>): Boolean =
		if (tool.name !in tools) {
			tools.add(tool.name)
			true
		} else {
			false
		}

	/**
	 * Dynamically removes a previously added tool by its @McpTool-annotated function reference.
	 *
	 * @param tool The @McpTool-annotated function reference that was previously added.
	 * @return `true` if the tool was removed, `false` if it was not found.
	 */
	fun removeTool(tool: KFunction<*>): Boolean {
		return tools.remove(tool.name)
	}

	/**
	 * Dynamically adds a new prompt to the server at runtime.
	 *
	 * @param prompt The @McpPrompt-annotated function reference.
	 * @return `true` if the prompt was added, `false` if it was already present.
	 */
	fun addPrompt(prompt: KFunction<*>): Boolean {
		return if (prompt.name !in prompts) {
			prompts.add(prompt.name)
			true
		} else {
			false
		}
	}

	/**
	 * Dynamically removes a previously added prompt by its @McpPrompt-annotated function reference.
	 *
	 * @param prompt The @McpPrompt-annotated function reference that was previously added.
	 * @return `true` if the prompt was removed, `false` if it was not found.
	 */
	fun removePrompt(prompt: KFunction<*>): Boolean {
		return prompts.remove(prompt.name)
	}

	// -----------------------------------------------------
	// Overridden Request Handlers for MCP Operations
	// -----------------------------------------------------

	override suspend fun handleInitializeRequest(params: InitializeParams): InitializeResult {
		val toolsCapability = if (tools.isNotEmpty()) ToolsCapability() else null
		val promptsCapability = if (prompts.isNotEmpty()) PromptsCapability() else null
		val resourcesCapability = if (resourceManager.providers.isNotEmpty()) {
			ResourcesCapability(
				subscribe = resourceManager.supportsSubscriptions,
				listChanged = true,
			)
		} else {
			null
		}

		return InitializeResult(
			protocolVersion = MCP_VERSION,
			capabilities = ServerCapabilities(
				tools = toolsCapability,
				prompts = promptsCapability,
				resources = resourcesCapability,
			),
			serverInfo = Implementation(serverName, serverVersion),
		)
	}

	override suspend fun handleListPromptsRequest(params: ListPromptsParams?): ListPromptsResult {
		val allPrompts = prompts.map { name ->
			val params = mcpPromptParams[name] ?: throw IllegalStateException("Prompt not found: $name")
			val paramsSchema = params.serializer().descriptor.toSchema() as Schema.ObjectSchema
			val requiredParams = paramsSchema
				.toJsonObject()
				.jsonObject["required"]
				?.jsonArray
				?.map {
					it.jsonPrimitive.content
				} ?: emptyList()
			Prompt(
				name = name,
				description = paramsSchema.description,
				arguments = paramsSchema.copy(description = null).properties!!.map { (name, schema) ->
					PromptArgument(
						name = name,
						description = schema.description,
						required = name in requiredParams,
					)
				},
			)
		}
		val (promptsOnPage, nextCursor) = paginate(
			items = allPrompts,
			cursor = params?.cursor,
			pageSize = pageSize,
		)
		return ListPromptsResult(
			prompts = promptsOnPage,
			nextCursor = nextCursor,
		)
	}

	override suspend fun handleGetPromptRequest(params: GetPromptParams): GetPromptResult {
		val promptName = params.name
		if (promptName !in prompts) {
			throw MethodNotFoundException("Prompt '$promptName' not registered on this server.")
		}
		val handler = mcpPromptHandlers[promptName] ?: throw MethodNotFoundException(
			"Handler for prompt '$promptName' not found in global registry.",
		)
		val jsonArgs = params.arguments
			?.mapValues { JsonPrimitive(it.value) }
			?.let { JsonObject(it) }
			?: JsonObject(emptyMap())

		return handler.call(
			server = this,
			params = jsonArgs,
		)
	}

	override suspend fun handleCallToolRequest(params: CallToolParams): CallToolResult {
		val toolName = params.name
		if (toolName !in tools) {
			throw MethodNotFoundException("Tool '$toolName' not registered on this server.")
		}
		val handler = mcpToolHandlers[toolName] ?: throw IllegalStateException("Handler for tool '$toolName' not found in global registry.")
		val jsonArguments = JsonObject(params.arguments ?: emptyMap())
		return handler.call(
			server = this,
			params = jsonArguments,
		)
	}

	override suspend fun handleListToolsRequest(params: ListToolsParams?): ListToolsResult {
		val allTools = tools.map { name ->
			getMcpTool(name)
		}
		val (toolsOnPage, nextCursor) = paginate(
			items = allTools,
			cursor = params?.cursor,
			pageSize = pageSize,
		)
		return ListToolsResult(
			tools = toolsOnPage,
			nextCursor = nextCursor,
		)
	}

	override suspend fun handleListResourcesRequest(params: ListResourcesParams?): ListResourcesResult {
		val allResources = resourceManager.listResources()
		val (resourcesOnPage, nextCursor) = paginate(
			items = allResources,
			cursor = params?.cursor,
			pageSize = pageSize,
		)
		return ListResourcesResult(
			resources = resourcesOnPage,
			nextCursor = nextCursor,
		)
	}

	override suspend fun handleReadResourceRequest(params: ReadResourceParams): ReadResourceResult {
		val uri = params.uri
		val contents = resourceManager.readResource(uri) ?: throw ResourceNotFoundException("Resource not found: $uri")
		return ReadResourceResult(contents = listOf(contents))
	}

	override suspend fun handleListResourceTemplatesRequest(params: ListResourceTemplatesParams?): ListResourceTemplatesResult {
		val allTemplates = resourceManager.listResourceTemplates()
		val (templatesOnPage, nextCursor) = paginate(
			items = allTemplates,
			cursor = params?.cursor,
			pageSize = pageSize,
		)
		return ListResourceTemplatesResult(
			resourceTemplates = templatesOnPage,
			nextCursor = nextCursor,
		)
	}

	override suspend fun handleSubscribeRequest(params: SubscribeParams): EmptyResult {
		resourceManager.subscribe(params.uri)
		return EmptyResult()
	}

	override suspend fun handleUnsubscribeRequest(params: UnsubscribeParams): EmptyResult {
		resourceManager.removeSubscription(params.uri)
		return EmptyResult()
	}

	/**
	 * Builder for constructing a [Server] instance.
	 *
	 * All configuration is done via fluent API calls, and once [build] is called,
	 * you get a fully configured Server ready to be started with [Server.start].
	 *
	 * Example:
	 * ```kotlin
	 * val server = Server.Builder()
	 *     .withPageSize(25)
	 *     .withPrompt(::myPromptFunction)
	 *     .withResourceProvider(myLocalFileProvider)
	 *     .withServerInfo("MyServer", "1.0.0")
	 *     .withTool(::myToolFunction)
	 *     .withTransport(myTransport)
	 *     .withTransportLogger(
	 *       logIncoming = { msg -> println("SERVER INCOMING: $msg") },
	 *       logOutgoing = { msg -> println("SERVER OUTGOING: $msg") },
	 *     )
	 *     .build()
	 *
	 * server.start()
	 * ```
	 */
	class Builder {
		private var builderContext: ServerContext? = null
		private var builderDispatcher: CoroutineContext = Dispatchers.Default
		private var builderLogIncoming: suspend (String) -> Unit = {}
		private var builderLogOutgoing: suspend (String) -> Unit = {}
		private var builderPageSize: Int = 20
		private val builderPrompts = mutableSetOf<String>()
		private val builderResourceProviders = mutableListOf<ResourceProvider>()
		private var builderServerName: String = "MyServer"
		private var builderServerVersion: String = "1.0.0"
		private val builderTools = mutableSetOf<String>()
		private var builderTransport: Transport? = null
		private var used = false

		fun withContext(context: ServerContext) =
			apply {
				builderContext = context
			}

		/**
		 * Sets the coroutine context (or dispatcher) for the server's internal coroutines.
		 * Defaults to [Dispatchers.Default] if not set.
		 */
		fun withDispatcher(dispatcher: CoroutineContext) =
			apply {
				builderDispatcher = dispatcher
			}

		/**
		 * Sets the default page size for paginated responses.
		 * Defaults to 20.
		 */
		fun withPageSize(pageSize: Int) =
			apply {
				require(pageSize > 0) { "Page size must be greater than 0." }
				builderPageSize = pageSize
			}

		/**
		 * Registers a prompt by referencing its @McpPrompt-annotated function.
		 */
		fun withPrompt(promptFunction: KFunction<*>) =
			apply {
				require(promptFunction.name !in builderPrompts) {
					"Prompt with name ${promptFunction.name} already registered."
				}
				builderPrompts.add(promptFunction.name)
			}

		/**
		 * Registers multiple prompts by referencing their @McpPrompt-annotated functions.
		 */
		fun withPrompts(vararg promptFunctions: KFunction<*>) =
			apply {
				promptFunctions.forEach { withPrompt(it) }
			}

		/**
		 * Registers a resource provider that can list/read resources.
		 */
		fun withResourceProvider(provider: ResourceProvider) =
			apply {
				this.builderResourceProviders.add(provider)
			}

		/**
		 * Sets the server's name and version, reported in the `initialize` response.
		 * Defaults to "MyServer" and "1.0.0" if not provided.
		 */
		fun withServerInfo(
			name: String,
			version: String,
		) = apply {
			builderServerName = name
			builderServerVersion = version
		}

		/**
		 * Registers a tool by referencing its @McpTool-annotated function.
		 */
		fun withTool(toolFunction: KFunction<*>) =
			apply {
				require(toolFunction.name !in builderTools) {
					"Tool with name ${toolFunction.name} already registered."
				}
				builderTools.add(toolFunction.name)
			}

		/**
		 * Registers multiple tools by referencing their @McpTool-annotated functions.
		 */
		fun withTools(vararg toolFunctions: KFunction<*>) =
			apply {
				toolFunctions.forEach { withTool(it) }
			}

		/**
		 * Sets the [Transport] used by the server to communicate with clients.
		 * This must be called before [build], or an error is thrown.
		 */
		fun withTransport(transport: Transport) =
			apply {
				builderTransport = transport
			}

		/**
		 * Sets separate loggers for incoming and outgoing transport messages.
		 *
		 * @param logIncoming Logger for incoming transport messages
		 * @param logOutgoing Logger for outgoing transport messages
		 */
		fun withTransportLogger(
			logIncoming: suspend (String) -> Unit = {},
			logOutgoing: suspend (String) -> Unit = {},
		) = apply {
			builderLogIncoming = logIncoming
			builderLogOutgoing = logOutgoing
		}

		/**
		 * Builds the [Server] instance.
		 *
		 * @throws IllegalStateException if called more than once or if a required field is missing.
		 */
		fun build(): Server {
			check(!used) { "This Builder has already been used." }
			used = true

			val transport = builderTransport ?: error("Transport must be set before building.")
			return Server(
				builderResourceProviders = builderResourceProviders.toList(),
				serverContext = builderContext,
				dispatcher = builderDispatcher,
				logIncoming = builderLogIncoming,
				logOutgoing = builderLogOutgoing,
				pageSize = builderPageSize,
				prompts = builderPrompts.toMutableList(),
				serverName = builderServerName,
				serverVersion = builderServerVersion,
				tools = builderTools.toMutableList(),
				transport = transport,
			)
		}
	}
}
