package sh.ondr.kmcp.schema.messages.content

import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import sh.ondr.kmcp.runtime.KMCP
import sh.ondr.kmcp.schema.content.Content
import sh.ondr.kmcp.schema.content.EmbeddedResourceContent
import sh.ondr.kmcp.schema.content.ImageContent
import sh.ondr.kmcp.schema.content.TextContent
import sh.ondr.kmcp.schema.core.Annotations
import sh.ondr.kmcp.schema.core.Role
import sh.ondr.kmcp.schema.resources.ResourceContents
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SimpleDeserializationTest {
	@Test
	fun textContentRoundTrip() {
		val original = TextContent(text = "Hello, world!")
		val json = KMCP.json.encodeToJsonElement<Content>(original)

		val jsonObject = json.jsonObject
		assertEquals("text", jsonObject["type"]?.jsonPrimitive?.content)

		val deserialized = KMCP.json.decodeFromJsonElement<Content>(json)
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
		val json = KMCP.json.encodeToJsonElement<Content>(original)
		val jsonObject = json.jsonObject
		assertEquals("image", jsonObject["type"]?.jsonPrimitive?.content)

		val deserialized = KMCP.json.decodeFromJsonElement<Content>(json)
		assertTrue(deserialized is ImageContent)
		assertEquals(original.data, deserialized.data)
		assertEquals(original.mimeType, deserialized.mimeType)
		assertEquals(original.annotations, deserialized.annotations)
	}

	@Test
	fun ensurePolymorphicBehaviorOnlyWhenSerializedAsBaseType() {
		val textOnly = TextContent("Just text")
		val directJson = KMCP.json.encodeToJsonElement(textOnly)
		assertEquals(null, directJson.jsonObject["type"])

		val polyJson = KMCP.json.encodeToJsonElement<Content>(textOnly)
		assertEquals("text", polyJson.jsonObject["type"]?.jsonPrimitive?.content)
	}

	@Test
	fun annotationsRoundTrip() {
		val original =
			TextContent(
				text = "Annotated text",
				annotations = Annotations(audience = listOf(Role.USER, Role.ASSISTANT), priority = 0.5),
			)

		val json = KMCP.json.encodeToJsonElement<Content>(original)
		val deserialized = KMCP.json.decodeFromJsonElement<Content>(json)
		assertTrue(deserialized is TextContent)
		assertNotNull(deserialized.annotations)
		assertEquals(listOf(Role.USER, Role.ASSISTANT), deserialized.annotations.audience)
		assertEquals(0.5, deserialized.annotations.priority)
	}

	@Test
	fun annotationsNoAudience() {
		// Only priority is set, audience is null
		val original =
			TextContent(
				text = "Only priority",
				annotations = Annotations(priority = 1.0),
			)
		val json = KMCP.json.encodeToJsonElement<Content>(original)
		val deserialized = KMCP.json.decodeFromJsonElement<Content>(json)
		assertTrue(deserialized is TextContent)
		assertNotNull(deserialized.annotations)
		assertNull(deserialized.annotations.audience)
		assertEquals(1.0, deserialized.annotations.priority)
	}

	@Test
	fun annotationsNoPriority() {
		// Only audience is set
		val original =
			TextContent(
				text = "Only audience",
				annotations = Annotations(audience = listOf(Role.ASSISTANT)),
			)
		val json = KMCP.json.encodeToJsonElement<Content>(original)
		val deserialized = KMCP.json.decodeFromJsonElement<Content>(json)
		assertTrue(deserialized is TextContent)
		assertNotNull(deserialized.annotations)
		assertEquals(listOf(Role.ASSISTANT), deserialized.annotations.audience)
		assertNull(deserialized.annotations.priority)
	}

	@Test
	fun annotationsMissingEntirely() {
		// No annotations at all
		val original = TextContent(text = "No annotations")
		val json = KMCP.json.encodeToJsonElement<Content>(original)

		// Check JSON does not have 'annotations' field
		val jsonObject = json.jsonObject
		assertNull(jsonObject["annotations"])

		val deserialized = KMCP.json.decodeFromJsonElement<Content>(json)
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

		val json = KMCP.json.encodeToJsonElement<Content>(original)
		val jsonObject = json.jsonObject
		assertEquals("resource", jsonObject["type"]?.jsonPrimitive?.content)

		val deserialized = KMCP.json.decodeFromJsonElement<Content>(json)
		assertTrue(deserialized is EmbeddedResourceContent)
		assertTrue(deserialized.resource is ResourceContents.Text)
		val textResource = deserialized.resource
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

		val json = KMCP.json.encodeToJsonElement<Content>(original)
		val deserialized = KMCP.json.decodeFromJsonElement<Content>(json)
		assertTrue(deserialized is EmbeddedResourceContent)
		assertTrue(deserialized.resource is ResourceContents.Blob)
		val blobResource = deserialized.resource
		assertEquals("file://image.png", blobResource.uri)
		assertEquals("base64imgdata", blobResource.blob)
		assertEquals("image/png", blobResource.mimeType)

		assertNotNull(deserialized.annotations)
		assertEquals(listOf(Role.USER), deserialized.annotations.audience)
		assertEquals(0.9, deserialized.annotations.priority)
	}

	@Test
	fun imageContentNoAnnotations() {
		val original =
			ImageContent(
				data = "base64img",
				mimeType = "image/jpeg",
			)
		val json = KMCP.json.encodeToJsonElement<Content>(original)
		val jsonObject = json.jsonObject
		assertNull(jsonObject["annotations"])

		val deserialized = KMCP.json.decodeFromJsonElement<Content>(json)
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

		val json = KMCP.json.encodeToJsonElement<Content>(original)
		val deserialized = KMCP.json.decodeFromJsonElement<Content>(json)
		assertTrue(deserialized is TextContent)
		assertNotNull(deserialized.annotations)
		// audience is empty
		assertEquals(listOf(), deserialized.annotations.audience)
		assertNull(deserialized.annotations.priority)
	}
}
