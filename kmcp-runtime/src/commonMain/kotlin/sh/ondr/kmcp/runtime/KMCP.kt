package sh.ondr.kmcp.runtime

import kotlinx.serialization.json.Json
import sh.ondr.kmcp.runtime.tools.Tool
import sh.ondr.kmcp.runtime.tools.ToolHandler

object KMCP {
	val json =
		Json {
			encodeDefaults = true
			explicitNulls = false
			isLenient = true
		}

	object ToolRegistry {
		val tools = mutableMapOf<String, Tool>()
		val handlers = mutableMapOf<String, ToolHandler>()
		val defaultValues = mutableMapOf<String, Map<String, Any>>()
	}
}
