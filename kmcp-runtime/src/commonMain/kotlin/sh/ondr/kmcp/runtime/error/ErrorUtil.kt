@file:OptIn(ExperimentalSerializationApi::class)

package sh.ondr.kmcp.runtime.error

import kotlinx.serialization.ExperimentalSerializationApi
import sh.ondr.kmcp.schema.core.JsonRpcErrorCodes

internal fun determineErrorResponse(
	method: String?,
	e: Throwable,
): Pair<Int, String> {
	val methodSuffix = if (method != null) " in $method request" else ""
	return when (e) {
		is kotlinx.serialization.MissingFieldException -> {
			val msg = if (e.missingFields.size == 1) {
				"Missing required field '${e.missingFields.single()}'$methodSuffix"
			} else {
				"Missing required fields$methodSuffix: [${e.missingFields.joinToString()}]"
			}
			JsonRpcErrorCodes.INVALID_PARAMS to msg
		}

		// Without id handling here, just return a generic error
		// If parsing failed at a low level, `id` is null and we won't respond anyway.
		is kotlinx.serialization.SerializationException -> {
			JsonRpcErrorCodes.INVALID_PARAMS to "Invalid parameters$methodSuffix."
		}

		is UnknownArgumentException -> {
			JsonRpcErrorCodes.INVALID_PARAMS to (e.message ?: "Invalid parameters$methodSuffix.")
		}

		is MethodNotFoundException -> {
			JsonRpcErrorCodes.METHOD_NOT_FOUND to (e.message ?: "Method not found$methodSuffix")
		}

		is MissingRequiredArgumentException -> {
			JsonRpcErrorCodes.INVALID_PARAMS to (e.message ?: "Invalid parameters$methodSuffix.")
		}

		else -> {
			// Everything else is an internal error or parse error if no id
			// Without id or parse checking, just return internal error message.
			JsonRpcErrorCodes.INTERNAL_ERROR to "Internal error: ${e.message ?: "unknown"}"
		}
	}
}
