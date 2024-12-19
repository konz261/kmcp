package sh.ondr.kmcp.runtime

import kotlinx.serialization.json.Json
import sh.ondr.kmcp.runtime.prompts.PromptHandler
import sh.ondr.kmcp.runtime.serialization.module
import sh.ondr.kmcp.runtime.tools.ToolHandler
import sh.ondr.kmcp.schema.prompts.PromptInfo
import sh.ondr.kmcp.schema.tools.ToolInfo

const val JSON_RPC_VERSION = "2.0"
const val MCP_VERSION = "2024-11-05"
val kmcpJson = Json {
	encodeDefaults = true
	explicitNulls = false
	isLenient = true
	classDiscriminator = "method"
	serializersModule = module
}

object KMCP {
	val toolInfos = mutableMapOf<String, ToolInfo>()
	val toolHandlers = mutableMapOf<String, ToolHandler>()
	val promptInfos = mutableMapOf<String, PromptInfo>()
	val promptHandlers = mutableMapOf<String, PromptHandler>()
}
