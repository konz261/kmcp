package sh.ondr.mcp4k.schema.core

import kotlinx.serialization.Polymorphic
import kotlinx.serialization.Serializable
import sh.ondr.mcp4k.runtime.core.JSON_RPC_VERSION

@Serializable
@Polymorphic
abstract class JsonRpcRequest : JsonRpcMessage {
	val jsonrpc: String = JSON_RPC_VERSION
	abstract val id: String
}
