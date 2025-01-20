package sh.ondr.mcp4k.runtime.serialization

import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import sh.ondr.mcp4k.schema.capabilities.InitializeRequest
import sh.ondr.mcp4k.schema.capabilities.InitializedNotification
import sh.ondr.mcp4k.schema.completion.CompleteRequest
import sh.ondr.mcp4k.schema.core.CancelledNotification
import sh.ondr.mcp4k.schema.core.JsonRpcNotification
import sh.ondr.mcp4k.schema.core.JsonRpcRequest
import sh.ondr.mcp4k.schema.core.PingRequest
import sh.ondr.mcp4k.schema.core.ProgressNotification
import sh.ondr.mcp4k.schema.logging.LoggingMessageNotification
import sh.ondr.mcp4k.schema.logging.SetLoggingLevelRequest
import sh.ondr.mcp4k.schema.prompts.GetPromptRequest
import sh.ondr.mcp4k.schema.prompts.ListPromptsRequest
import sh.ondr.mcp4k.schema.prompts.PromptListChangedNotification
import sh.ondr.mcp4k.schema.resources.ListResourceTemplatesRequest
import sh.ondr.mcp4k.schema.resources.ListResourcesRequest
import sh.ondr.mcp4k.schema.resources.ReadResourceRequest
import sh.ondr.mcp4k.schema.resources.ResourceListChangedNotification
import sh.ondr.mcp4k.schema.resources.ResourceUpdatedNotification
import sh.ondr.mcp4k.schema.resources.SubscribeRequest
import sh.ondr.mcp4k.schema.resources.UnsubscribeRequest
import sh.ondr.mcp4k.schema.roots.ListRootsRequest
import sh.ondr.mcp4k.schema.roots.RootsListChangedNotification
import sh.ondr.mcp4k.schema.sampling.CreateMessageRequest
import sh.ondr.mcp4k.schema.tools.CallToolRequest
import sh.ondr.mcp4k.schema.tools.ListToolsRequest
import sh.ondr.mcp4k.schema.tools.ToolListChangedNotification

val mcp4kSerializersModule = SerializersModule {
	// Register all known request subclasses
	polymorphic(JsonRpcRequest::class) {
		subclass(CallToolRequest::class)
		subclass(GetPromptRequest::class)
		subclass(InitializeRequest::class)
		subclass(PingRequest::class)
		subclass(ListPromptsRequest::class)
		subclass(ListResourcesRequest::class)
		subclass(ListResourceTemplatesRequest::class)
		subclass(ReadResourceRequest::class)
		subclass(SubscribeRequest::class)
		subclass(UnsubscribeRequest::class)
		subclass(ListToolsRequest::class)
		subclass(CompleteRequest::class)
		subclass(CreateMessageRequest::class)
		subclass(SetLoggingLevelRequest::class)
		subclass(ListRootsRequest::class)
	}

	// Register all known notification subclasses
	polymorphic(JsonRpcNotification::class) {
		subclass(CancelledNotification::class)
		subclass(InitializedNotification::class)
		subclass(ProgressNotification::class)
		subclass(ResourceListChangedNotification::class)
		subclass(ResourceUpdatedNotification::class)
		subclass(PromptListChangedNotification::class)
		subclass(ToolListChangedNotification::class)
		subclass(LoggingMessageNotification::class)
		subclass(RootsListChangedNotification::class)
	}
}
