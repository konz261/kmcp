package sh.ondr.kmcp.runtime.core

import sh.ondr.kmcp.schema.core.JsonRpcError
import sh.ondr.kmcp.schema.core.JsonRpcRequest
import sh.ondr.kmcp.schema.core.JsonRpcResponse

/**
 * Extension to create an error response.
 */
fun JsonRpcRequest.returnErrorResponse(
	message: String,
	code: Int,
): JsonRpcResponse {
	return JsonRpcResponse(
		id = id,
		error = JsonRpcError(code, message),
	)
}

fun notImplemented(): Nothing = throw NotImplementedError("Not implemented.")
