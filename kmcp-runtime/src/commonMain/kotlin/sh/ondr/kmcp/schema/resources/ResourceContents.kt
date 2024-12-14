package sh.ondr.kmcp.schema.resources

import kotlinx.serialization.Serializable

@Serializable
sealed class ResourceContents {
	abstract val uri: String
	abstract val mimeType: String?

	@Serializable
	data class Text(
		override val uri: String,
		override val mimeType: String? = null,
		val text: String,
	) : ResourceContents()

	@Serializable
	data class Blob(
		override val uri: String,
		override val mimeType: String? = null,
		val blob: String,
	) : ResourceContents()
}
