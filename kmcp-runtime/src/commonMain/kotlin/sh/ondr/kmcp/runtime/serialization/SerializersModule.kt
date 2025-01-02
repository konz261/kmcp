package sh.ondr.kmcp.runtime.serialization

import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import sh.ondr.kmcp.schema.capabilities.InitializeRequest
import sh.ondr.kmcp.schema.capabilities.InitializedNotification
import sh.ondr.kmcp.schema.completion.CompleteRequest
import sh.ondr.kmcp.schema.core.CancelledNotification
import sh.ondr.kmcp.schema.core.JsonRpcNotification
import sh.ondr.kmcp.schema.core.JsonRpcRequest
import sh.ondr.kmcp.schema.core.PingRequest
import sh.ondr.kmcp.schema.core.ProgressNotification
import sh.ondr.kmcp.schema.logging.LoggingMessageNotification
import sh.ondr.kmcp.schema.logging.SetLoggingLevelRequest
import sh.ondr.kmcp.schema.prompts.GetPromptRequest
import sh.ondr.kmcp.schema.prompts.ListPromptsRequest
import sh.ondr.kmcp.schema.prompts.PromptListChangedNotification
import sh.ondr.kmcp.schema.resources.ListResourceTemplatesRequest
import sh.ondr.kmcp.schema.resources.ListResourcesRequest
import sh.ondr.kmcp.schema.resources.ReadResourceRequest
import sh.ondr.kmcp.schema.resources.ResourceListChangedNotification
import sh.ondr.kmcp.schema.resources.ResourceUpdatedNotification
import sh.ondr.kmcp.schema.resources.SubscribeRequest
import sh.ondr.kmcp.schema.resources.UnsubscribeRequest
import sh.ondr.kmcp.schema.roots.ListRootsRequest
import sh.ondr.kmcp.schema.roots.RootsListChangedNotification
import sh.ondr.kmcp.schema.sampling.CreateMessageRequest
import sh.ondr.kmcp.schema.tools.CallToolRequest
import sh.ondr.kmcp.schema.tools.ListToolsRequest
import sh.ondr.kmcp.schema.tools.ToolListChangedNotification

val kmcpSerializersModule = SerializersModule {
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
