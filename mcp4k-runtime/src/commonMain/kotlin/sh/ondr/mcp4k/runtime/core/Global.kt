package sh.ondr.mcp4k.runtime.core

import kotlinx.serialization.json.Json
import sh.ondr.mcp4k.runtime.prompts.McpPromptHandler
import sh.ondr.mcp4k.runtime.serialization.mcp4kSerializersModule
import sh.ondr.mcp4k.runtime.tools.McpToolHandler
import kotlin.reflect.KClass

const val JSON_RPC_VERSION = "2.0"
const val MCP_VERSION = "2024-11-05"

val mcpJson = Json {
	encodeDefaults = true
	explicitNulls = false
	isLenient = true
	ignoreUnknownKeys = true
	classDiscriminator = "method"
	serializersModule = mcp4kSerializersModule
}

val mcpToolParams = mutableMapOf<String, KClass<*>>()
val mcpToolHandlers = mutableMapOf<String, McpToolHandler>()
val mcpPromptParams = mutableMapOf<String, KClass<*>>()
val mcpPromptHandlers = mutableMapOf<String, McpPromptHandler>()
