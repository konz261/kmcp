package sh.ondr.kmcp.runtime

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

inline fun <reified T : @Serializable Any> T.toJsonObject(): JsonObject {
	return KMCP.json.encodeToJsonElement(this).jsonObject
}
