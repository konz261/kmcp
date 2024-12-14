@file:OptIn(InternalSerializationApi::class)

package sh.ondr.kmcp.runtime

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.serializer
import sh.ondr.jsonschema.jsonSchema
import sh.ondr.kmcp.runtime.tools.CallToolResult
import sh.ondr.kmcp.runtime.tools.GenericToolHandler
import sh.ondr.kmcp.runtime.tools.ToolHandler
import sh.ondr.kmcp.schema.content.ToolContent
import sh.ondr.kmcp.schema.tools.Tool
import kotlin.reflect.KFunction

class Server private constructor() {
	companion object {
		init {
			println("Server companion init block")
		}
	}

	val tools = mutableMapOf<String, Tool>()
	val toolHandlers = mutableMapOf<String, GenericToolHandler>()

	class Builder {
		val builderTools = mutableListOf<Pair<Tool, GenericToolHandler>>()

		companion object {
			init {
				println("Server.Builder companion init block")
				println("Tool descriptions available globally: ${KMCP.toolDescriptions.size}")
			}
		}

		inline fun <reified T : @Serializable Any, reified R : ToolContent> withTool(noinline toolFunction: T.() -> R): Builder {
			require(toolFunction is KFunction<*>) { "toolHandler must be a function" }
			// TODO get description from KMCP.toolDescriptions
			val tool =
				Tool(
					name = toolFunction.name,
					description = null,
					inputSchema = jsonSchema<T>(),
				)
			val toolHandler =
				ToolHandler(
					function = toolFunction,
					paramsSerializer = (T::class).serializer(),
				)
			builderTools += tool to toolHandler
			return this
		}

		fun build(): Server =
			Server().apply {
				builderTools.forEach { (tool, handler) ->
					addTool(tool, handler)
				}
			}
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

	fun callTool(
		name: String,
		params: JsonElement?,
	): CallToolResult {
		val handler = toolHandlers[name] ?: throw IllegalStateException("Handler for tool $name not found")
		return handler.call(params)
	}
}
