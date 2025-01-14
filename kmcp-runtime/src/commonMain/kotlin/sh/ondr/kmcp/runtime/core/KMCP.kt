package sh.ondr.kmcp.runtime.core

import kotlinx.serialization.json.Json
import sh.ondr.kmcp.runtime.prompts.PromptHandler
import sh.ondr.kmcp.runtime.serialization.kmcpSerializersModule
import sh.ondr.kmcp.runtime.tools.McpToolHandler
import kotlin.reflect.KClass

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

val mcpToolParams = mutableMapOf<String, KClass<*>>()
val mcpToolHandlers = mutableMapOf<String, McpToolHandler>()
val mcpPromptParams = mutableMapOf<String, KClass<*>>()
val mcpPromptHandlers = mutableMapOf<String, PromptHandler>()
