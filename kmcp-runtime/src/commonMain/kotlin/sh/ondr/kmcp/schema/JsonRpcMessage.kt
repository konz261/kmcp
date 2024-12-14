package sh.ondr.kmcp.schema

import kotlinx.serialization.Serializable
import sh.ondr.kmcp.runtime.KMCP

@Serializable
abstract class JsonRpcMessage(val jsonrpc: String = KMCP.JSON_RPC_VERSION)
