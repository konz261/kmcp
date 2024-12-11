package sh.ondr.kmcp.runtime.tools

import kotlinx.serialization.json.JsonElement

interface ToolHandler {
	fun invoke(params: JsonElement?): Any?
}
