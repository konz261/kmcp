package sh.ondr.mcp4k.test.schema.messages.content

import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import sh.ondr.mcp4k.runtime.core.mcpJson
import sh.ondr.mcp4k.schema.content.Content
import sh.ondr.mcp4k.schema.content.EmbeddedResourceContent
import sh.ondr.mcp4k.schema.content.ImageContent
import sh.ondr.mcp4k.schema.content.TextContent
import sh.ondr.mcp4k.schema.core.Annotations
import sh.ondr.mcp4k.schema.core.Role
import sh.ondr.mcp4k.schema.resources.ResourceContents
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SimpleDeserializationTest {
	@Test
	fun textContentRoundTrip() {
		val original = TextContent(text = "Hello, world!")
		val json = mcpJson.encodeToJsonElement<Content>(original)

		val jsonObject = json.jsonObject
		assertEquals("text", jsonObject["type"]?.jsonPrimitive?.content)

		val deserialized = mcpJson.decodeFromJsonElement<Content>(json)
		assertTrue(deserialized is TextContent)
		assertEquals(original.text, deserialized.text)
		assertEquals(original.annotations, deserialized.annotations)
		assertNull(deserialized.annotations)
	}

	@Test
	fun imageContentRoundTrip() {
		val original =
			ImageContent(
				data = "base64encodeddata",
				mimeType = "image/png",
				annotations = Annotations(audience = listOf(Role.USER)),
			)
		val json = mcpJson.encodeToJsonElement<Content>(original)
		val jsonObject = json.jsonObject
		assertEquals("image", jsonObject["type"]?.jsonPrimitive?.content)

		val deserialized = mcpJson.decodeFromJsonElement<Content>(json)
		assertTrue(deserialized is ImageContent)
		assertEquals(original.data, deserialized.data)
		assertEquals(original.mimeType, deserialized.mimeType)
		assertEquals(original.annotations, deserialized.annotations)
	}

	@Test
	fun ensurePolymorphicBehaviorOnlyWhenSerializedAsBaseType() {
		val textOnly = TextContent("Just text")
		val directJson = mcpJson.encodeToJsonElement(textOnly)
		assertEquals(null, directJson.jsonObject["type"])

		val polyJson = mcpJson.encodeToJsonElement<Content>(textOnly)
		assertEquals("text", polyJson.jsonObject["type"]?.jsonPrimitive?.content)
	}

	@Test
	fun annotationsRoundTrip() {
		val original =
			TextContent(
				text = "Annotated text",
				annotations = Annotations(audience = listOf(Role.USER, Role.ASSISTANT), priority = 0.5),
			)

		val json = mcpJson.encodeToJsonElement<Content>(original)
		val deserialized = mcpJson.decodeFromJsonElement<Content>(json)
		assertTrue(deserialized is TextContent)
		assertNotNull(deserialized.annotations)
		val annotations = deserialized.annotations
		assertNotNull(annotations)
		assertEquals(listOf(Role.USER, Role.ASSISTANT), annotations.audience)
		assertEquals(0.5, annotations.priority)
	}

	@Test
	fun annotationsNoAudience() {
		// Only priority is set, audience is null
		val original =
			TextContent(
				text = "Only priority",
				annotations = Annotations(priority = 1.0),
			)
		val json = mcpJson.encodeToJsonElement<Content>(original)
		val deserialized = mcpJson.decodeFromJsonElement<Content>(json)
		assertTrue(deserialized is TextContent)
		assertNotNull(deserialized.annotations)
		val annotations = deserialized.annotations
		assertNotNull(annotations)
		assertNull(annotations.audience)
		assertEquals(1.0, annotations.priority)
	}

	@Test
	fun annotationsNoPriority() {
		// Only audience is set
		val original =
			TextContent(
				text = "Only audience",
				annotations = Annotations(audience = listOf(Role.ASSISTANT)),
			)
		val json = mcpJson.encodeToJsonElement<Content>(original)
		val deserialized = mcpJson.decodeFromJsonElement<Content>(json)
		assertTrue(deserialized is TextContent)
		assertNotNull(deserialized.annotations)
		val annotations = deserialized.annotations
		assertNotNull(annotations)
		assertEquals(listOf(Role.ASSISTANT), annotations.audience)
		assertNull(annotations.priority)
	}

	@Test
	fun annotationsMissingEntirely() {
		// No annotations at all
		val original = TextContent(text = "No annotations")
		val json = mcpJson.encodeToJsonElement<Content>(original)

		// Check JSON does not have 'annotations' field
		val jsonObject = json.jsonObject
		assertNull(jsonObject["annotations"])

		val deserialized = mcpJson.decodeFromJsonElement<Content>(json)
		assertTrue(deserialized is TextContent)
		assertNull(deserialized.annotations)
	}

	@Test
	fun embeddedResourceContentRoundTrip() {
		val resource =
			ResourceContents.Text(
				uri = "file://example.txt",
				text = "Resource text",
				mimeType = "text/plain",
			)
		val original = EmbeddedResourceContent(resource = resource)

		val json = mcpJson.encodeToJsonElement<Content>(original)
		val jsonObject = json.jsonObject
		assertEquals("resource", jsonObject["type"]?.jsonPrimitive?.content)

		val deserialized = mcpJson.decodeFromJsonElement<Content>(json)
		assertTrue(deserialized is EmbeddedResourceContent)
		assertTrue(deserialized.resource is ResourceContents.Text)
		val textResource = deserialized.resource as ResourceContents.Text
		assertEquals("file://example.txt", textResource.uri)
		assertEquals("Resource text", textResource.text)
		assertEquals("text/plain", textResource.mimeType)
		assertNull(deserialized.annotations)
	}

	@Test
	fun embeddedResourceContentWithAnnotations() {
		val resource =
			ResourceContents.Blob(
				uri = "file://image.png",
				blob = "base64imgdata",
				mimeType = "image/png",
			)
		val original =
			EmbeddedResourceContent(
				resource = resource,
				annotations = Annotations(audience = listOf(Role.USER), priority = 0.9),
			)

		val json = mcpJson.encodeToJsonElement<Content>(original)
		val deserialized = mcpJson.decodeFromJsonElement<Content>(json)
		assertTrue(deserialized is EmbeddedResourceContent)
		assertTrue(deserialized.resource is ResourceContents.Blob)
		val blobResource = deserialized.resource as ResourceContents.Blob
		assertEquals("file://image.png", blobResource.uri)
		assertEquals("base64imgdata", blobResource.blob)
		assertEquals("image/png", blobResource.mimeType)

		assertNotNull(deserialized.annotations)
		val annotations = deserialized.annotations
		assertNotNull(annotations)
		assertEquals(listOf(Role.USER), annotations.audience)
		assertEquals(0.9, annotations.priority)
	}

	@Test
	fun imageContentNoAnnotations() {
		val original =
			ImageContent(
				data = "base64img",
				mimeType = "image/jpeg",
			)
		val json = mcpJson.encodeToJsonElement<Content>(original)
		val jsonObject = json.jsonObject
		assertNull(jsonObject["annotations"])

		val deserialized = mcpJson.decodeFromJsonElement<Content>(json)
		assertTrue(deserialized is ImageContent)
		assertEquals("base64img", deserialized.data)
		assertEquals("image/jpeg", deserialized.mimeType)
		assertNull(deserialized.annotations)
	}

	@Test
	fun textContentEmptyAudience() {
		// If audience is empty list, it should serialize as "audience":[]
		val original =
			TextContent(
				text = "Empty audience",
				annotations = Annotations(audience = emptyList()),
			)

		val json = mcpJson.encodeToJsonElement<Content>(original)
		val deserialized = mcpJson.decodeFromJsonElement<Content>(json)
		assertTrue(deserialized is TextContent)
		assertNotNull(deserialized.annotations)
		// audience is empty
		val annotations = deserialized.annotations
		assertNotNull(annotations)
		assertEquals(listOf(), annotations.audience)
		assertNull(annotations.priority)
	}
}
