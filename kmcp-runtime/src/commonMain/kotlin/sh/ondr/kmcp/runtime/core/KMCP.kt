package sh.ondr.kmcp.runtime.core

import kotlinx.serialization.json.Json
import sh.ondr.kmcp.runtime.prompts.PromptHandler
import sh.ondr.kmcp.runtime.serialization.kmcpSerializersModule
import sh.ondr.kmcp.runtime.tools.ToolHandler
import sh.ondr.kmcp.schema.prompts.PromptInfo
import sh.ondr.kmcp.schema.tools.ToolInfo

const val JSON_RPC_VERSION = "2.0"
const val MCP_VERSION = "2024-11-05"

val mcpJson = Json {
	encodeDefaults = true
	explicitNulls = false
	isLenient = true
	ignoreUnknownKeys = true
	classDiscriminator = "method"
	serializersModule = kmcpSerializersModule
}

val mcpToolInfos = mutableMapOf<String, ToolInfo>()
val mcpToolHandlers = mutableMapOf<String, ToolHandler>()
val mcpPromptInfos = mutableMapOf<String, PromptInfo>()
val mcpPromptHandlers = mutableMapOf<String, PromptHandler>()
