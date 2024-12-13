@file:OptIn(InternalSerializationApi::class)

package sh.ondr.kmcp.runtime

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.serializer
import sh.ondr.jsonschema.jsonSchema
import sh.ondr.kmcp.runtime.content.ToolContent
import sh.ondr.kmcp.runtime.tools.CallToolResult
import sh.ondr.kmcp.runtime.tools.GenericToolMeta
import sh.ondr.kmcp.runtime.tools.ToolMeta
import kotlin.reflect.KFunction

class Server private constructor() {
	val tools = mutableMapOf<String, GenericToolMeta>()

	class Builder {
		val builderTools = mutableListOf<GenericToolMeta>()

		inline fun <reified T : @Serializable Any, reified R : ToolContent> withTool(noinline toolHandler: T.() -> R): Builder {
			require(toolHandler is KFunction<*>) { "toolHandler must be a function" }
			builderTools +=
				ToolMeta(
					name = toolHandler.name,
					handler = toolHandler,
					paramsClass = T::class,
					resultClass = R::class,
					inputSchema = jsonSchema<T>(),
				)
			return this
		}

		fun build(): Server {
			val server =
				Server().apply {
					builderTools.forEach {
						addTool(it)
					}
				}
			return server
		}
	}

	fun addTool(tool: GenericToolMeta) {
		tools[tool.name] = tool
	}

	fun callTool(
		name: String,
		params: JsonElement,
	): CallToolResult {
		val tool: GenericToolMeta = tools[name] ?: error { "Tool not found: $name" }
		val paramInstance: Any = KMCP.json.decodeFromJsonElement(tool.paramsClass.serializer(), params)
		return CallToolResult(
			content = listOf(tool.call(paramInstance)),
		)
	}
}
