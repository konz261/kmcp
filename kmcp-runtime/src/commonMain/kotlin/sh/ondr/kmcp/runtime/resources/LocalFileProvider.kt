@file:OptIn(ExperimentalEncodingApi::class)

package sh.ondr.kmcp.runtime.resources

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path
import okio.buffer
import okio.use
import sh.ondr.kmcp.schema.resources.Resource
import sh.ondr.kmcp.schema.resources.ResourceContents
import sh.ondr.kmcp.schema.resources.ResourceTemplate
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * A LocalFileProvider that can operate in two modes:
 * 1) Files mode: The provider returns a list of known files from a given rootDir.
 * 2) Template mode: The provider returns a template and lets the client read arbitrary files
 *    by interpreting the requested URI as a relative path under rootDir.
 *
 * @param fileSystem The file system to use for reading files. Typically FileSystem.SYSTEM.
 * @param rootDir The root directory within which files are read.
 * @param fileProviderMode Either [LocalFileProviderMode.DISCRETE] or [LocalFileProviderMode.TEMPLATE].
 * @param knownFiles A list of relative paths (relative to rootDir) that should be listed as resources in files mode.
 */
class LocalFileProvider(
	private val fileSystem: FileSystem,
	private val rootDir: Path,
	private val fileProviderMode: LocalFileProviderMode = LocalFileProviderMode.DISCRETE,
	knownFiles: List<String> = emptyList(),
) : ResourceProvider {
	// Callbacks set by the Server. Initially no-ops.
	override var onResourceChange: suspend (uri: String) -> Unit = {}
	override var onResourcesListChanged: suspend () -> Unit = {}

	/**
	 * We store knownFiles in a mutable list so we can dynamically add/remove resources.
	 * For now, these are just relative paths representing files under the rootDir.
	 */
	private val files = knownFiles.toMutableList()

	override suspend fun listResources(): List<Resource> {
		return when (fileProviderMode) {
			LocalFileProviderMode.DISCRETE -> {
				files.map { relativePath ->
					val fullPath = rootDir.resolve(relativePath)
					val name = fullPath.name
					Resource(
						uri = "file://$relativePath",
						name = name,
						description = "A file at $relativePath",
						mimeType = guessMimeType(name),
					)
				}
			}

			LocalFileProviderMode.TEMPLATE -> emptyList()
		}
	}

	override suspend fun listResourceTemplates(): List<ResourceTemplate> {
		return when (fileProviderMode) {
			LocalFileProviderMode.DISCRETE -> emptyList()
			LocalFileProviderMode.TEMPLATE -> {
				listOf(
					ResourceTemplate(
						uriTemplate = "file:///{path}",
						name = "Arbitrary file access",
						description = "Access any file by specifying a path relative to root directory",
						mimeType = null,
						annotations = null,
					),
				)
			}
		}
	}

	override suspend fun readResource(uri: String): ResourceContents? =
		withContext(Dispatchers.Default) {
			// Expecting uri like "file://somepath"
			if (!uri.startsWith("file://")) return@withContext null
			val relativePath = uri.removePrefix("file://")
			if (relativePath.isEmpty()) return@withContext null

			val fullPath = rootDir.resolve(relativePath)

			// Check if the file exists
			if (!fileSystem.exists(fullPath)) return@withContext null
			if (fileSystem.metadata(fullPath).isDirectory) {
				// For directories, we might return null or handle specially
				return@withContext null
			}

			// Read the file
			val source = fileSystem.source(fullPath)
			val data = source.buffer().use { it.readByteArray() }

			// Get mime type
			val mimeType = guessMimeType(relativePath = relativePath)

			if (mimeType.startsWith("text")) {
				try {
					return@withContext ResourceContents.Text(
						uri = uri,
						mimeType = mimeType,
						text = data.decodeToString(),
					)
				} catch (e: Throwable) {
					null // just continue for now
				}
			}
			return@withContext ResourceContents.Blob(
				uri = uri,
				mimeType = "application/octet-stream",
				blob = Base64.encode(data),
			)
		}

	suspend fun addFile(relativePath: String) {
		if (!files.contains(relativePath)) {
			files += relativePath
			onResourcesListChanged() // Trigger callback
		}
	}

	suspend fun removeFile(relativePath: String) {
		if (files.remove(relativePath)) {
			onResourcesListChanged() // Trigger callback
		}
	}

	suspend fun notifyResourceUpdated(uri: String) {
		onResourceChange(uri)
	}

	// Just return text/plain for now
	private fun guessMimeType(relativePath: String): String = "text/plain"
}
