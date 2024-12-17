@file:OptIn(InternalSerializationApi::class)

package sh.ondr.kmcp.runtime

import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.serializer
import sh.ondr.jsonschema.jsonSchema
import sh.ondr.kmcp.runtime.prompts.PromptHandler
import sh.ondr.kmcp.runtime.tools.GenericToolHandler
import sh.ondr.kmcp.runtime.tools.ToolHandler
import sh.ondr.kmcp.runtime.transport.Transport
import sh.ondr.kmcp.schema.capabilities.Implementation
import sh.ondr.kmcp.schema.capabilities.InitializeRequest.InitializeParams
import sh.ondr.kmcp.schema.capabilities.InitializeResult
import sh.ondr.kmcp.schema.capabilities.ServerCapabilities
import sh.ondr.kmcp.schema.content.ToolContent
import sh.ondr.kmcp.schema.prompts.GetPromptRequest
import sh.ondr.kmcp.schema.prompts.GetPromptResult
import sh.ondr.kmcp.schema.prompts.ListPromptsRequest
import sh.ondr.kmcp.schema.prompts.ListPromptsResult
import sh.ondr.kmcp.schema.prompts.PromptArgument
import sh.ondr.kmcp.schema.prompts.PromptInfo
import sh.ondr.kmcp.schema.tools.CallToolRequest.CallToolParams
import sh.ondr.kmcp.schema.tools.CallToolResult
import sh.ondr.kmcp.schema.tools.ListToolsRequest.ListToolsParams
import sh.ondr.kmcp.schema.tools.ListToolsResult
import sh.ondr.kmcp.schema.tools.ToolInfo
import kotlin.coroutines.CoroutineContext
import kotlin.reflect.KFunction

/**
 * A server implementation that communicates using the MCP protocol.
 *
 * The [Server] is created via its [Builder]. Once constructed, call [start] to begin processing.
 *
 * Example usage:
 * ```
 * val server = Server.Builder()
 *     .withTransport(myTransport)
 *     .withTool(MyParams::myToolFunction)
 *     .withPrompt(
 *         name = "code_review",
 *         description = "Analyze code quality and suggest improvements",
 *         arguments = listOf(PromptArgument(name = "code", required = true)),
 *     ) { args ->
 *         val code = args?.get("code") ?: throw IllegalArgumentException("Missing 'code' argument")
 *         GetPromptResult(
 *             description = "Code review prompt",
 *             messages = listOf(
 *                 PromptMessage(
 *                     role = Role.user,
 *                     content = TextContent("Please review this code:\n$code")
 *                 )
 *             )
 *         )
 *     }
 *     .withServerInfo(name = "MyCoolServer", version = "2.0.0")
 *     .withLogger { line -> println(line) }
 *     .build()
 *
 * server.start()
 * ```
 *
 * You can dynamically add or remove tools at runtime using [addTool] and [removeTool].
 * Similarly, prompts can be managed during the server's lifecycle to reflect current capabilities.
 */
class Server private constructor(
	private val transport: Transport,
	private val tools: MutableMap<String, ToolInfo>,
	private val toolHandlers: MutableMap<String, GenericToolHandler>,
	private val prompts: MutableMap<String, PromptInfo>,
	private val promptHandlers: MutableMap<String, PromptHandler>,
	private val logger: ((String) -> Unit)?,
	private val dispatcher: CoroutineContext, // For testing
	private val serverName: String,
	private val serverVersion: String,
) : McpComponent(
		transport,
		logger = logger,
		coroutineContext = dispatcher,
	) {
	/**
	 * Adds a new tool and its corresponding handler at runtime.
	 *
	 * @param tool The [ToolInfo] definition containing its name, description, and input schema.
	 * @param handler The [GenericToolHandler] that implements the tool's logic.
	 */
	fun addTool(
		tool: ToolInfo,
		handler: GenericToolHandler,
	) {
		tools[tool.name] = tool
		toolHandlers[tool.name] = handler
	}

	/**
	 * Removes a previously added tool.
	 *
	 * @param tool The [ToolInfo] to remove.
	 */
	fun removeTool(tool: ToolInfo) {
		tools.remove(tool.name)
		toolHandlers.remove(tool.name)
	}

	// -----------------------------------------------------
	// Overridden request handlers for MCP operations
	// -----------------------------------------------------

	override suspend fun handleInitializeRequest(params: InitializeParams): InitializeResult {
		return InitializeResult(
			protocolVersion = MCP_VERSION,
			capabilities = ServerCapabilities(),
			serverInfo = Implementation(serverName, serverVersion),
		)
	}

	override suspend fun handleListPromptsRequest(params: ListPromptsRequest.ListPromptsParams?): ListPromptsResult {
		// Just return all prompts
		return ListPromptsResult(prompts = prompts.values.toList())
	}

	override suspend fun handleGetPromptRequest(params: GetPromptRequest.GetPromptParams): GetPromptResult {
		val prompt = prompts[params.name] ?: throw IllegalArgumentException("Prompt not found: ${params.name}")
		val handler = promptHandlers[params.name] ?: throw IllegalStateException("Handler for prompt ${params.name} not found")

		// Validate required arguments
		prompt.arguments?.forEach { arg ->
			if (arg.required == true && (params.arguments?.containsKey(arg.name) != true)) {
				throw IllegalArgumentException("Missing required argument: ${arg.name}")
			}
		}

		return handler.generate(params.arguments)
	}

	override suspend fun handleCallToolRequest(params: CallToolParams): CallToolResult {
		val toolName = params.name
		val handler = toolHandlers[toolName] ?: throw IllegalStateException("Handler for tool $toolName not found")
		val jsonArguments = JsonObject(params.arguments ?: emptyMap())
		return handler.call(jsonArguments)
	}

	override suspend fun handleListToolsRequest(params: ListToolsParams?): ListToolsResult {
		return ListToolsResult(tools.values.toList())
	}

	/**
	 * Builder for creating a [Server] instance.
	 *
	 * Usage:
	 * ```
	 * val server = Server.Builder()
	 *     .withTransport(myTransport)
	 *     .withTool(MyParams::myToolFunction)
	 *     .withServerInfo("MyServer", "1.2.3")
	 *     .build()
	 * ```
	 */
	class Builder {
		@PublishedApi
		internal var builderTools = mutableMapOf<String, ToolInfo>()

		@PublishedApi
		internal val builderHandlers = mutableMapOf<String, GenericToolHandler>()

		@PublishedApi
		internal val builderPrompts = mutableMapOf<String, PromptInfo>()

		@PublishedApi
		internal val builderPromptHandlers = mutableMapOf<String, PromptHandler>()

		private var builderTransport: Transport? = null
		private var builderLogger: ((String) -> Unit)? = null
		private var builderDispatcher: CoroutineContext = Dispatchers.Default
		private var builderServerName: String = "TestServer"
		private var builderServerVersion: String = "1.0.0"
		private var used = false

		/**
		 * Sets the [Transport] to be used by the server.
		 * This is mandatory and must be called before [build].
		 */
		fun withTransport(transport: Transport) =
			apply {
				builderTransport = transport
			}

		/**
		 * Registers a tool function for the server.
		 *
		 * @param toolFunction A function reference from a serializable class T that returns a [ToolContent].
		 * The function must be a Kotlin function reference, e.g., `MyClass::myFunction`.
		 */
		inline fun <reified T : @Serializable Any> withTool(noinline toolFunction: T.() -> ToolContent) =
			apply {
				require(toolFunction is KFunction<*>) {
					"toolHandler must be a function reference, e.g., MyClass::myFunction"
				}
				require(toolFunction.name !in builderTools) {
					"Tool with name ${toolFunction.name} already registered."
				}
				val name = toolFunction.name
				builderTools[name] =
					ToolInfo(
						name = name,
						description = KMCP.toolDescriptions[name],
						inputSchema = jsonSchema<T>(),
					)
				builderHandlers[name] = ToolHandler(function = toolFunction, paramsSerializer = (T::class).serializer())
			}

		/**
		 * Registers a prompt.
		 * @param name Unique name for the prompt.
		 * @param description Optional description.
		 * @param arguments Optional list of arguments.
		 * @param generate A lambda that takes arguments and returns a GetPromptResult.
		 */
		fun withPrompt(
			name: String,
			description: String? = null,
			arguments: List<PromptArgument>? = null,
			generate: (Map<String, String>?) -> GetPromptResult,
		) = apply {
			require(name !in builderPrompts) { "Prompt with name $name already registered." }
			builderPrompts[name] = PromptInfo(name, description, arguments)
			builderPromptHandlers[name] = PromptHandler(generate)
		}

		/**
		 * Sets a coroutine dispatcher or context for the server's internal coroutines.
		 * Defaults to [Dispatchers.Default].
		 */
		fun withDispatcher(dispatcher: CoroutineContext) =
			apply {
				builderDispatcher = dispatcher
			}

		/**
		 * Adds a logger for incoming/outgoing messages.
		 */
		fun withLogger(logger: (String) -> Unit) =
			apply {
				builderLogger = logger
			}

		/**
		 * Sets the server's name and version, which are returned during initialization.
		 *
		 * If not set, defaults to "TestServer" and "1.0.0".
		 */
		fun withServerInfo(
			name: String,
			version: String,
		) = apply {
			builderServerName = name
			builderServerVersion = version
		}

		/**
		 * Builds the [Server] instance.
		 *
		 * @throws IllegalStateException if transport was not set before building
		 * @throws IllegalStateException if this builder is reused after building
		 */
		fun build(): Server {
			check(!used) { "This Builder has already been used." }
			used = true

			return Server(
				transport = builderTransport ?: error("Transport must be set via withTransport before building."),
				tools = builderTools.toMutableMap(),
				toolHandlers = builderHandlers.toMutableMap(),
				prompts = builderPrompts.toMutableMap(),
				promptHandlers = builderPromptHandlers.toMutableMap(),
				logger = builderLogger,
				dispatcher = builderDispatcher,
				serverName = builderServerName,
				serverVersion = builderServerVersion,
			)
		}
	}
}
