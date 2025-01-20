package sh.ondr.mcp4k.schema.core

object JsonRpcErrorCodes {
	// Standard JSON-RPC error codes
	const val PARSE_ERROR = -32700 // Invalid JSON was received
	const val INVALID_REQUEST = -32600 // JSON is not a valid Request object
	const val METHOD_NOT_FOUND = -32601 // Method does not exist or is not available
	const val INVALID_PARAMS = -32602 // Invalid method parameter(s)
	const val INTERNAL_ERROR = -32603 // Internal JSON-RPC error

	// MCP-specific error codes
	const val RESOURCE_NOT_FOUND = -32002 // Returned when a requested resource doesn't exist
}
