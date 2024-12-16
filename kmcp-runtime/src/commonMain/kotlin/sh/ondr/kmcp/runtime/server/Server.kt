@file:OptIn(InternalSerializationApi::class)

package sh.ondr.kmcp.runtime.server

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.serializer
import sh.ondr.jsonschema.jsonSchema
import sh.ondr.kmcp.runtime.KMCP
import sh.ondr.kmcp.runtime.core.McpComponent
import sh.ondr.kmcp.runtime.tools.GenericToolHandler
import sh.ondr.kmcp.runtime.tools.ToolHandler
import sh.ondr.kmcp.runtime.transport.Transport
import sh.ondr.kmcp.schema.capabilities.Implementation
import sh.ondr.kmcp.schema.capabilities.InitializeRequest
import sh.ondr.kmcp.schema.capabilities.InitializeResult
import sh.ondr.kmcp.schema.capabilities.ServerCapabilities
import sh.ondr.kmcp.schema.content.ToolContent
import sh.ondr.kmcp.schema.tools.CallToolRequest
import sh.ondr.kmcp.schema.tools.CallToolResult
import sh.ondr.kmcp.schema.tools.Tool
import kotlin.reflect.KFunction

class Server private constructor(
	val transport: Transport,
	val tools: MutableMap<String, Tool> = mutableMapOf(),
	val toolHandlers: MutableMap<String, GenericToolHandler> = mutableMapOf(),
	val rawLoggers: List<(String) -> Unit> = mutableListOf(),
) : McpComponent(transport, basicRawLogger = rawLoggers.firstOrNull()) {
	class Builder {
		lateinit var builderTransport: Transport
		val builderTools = mutableMapOf<String, Tool>()
		val builderHandlers = mutableMapOf<String, GenericToolHandler>()
		var builderRawLoggers = mutableListOf<(String) -> Unit>()
		var used = false

		inline fun <reified T : @Serializable Any, reified R : ToolContent> withTool(noinline toolFunction: T.() -> R) =
			apply {
				require(toolFunction is KFunction<*>) { "toolHandler must be a function" }
				val name = toolFunction.name // TODO customize
				builderTools[name] =
					Tool(
						name = toolFunction.name,
						description = KMCP.toolDescriptions[toolFunction.name],
						inputSchema = jsonSchema<T>(),
					)
				builderHandlers[name] =
					ToolHandler(
						function = toolFunction,
						paramsSerializer = (T::class).serializer(),
					)
			}

		fun withRawLogger(logger: (String) -> Unit) =
			apply {
				builderRawLoggers += logger
			}

		fun withTransport(transport: Transport) =
			apply {
				builderTransport = transport
			}

		fun build(): Server {
			check(!used) { "This Builder has already been used." }
			used = true
			check(::builderTransport.isInitialized) { "Transport must be set" }
			return Server(
				transport = builderTransport,
				tools = builderTools,
				toolHandlers = builderHandlers,
				rawLoggers = builderRawLoggers,
			)
		}
	}

	override suspend fun handleCallToolRequest(params: CallToolRequest.CallToolParams): CallToolResult {
		val toolName = params.name
		val handler = toolHandlers[toolName] ?: throw IllegalStateException("Handler for tool $toolName not found")
		val jsonArguments = JsonObject(params.arguments ?: emptyMap())
		val callToolResult = handler.call(jsonArguments)
		return callToolResult
	}

	override suspend fun handleInitializeRequest(params: InitializeRequest.InitializeParams): InitializeResult {
		return InitializeResult(
			protocolVersion = "2024-11-05",
			capabilities = ServerCapabilities(),
			serverInfo = Implementation("TestServer", "1.0.0"),
		)
	}

	fun addTool(
		tool: Tool,
		handler: GenericToolHandler,
	) {
		tools[tool.name] = tool
		toolHandlers[tool.name] = handler
	}

	fun removeTool(tool: Tool) {
		tools.remove(tool.name)
		toolHandlers.remove(tool.name)
	}
}
