package sh.ondr.kmcp.runtime.serialization

import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import sh.ondr.kmcp.schema.resources.ResourceContents

object ResourceContentsSerializer : JsonContentPolymorphicSerializer<ResourceContents>(ResourceContents::class) {
	override fun selectDeserializer(element: JsonElement) =
		when {
			element.jsonObject.containsKey("blob") -> ResourceContents.Blob.serializer()
			element.jsonObject.containsKey("text") -> ResourceContents.Text.serializer()
			else -> error("Could not deserialize ResourceContents: neither 'blob' nor 'text' present.")
		}
}
