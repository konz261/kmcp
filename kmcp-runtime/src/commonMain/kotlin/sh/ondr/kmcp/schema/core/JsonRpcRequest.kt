package sh.ondr.kmcp.schema.core

import kotlinx.serialization.Polymorphic
import kotlinx.serialization.Serializable
import sh.ondr.kmcp.runtime.core.JSON_RPC_VERSION

@Serializable
@Polymorphic
abstract class JsonRpcRequest : JsonRpcMessage {
	val jsonrpc: String = JSON_RPC_VERSION
	abstract val id: String
}
