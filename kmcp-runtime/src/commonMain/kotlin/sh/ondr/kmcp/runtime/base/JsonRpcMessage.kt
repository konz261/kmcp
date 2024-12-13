package sh.ondr.kmcp.runtime.base

import kotlinx.serialization.Serializable
import sh.ondr.kmcp.runtime.KMCP

@Serializable
sealed class JsonRpcMessage(val jsonrpc: String = KMCP.JSON_RPC_VERSION)
