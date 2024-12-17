@file:OptIn(InternalSerializationApi::class)

package sh.ondr.kmcp.runtime

import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.serializer
import sh.ondr.jsonschema.jsonSchema
import sh.ondr.kmcp.runtime.tools.GenericToolHandler
import sh.ondr.kmcp.runtime.tools.ToolHandler
import sh.ondr.kmcp.runtime.transport.Transport
import sh.ondr.kmcp.schema.capabilities.Implementation
import sh.ondr.kmcp.schema.capabilities.InitializeRequest.InitializeParams
import sh.ondr.kmcp.schema.capabilities.InitializeResult
import sh.ondr.kmcp.schema.capabilities.ServerCapabilities
import sh.ondr.kmcp.schema.content.ToolContent
import sh.ondr.kmcp.schema.tools.CallToolRequest.CallToolParams
import sh.ondr.kmcp.schema.tools.CallToolResult
import sh.ondr.kmcp.schema.tools.ListToolsRequest.ListToolsParams
import sh.ondr.kmcp.schema.tools.ListToolsResult
import sh.ondr.kmcp.schema.tools.Tool
import kotlin.coroutines.CoroutineContext
import kotlin.reflect.KFunction

/**
 * A server implementation that communicates using the MCP protocol.
 *
 * This class extends [McpComponent] to handle JSON-RPC requests and notifications.
 * It supports defining tools (functions) that the connected client or host can invoke.
 *
 * You typically create a [Server] using the [Server.Builder].
 */
class Server private constructor(
	val transport: Transport,
	private val tools: MutableMap<String, Tool> = mutableMapOf(),
	private val toolHandlers: MutableMap<String, GenericToolHandler> = mutableMapOf(),
	private val rawLoggers: List<(String) -> Unit> = mutableListOf(),
	dispatcher: CoroutineContext = Dispatchers.Default,
) : McpComponent(transport, basicRawLogger = rawLoggers.firstOrNull(), coroutineContext = dispatcher) {
	/**
	 * Adds a new tool and its corresponding handler at runtime.
	 *
	 * @param tool The [Tool] definition containing its name, description, and input schema.
	 * @param handler The [GenericToolHandler] that implements the tool's logic.
	 */
	fun addTool(
		tool: Tool,
		handler: GenericToolHandler,
	) {
		tools[tool.name] = tool
		toolHandlers[tool.name] = handler
	}

	/**
	 * Removes a previously added tool.
	 *
	 * @param tool The [Tool] to remove.
	 */
	fun removeTool(tool: Tool) {
		tools.remove(tool.name)
		toolHandlers.remove(tool.name)
	}

	// -----------------------------------------------------
	// Overridden request handlers for MCP operations
	// -----------------------------------------------------

	override suspend fun handleCallToolRequest(params: CallToolParams): CallToolResult {
		val toolName = params.name
		val handler = toolHandlers[toolName] ?: throw IllegalStateException("Handler for tool $toolName not found")

		val jsonArguments = JsonObject(params.arguments ?: emptyMap())
		val callToolResult = handler.call(jsonArguments)
		return callToolResult
	}

	override suspend fun handleListToolsRequest(params: ListToolsParams?): ListToolsResult {
		return ListToolsResult(tools.values.toList())
	}

	override suspend fun handleInitializeRequest(params: InitializeParams): InitializeResult {
		return InitializeResult(
			protocolVersion = "2024-11-05",
			capabilities = ServerCapabilities(),
			serverInfo = Implementation("TestServer", "1.0.0"),
		)
	}

	// -----------------------------------------------------
	// Builder
	// -----------------------------------------------------

	/**
	 * Builder for creating a [Server] instance.
	 *
	 * Usage:
	 * ```
	 * val server = Server.Builder()
	 *     .withTransport(myTransport)
	 *     .withTool(MyParams::myToolFunction)
	 *     .withRawLogger { line -> println(line) }
	 *     .build()
	 * ```
	 */
	class Builder {
		@PublishedApi
		internal var builderTools = mutableMapOf<String, Tool>()

		@PublishedApi
		internal val builderHandlers = mutableMapOf<String, GenericToolHandler>()
		private var builderTransport: Transport? = null
		private val builderRawLoggers = mutableListOf<(String) -> Unit>()
		private var builderDispatcher: CoroutineContext = Dispatchers.Default
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
		 * The function must be a Kotlin function reference, i.e., `MyClass::myFunction`.
		 */
		inline fun <reified T : @Serializable Any, reified R : ToolContent> withTool(noinline toolFunction: T.() -> R) =
			apply {
				require(toolFunction is KFunction<*>) {
					"toolHandler must be a function reference, e.g., MyClass::myFunction"
				}
				require(toolFunction.name !in builderTools) {
					"Tool with name ${toolFunction.name} already registered."
				}
				val name = toolFunction.name
				builderTools += name to
					Tool(
						name = name,
						description = KMCP.toolDescriptions[name],
						inputSchema = jsonSchema<T>(),
					)
				builderHandlers[name] = ToolHandler(function = toolFunction, paramsSerializer = (T::class).serializer())
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
		 * Adds a raw logger for incoming/outgoing messages.
		 * Can be called multiple times to add multiple loggers.
		 */
		fun withRawLogger(logger: (String) -> Unit) =
			apply {
				builderRawLoggers += logger
			}

		/**
		 * Builds the [Server] instance.
		 * @throws IllegalStateException if transport was not set
		 * @throws IllegalStateException if this builder is reused after building
		 */
		fun build(): Server {
			check(!used) { "This Builder has already been used." }
			used = true

			return Server(
				transport = builderTransport ?: error("Transport must be set via withTransport before building."),
				tools = builderTools.toMutableMap(),
				toolHandlers = builderHandlers.toMutableMap(),
				rawLoggers = builderRawLoggers.toMutableList(),
				dispatcher = builderDispatcher,
			)
		}
	}
}
