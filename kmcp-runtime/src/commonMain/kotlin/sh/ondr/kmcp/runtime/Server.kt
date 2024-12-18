@file:OptIn(InternalSerializationApi::class)

package sh.ondr.kmcp.runtime

import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.json.JsonObject
import sh.ondr.kmcp.runtime.prompts.PromptHandler
import sh.ondr.kmcp.runtime.transport.Transport
import sh.ondr.kmcp.schema.capabilities.Implementation
import sh.ondr.kmcp.schema.capabilities.InitializeRequest.InitializeParams
import sh.ondr.kmcp.schema.capabilities.InitializeResult
import sh.ondr.kmcp.schema.capabilities.ServerCapabilities
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
import kotlin.coroutines.CoroutineContext
import kotlin.reflect.KFunction

/**
 * A Model Context Protocol (MCP) server implementation that handles
 * MCP requests from a connected client via a specified [Transport].
 *
 * Tools can be added at build time via [Builder.withTool], and can also be added or removed
 * dynamically at runtime using [addTool]/[removeTool].
 *
 * ### Example Usage
 *
 * ```kotlin
 * val server = Server.Builder()
 *     .withTransport(myTransport)
 *     .withTool(::myToolFunction)
 *     .withPrompt(
 *         name = "code_review",
 *         description = "Analyze code quality",
 *         arguments = listOf(PromptArgument(name = "code", required = true))
 *     ) { args ->
 *         val code = args?.get("code") ?: error("Missing 'code' argument")
 *         GetPromptResult(
 *             description = "Code review prompt",
 *             messages = listOf(
 *                 // ... build PromptMessage list ...
 *             )
 *         )
 *     }
 *     .withServerInfo(name = "MyCoolServer", version = "2.0.0")
 *     .withLogger { line -> println(line) }
 *     .build()
 *
 * server.start()
 * ```
 */
class Server private constructor(
	private val transport: Transport,
	private val tools: MutableList<String>,
	private val prompts: MutableMap<String, PromptInfo>,
	private val promptHandlers: MutableMap<String, PromptHandler>,
	private val logger: ((String) -> Unit)?,
	private val dispatcher: CoroutineContext,
	private val serverName: String,
	private val serverVersion: String,
) : McpComponent(
		transport = transport,
		logger = logger,
		coroutineContext = dispatcher,
	) {
	/**
	 * Dynamically adds a new tool to the server at runtime.
	 *
	 * @param tool The @Tool-annotated function reference.
	 * @return `true` if the tool was added, `false` if it was already present.
	 */
	fun addTool(tool: KFunction<*>): Boolean {
		return if (tool.name !in tools) {
			tools.add(tool.name)
			true
		} else {
			false
		}
	}

	/**
	 * Dynamically removes a previously added tool by its @Tool-annotated function reference.
	 *
	 * @param tool The @Tool-annotated function reference that was previously added.
	 * @return `true` if the tool was removed, `false` if it was not found.
	 */
	fun removeTool(tool: KFunction<*>): Boolean {
		return tools.remove(tool.name)
	}

	// -----------------------------------------------------
	// Overridden Request Handlers for MCP Operations
	// -----------------------------------------------------

	override suspend fun handleInitializeRequest(params: InitializeParams): InitializeResult {
		return InitializeResult(
			protocolVersion = MCP_VERSION,
			capabilities = ServerCapabilities(),
			serverInfo = Implementation(serverName, serverVersion),
		)
	}

	override suspend fun handleListPromptsRequest(params: ListPromptsRequest.ListPromptsParams?): ListPromptsResult {
		return ListPromptsResult(prompts = prompts.values.toList())
	}

	override suspend fun handleGetPromptRequest(params: GetPromptRequest.GetPromptParams): GetPromptResult {
		val prompt =
			prompts[params.name]
				?: throw IllegalArgumentException("Prompt not found: ${params.name}")

		val handler =
			promptHandlers[params.name]
				?: throw IllegalStateException("Handler for prompt ${params.name} not found")

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
		val handler =
			KMCP.toolHandlers[toolName]
				?: throw IllegalStateException("Handler for tool $toolName not found")
		val jsonArguments = JsonObject(params.arguments ?: emptyMap())
		return handler.call(jsonArguments)
	}

	override suspend fun handleListToolsRequest(params: ListToolsParams?): ListToolsResult {
		// Map each tool name to its ToolInfo from KMCP
		val toolInfos =
			tools.map { name ->
				KMCP.toolInfos[name]
					?: throw IllegalStateException("ToolInfo not found for tool: $name")
			}
		return ListToolsResult(tools = toolInfos)
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
	 *     .withTransport(myTransport)
	 *     .withTool(::myToolFunction)
	 *     .withPrompt("myPrompt", "A demo prompt") { args -> ... }
	 *     .withServerInfo("MyServer", "1.2.3")
	 *     .withLogger { line -> println(line) }
	 *     .build()
	 * ```
	 */
	class Builder {
		private val builderTools = mutableSetOf<String>()
		private val builderPrompts = mutableMapOf<String, PromptInfo>()
		private val builderPromptHandlers = mutableMapOf<String, PromptHandler>()
		private var builderTransport: Transport? = null
		private var builderLogger: ((String) -> Unit)? = null
		private var builderDispatcher: CoroutineContext = Dispatchers.Default
		private var builderServerName: String = "TestServer"
		private var builderServerVersion: String = "1.0.0"
		private var used = false

		/**
		 * Sets the [Transport] used by the server to communicate with clients.
		 * This must be called before [build], or an error is thrown.
		 */
		fun withTransport(transport: Transport) =
			apply {
				builderTransport = transport
			}

		/**
		 * Registers a tool by referencing its @Tool-annotated function.
		 */
		fun withTool(toolFunction: KFunction<*>) =
			apply {
				require(toolFunction.name !in builderTools) {
					"Tool with name ${toolFunction.name} already registered."
				}
				builderTools.add(toolFunction.name)
			}

		/**
		 * Registers tools by referencing their @Tool-annotated functions.
		 */
		fun withTools(vararg toolFunctions: KFunction<*>) =
			apply {
				toolFunctions.forEach { withTool(it) }
			}

		/**
		 * Registers a prompt with the server.
		 *
		 * @param name Unique name for the prompt.
		 * @param description Optional human-readable description.
		 * @param arguments Optional list of [PromptArgument] defining the prompt's expected inputs.
		 * @param generate A lambda that, given arguments, returns a [GetPromptResult].
		 */
		fun withPrompt(
			name: String,
			description: String? = null,
			arguments: List<PromptArgument>? = null,
			generate: (Map<String, String>?) -> GetPromptResult,
		) = apply {
			require(name !in builderPrompts) {
				"Prompt with name $name already registered."
			}
			builderPrompts[name] = PromptInfo(name, description, arguments)
			builderPromptHandlers[name] = PromptHandler(generate)
		}

		/**
		 * Sets the coroutine context (or dispatcher) for the server's internal coroutines.
		 *
		 * Defaults to [Dispatchers.Default] if not set.
		 */
		fun withDispatcher(dispatcher: CoroutineContext) =
			apply {
				builderDispatcher = dispatcher
			}

		/**
		 * Adds a logger callback for incoming/outgoing messages.
		 *
		 * Useful for debugging or auditing. If not set, no logging occurs.
		 */
		fun withLogger(logger: (String) -> Unit) =
			apply {
				builderLogger = logger
			}

		/**
		 * Sets the server's name and version, reported in the `initialize` response.
		 *
		 * Defaults to "TestServer" and "1.0.0" if not provided.
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
		 * @throws IllegalStateException if called more than once or if a required field is missing.
		 */
		fun build(): Server {
			check(!used) { "This Builder has already been used." }
			used = true

			val transport = builderTransport ?: error("Transport must be set before building.")
			return Server(
				transport = transport,
				tools = builderTools.toMutableList(),
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
