package sh.ondr.kmcp.runtime.resources

import sh.ondr.kmcp.schema.resources.Resource
import sh.ondr.kmcp.schema.resources.ResourceContents
import sh.ondr.kmcp.schema.resources.ResourceTemplate

interface ResourceProvider {
	var onResourceChange: suspend (uri: String) -> Unit
	var onResourcesListChanged: suspend () -> Unit

	suspend fun listResources(): List<Resource>

	suspend fun readResource(uri: String): ResourceContents?

	suspend fun listResourceTemplates(): List<ResourceTemplate>
}
