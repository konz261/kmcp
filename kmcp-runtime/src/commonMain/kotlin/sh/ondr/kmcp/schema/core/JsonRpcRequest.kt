package sh.ondr.kmcp.schema.core

import kotlinx.serialization.Polymorphic
import kotlinx.serialization.Serializable
import sh.ondr.kmcp.runtime.KMCP

@Serializable
@Polymorphic
abstract class JsonRpcRequest : JsonRpcMessage {
	val jsonrpc: String = KMCP.JSON_RPC_VERSION
	abstract val id: String
}
