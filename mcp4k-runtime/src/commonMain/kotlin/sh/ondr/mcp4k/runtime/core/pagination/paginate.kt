@file:OptIn(ExperimentalEncodingApi::class)

package sh.ondr.mcp4k.runtime.core.pagination

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Returns a sub-list of [items] plus a `nextCursor` if there's another page.
 */
fun <T> paginate(
	items: List<T>,
	cursor: String?,
	pageSize: Int,
): Pair<List<T>, String?> {
	val (page, decodedPageSize) = decodeCursor(cursor)
	val effectivePageSize = decodedPageSize ?: pageSize

	val fromIndex = page * effectivePageSize
	if (fromIndex >= items.size) {
		return emptyList<T>() to null
	}

	val toIndex = minOf(fromIndex + effectivePageSize, items.size)
	val slice = items.subList(fromIndex, toIndex)

	val newCursor = if (toIndex < items.size) {
		encodeCursor(page + 1, effectivePageSize)
	} else {
		null
	}

	return slice to newCursor
}

private fun decodeCursor(cursor: String?): Pair<Int, Int?> {
	if (cursor == null) return 0 to null
	try {
		val jsonStr = Base64.decode(cursor).decodeToString()
		val obj = Json.parseToJsonElement(jsonStr).jsonObject

		val page = obj["page"]?.jsonPrimitive?.int ?: 0
		val pageSize = obj["pageSize"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
		return page to pageSize
	} catch (e: Throwable) {
		// Should respond with JsonRpcErrorCodes.INVALID_PARAMS in your handler
		throw IllegalArgumentException("Invalid cursor: $cursor", e)
	}
}

private fun encodeCursor(
	page: Int,
	pageSize: Int,
): String {
	val obj = buildJsonObject {
		put("page", page)
		put("pageSize", pageSize)
	}
	val jsonStr = Json.encodeToString(JsonObject.serializer(), obj)
	return Base64.encode(jsonStr.encodeToByteArray())
}
