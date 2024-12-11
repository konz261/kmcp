package sh.ondr.kmcp.runtime

import kotlinx.serialization.json.JsonElement
import sh.ondr.kmcp.runtime.tools.Tool
import sh.ondr.kmcp.runtime.tools.ToolHandler
import kotlin.reflect.KFunction

class Server private constructor() {
	private val tools = mutableMapOf<String, Tool>()
	private val toolHandlers = mutableMapOf<String, ToolHandler>()

	class Builder {
		private val functions = mutableListOf<KFunction<*>>()

		fun withTool(block: KFunction<*>): Builder {
			functions.add(block)
			return this
		}

		fun withTools(vararg blocks: KFunction<*>): Builder {
			functions.addAll(blocks)
			return this
		}

		fun build(): Server {
			val server = Server()
			functions.forEach { server.addTool(it) }
			return server
		}
	}

	fun addTool(block: KFunction<*>) {
		val name = block.name
		val tool = KMCP.ToolRegistry.tools[name]
		val handler = KMCP.ToolRegistry.handlers[name]

		if (tool != null && handler != null) {
			println("Adding tool: $name")
			tools[name] = tool
			toolHandlers[name] = handler
		} else {
			println("Tool not found: $name")
		}
	}

	fun callTool(
		name: String,
		params: JsonElement,
	): Any? {
		val handler =
			toolHandlers[name] ?: run {
				println("No handler found for tool: $name")
				return null
			}
		return handler.invoke(params)
	}
}
