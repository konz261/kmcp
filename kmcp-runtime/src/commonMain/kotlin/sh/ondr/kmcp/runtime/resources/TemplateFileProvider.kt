@file:OptIn(ExperimentalEncodingApi::class)

package sh.ondr.kmcp.runtime.resources

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.buffer
import okio.use
import sh.ondr.kmcp.schema.resources.Resource
import sh.ondr.kmcp.schema.resources.ResourceContents
import sh.ondr.kmcp.schema.resources.ResourceTemplate
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

// TODO Allow dynamic configuration

/**
 * A [ResourceProvider] that exposes a URI template, allowing arbitrary file paths
 * under [rootDir].
 *
 * For example, it might advertise a simple level 1 resource template like `file:///{path}`
 *
 * Example usage:
 * ```
 * val templateProvider = TemplateFileProvider(
 *     fileSystem = FileSystem.SYSTEM,
 *     rootDir = "/some/local/folder".toPath(),
 *     template = "file:///{path}",
 * )
 * ```
 *
 * Then the client can read resources such as `file:///sub/folder/example.txt`.
 */
class TemplateFileProvider(
	private val fileSystem: FileSystem,
	private val rootDir: Path,
	private val template: String,
	private val name: String = "Arbitrary local file access",
	private val description: String = "Allows reading any file under $rootDir by specifying {path}",
	private val mimeTypeDetector: MimeTypeDetector = MimeTypeDetector {
		"text/plain"
	},
) : ResourceProvider() {
	override val supportsSubscriptions: Boolean = true

	/**
	 * Returns the resource template defined in the constructor
	 */
	override suspend fun listResourceTemplates(): List<ResourceTemplate> {
		return listOf(
			ResourceTemplate(
				uriTemplate = template,
				name = name,
				description = description,
			),
		)
	}

	/**
	 * We have no discrete listing of resources; everything is driven by the template,
	 * so [listResources] returns empty by default.
	 */
	override suspend fun listResources(): List<Resource> = emptyList()

	/**
	 * Given a 'file:///{path}', we parse the actual path from the URI.
	 * Then we do a minimal check (normalize & trivial sandbox check) and read the file from disk if it exists.
	 */
	override suspend fun readResource(uri: String): ResourceContents? =
		withContext(Dispatchers.Default) {
			// Expect URIs like "file:///something"
			if (!uri.startsWith("file:///")) return@withContext null

			// 1) Extract the portion after "file:///"
			val pathPart = uri.removePrefix("file:///")

			// 2) Convert that substring to a Path and then resolve under rootDir.
			// 'normalize` will drop any '.' segments etc.
			val requestedPath = pathPart.toPath(normalize = true)
			val resolved = rootDir.resolve(requestedPath, normalize = true)

			// 3) Check if resolved is physically inside rootDir:
			val subPath = try {
				resolved.relativeTo(rootDir)
			} catch (iae: IllegalArgumentException) {
				// Means resolved is on a different root or otherwise can't be relative to rootDir
				// TODO Maybe throw
				return@withContext null
			}

			// If subPath has any '..' segments, we might be breaking out of rootDir
			if (".." in subPath.segments) {
				return@withContext null
			}

			// 4) Now that we've confirmed it's inside rootDir, verify that it exists and is not a directory
			if (!fileSystem.exists(resolved)) return@withContext null
			if (fileSystem.metadata(resolved).isDirectory) return@withContext null

			// 5) Read file contents
			val data = fileSystem.source(resolved).buffer().use { it.readByteArray() }

			// 6) Handle depending on MIME type
			val mimeType = mimeTypeDetector.detect(resolved.name)
			if (mimeType.startsWith("text")) {
				val text = runCatching { data.decodeToString() }.getOrNull() ?: return@withContext null
				ResourceContents.Text(
					uri = uri,
					mimeType = mimeType,
					text = text,
				)
			} else {
				ResourceContents.Blob(
					uri = uri,
					mimeType = mimeType,
					blob = Base64.encode(data),
				)
			}
		}
}
