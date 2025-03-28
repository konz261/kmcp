<p align="center">
  <img src="./mcp4k.svg" alt="mcp4k banner">
</p>

<p align="center">
  <a href="https://central.sonatype.com/search?q=sh.ondr.mcp4k">
    <img src="https://img.shields.io/maven-central/v/sh.ondr.mcp4k/mcp4k-gradle.svg?label=Maven%20Central&color=blue" alt="Maven Central"/>
  </a>
  <a href="https://www.apache.org/licenses/LICENSE-2.0">
    <img src="https://img.shields.io/badge/License-Apache%202.0-blue.svg?color=blue" alt="License"/>
  </a>
</p>

<b>mcp4k</b> is a compiler-driven framework for building both <b>clients and servers</b> using the
<a href="https://modelcontextprotocol.io">Model Context Protocol</a> (MCP) in Kotlin.
It implements the vast majority of the MCP specification, including resources, prompts, tools, sampling, and more.

By annotating your functions with ```@McpTool``` or ```@McpPrompt```,
mcp4k automatically generates JSON-RPC handlers, schema metadata, and a complete lifecycle framework for you.

---

## Overview

- **Client**: Connects to any MCP server to request prompts, read resources, or invoke tools.
- **Server**: Exposes resources, prompts, and tools to MCP-compatible clients, handling standard JSON-RPC messages and protocol events.
- **Transports**: Supports `stdio`, with HTTP-SSE and other transports on the roadmap.
- **Lifecycle**: Manages initialization, cancellation, sampling, progress tracking, and more.

mcp4k goes beyond simple stubs: it also enforces correct parameter typing at compile time.
If you describe a tool parameter incorrectly, you get a compile-time error instead of a runtime mismatch.

---

## Installation

Add mcp4k to your build:

```kotlin
plugins {
  kotlin("multiplatform") version "2.1.0" // or kotlin("jvm")
  kotlin("plugin.serialization") version "2.1.0"

  id("sh.ondr.mcp4k") version "0.3.6" // <-- Add this
}
```

---

## Quick Start

### Create a Simple Server

```kotlin
/**
 * Reverses an input string
 *
 * @param input The string to be reversed
 */
@McpTool
fun reverseString(input: String): ToolContent {
  return "Reversed: ${input.reversed()}".toTextContent()
}

fun main() = runBlocking {
  val server = Server.Builder()
    .withTool(::reverseString)
    .withTransport(StdioTransport())
    .build()
    
  server.start()
  
  // Keep server running 
  while (true) { 
    delay(1000)
  }
}
```

In this example, your new ```@McpTool``` is exposed via JSON-RPC as ```reverseString```.
Clients can call it by sending ```tools/call``` messages.

---

### Create a Simple Client

```kotlin
fun main() = runBlocking {
  val client = Client.Builder()
    .withClientInfo("MyClient", "1.0.0")
    .withTransport(StdioTransport())
    .build()
    
  // Connect to a MCP server using the supplied transport
  client.start()
  client.initialize()
  
  // For example, list available tools using pagination
  val allTools = mutableListOf<Tool>()
  var pageCount = 0
  
  client.fetchPagesAsFlow(ListToolsRequest).collect { pageOfTools ->
    pageCount++
    allTools += pageOfTools
  }
  println("Server tools = ${allTools}")
}
```

Once connected, the client can discover prompts/tools/resources and make calls according to the MCP spec.
All boilerplate (capability negotiation, JSON-RPC ID handling, etc.) is handled by mcp4k.

---

## Transport Logging

You can observe raw incoming/outgoing messages by providing ```withTransportLogger``` lambdas:

```kotlin
val server = Server.Builder()
  .withTransport(StdioTransport())
  .withTransportLogger(
    logIncoming = { msg -> println("SERVER INCOMING: $msg") },
    logOutgoing = { msg -> println("SERVER OUTGOING: $msg") },
  )
  .build()
```

Both ```Server``` and ```Client``` accept this configuration. Super useful for debugging and tests.

---

## Tools

```kotlin
@JsonSchema @Serializable
enum class Priority {
  LOW, NORMAL, HIGH
}

/**
 * @property title The email's title
 * @property body The email's body
 * @property priority The email's priority
 */
@JsonSchema @Serializable
data class Email(
  val title: String,
  val body: String?,
  val priority: Priority = Priority.NORMAL,
)

/**
 * Sends an email
 * @param recipients The email addresses of the recipients
 * @param email The email to send
 */
@McpTool
fun sendEmail(
  recipients: List<String>,
  email: Email,
) = buildString {
  append("Email sent to ${recipients.joinToString()} with ")
  append("title '${email.title}' and ")
  append("body '${email.body}' and ")
  append("priority ${email.priority}")
}.toTextContent()
```

When clients call `tools/list`, they see a JSON schema describing the tool's input:

```json
{
  "type": "object",
  "description": "Sends an email",
  "properties": {
    "recipients": {
      "type": "array",
      "description": "The email addresses of the recipients",
      "items": {
        "type": "string"
      }
    },
    "email": {
      "type": "object",
      "description": "The email to send",
      "properties": {
        "title": {
          "type": "string",
          "description": "The email's title"
        },
        "body": {
          "type": "string",
          "description": "The email's body"
        },
        "priority": {
          "type": "string",
          "description": "The email's priority",
          "enum": [
            "LOW",
            "NORMAL",
            "HIGH"
          ]
        }
      },
      "required": [
        "title"
      ]
    }
  },
  "required": [
    "recipients",
    "email"
  ]
}
```
KDoc parameter descriptions are type-safe and will throw a compile-time error if you specify a non-existing property.

Clients can now send a `tools/call` request with a JSON object describing the above schema. Invocation and type-safe deserialization will be handled by mcp4k.

---

## Prompts

Annotate functions with ```@McpPrompt``` to define parameterized conversation templates:

```kotlin
@McpPrompt
fun codeReviewPrompt(code: String) = buildPrompt {
  user("Please review the following code:")
  user("'''\n$code\n'''")
}
```

Clients can call ```prompts/get``` to retrieve the underlying messages.

---

## Server Context

In some cases, you want multiple tools or prompts to share state.

mcp4k allows you to attach a custom **context object** that tools and prompts can reference. For example:

```kotlin
// 1) Implement your custom context
class MyServerContext : ServerContext {
  var userName: String = ""
}

// 2) A tool function that writes into the context
@McpTool
fun Server.setUserName(name: String): ToolContent {
  getContextAs<MyServerContext>().userName = name
  return "Username set to: $name".toTextContent()
}

// 3) Another tool that reads from the context
@McpTool
fun Server.greetUser(): ToolContent {
  val name = getContextAs<MyServerContext>().userName
  if (name.isEmpty()) return "No user set yet!".toTextContent()
  return "Hello, $name!".toTextContent()
}

fun main() = runBlocking {
  val context = MyServerContext()
  val server = Server.Builder()
    .withContext(context) // <-- Provide the context
    .withTool(Server::setUserName)
    .withTool(Server::greetUser)
    .withTransport(StdioTransport())
    .build()
  
  server.start()
  while(true) {
    delay(1000)
  }
}
```

1) Pass it in with ```.withContext(MyServerContext())```
2) Each tool or prompt can access it by calling ```getContextAs()```

---

## Resources

Resources are provided by a `ResourceProvider`. You can either create your own `ResourceProvider` or use one of the 2 default implementations:

### DiscreteFileProvider

Let's say you want to expose 2 files:
- /app/resources/cpp/my_program.h
- /app/resources/cpp/my_program.cpp

You would first create the following provider:
```kotlin
val fileProvider = DiscreteFileProvider(
  fileSystem = FileSystem.SYSTEM,
  rootDir = "/app/resources".toPath(),
  initialFiles = listOf(
    File(
      relativePath = "cpp/my_program.h",
      mimeType = "text/x-c++",
    ),
    File(
      relativePath = "cpp/my_program.cpp",
      mimeType = "text/x-c++",
    ),
  )
)
```

And add it when building the server:
```kotlin
val server = Server.Builder()
  .withResourceProvider(fileProvider)
  .withTransport(StdioTransport())
  .build()
```

A client calling `resources/list` will then receive:
```json
{
  "resources": [
    {
      "uri": "file://cpp/my_program.h",
      "name": "my_program.h",
      "description": "File at cpp/my_program.h",
      "mimeType": "text/x-c++"
    },
    {
      "uri": "file://cpp/my_program.cpp",
      "name": "my_program.cpp",
      "description": "File at cpp/my_program.cpp",
      "mimeType": "text/x-c++"
    }
  ]
}
```

A client sending a `resources/read` request to fetch the contents of the source file would receive:
```json
{
  "contents": [
    {
      "uri": "file://cpp/my_program.cpp",
      "mimeType": "text/x-c++",
      "text": "int main(){}"
    }
  ]
}
```

You can also add or remove files at runtime via
```kotlin
fileProvider.addFile(
  File(
    relativePath = "cpp/README.txt",
    mimeType = "text/plain",
  )
)

fileProvider.removeFile("cpp/my_program.h")
```

Both `addFile` and `removeFile` will send a `notifications/resources/list_changed` notification.

<br>

When making changes to a file, always call
```kotlin
fileProvider.onResourceChange("cpp/my_program.h")
```

If (and only if) the client subscribed to this resource, this will send a `notifications/resources/updated` notification to the client.

<br>

### TemplateFileProvider

If you want to expose a whole directory, you can do:
```kotlin
val templateFileProvider = TemplateFileProvider(
  fileSystem = FileSystem.SYSTEM,
  rootDir = "/app/resources".toPath(),
)
```

A client calling `resources/templates/list` will receive:
```json
{
  "resourceTemplates": [
    {
      "uriTemplate": "file:///{path}",
      "name": "Arbitrary local file access",
      "description": "Allows reading any file by specifying {path}"
    }
  ]
}
```

The client can then issue a `resources/read` request by providing the `path`:
```json
{
  "method": "resources/read",
  "params": {
    "uri": "file:///cpp/my_program.cpp"
  }
}
```

This will read from `/app/resources/cpp/my_program.cpp` and return the result:
```json
{
  "contents": [
    {
      "uri": "file:///cpp/my_program.cpp",
      "mimeType": "text/plain",
      "text": "int main(){}"
    }
  ]
}
```

Note the incorrect `text/plain` here - proper MIME detection will be added at some point.

Similarly to `DiscreteFileProvider`, when modifying a resource, call
```kotlin
templateFileProvider.onResourceChange("cpp/my_program.h")
```
to trigger the notification in case a client is subscribed to this resource.

**Use those FileProviders only in a sand-boxed environment, they are NOT production-ready.**

---

## Sampling

Clients can fulfill server-initiated LLM requests by providing a `SamplingProvider`.

In a real application, you would call your favorite LLM API (e.g., OpenAI, Anthropic) inside the provider. Here’s a simplified example that always returns a dummy completion:

```kotlin
// 1) Define a sampling provider
val samplingProvider = SamplingProvider { params: CreateMessageParams ->
  CreateMessageResult(
    model = "dummy-model",
    role = Role.ASSISTANT,
    content = TextContent("Dummy completion result"),
    stopReason = "endTurn",
  )
}

// 2) Build the client with sampling support
val client = Client.Builder()
  .withTransport(StdioTransport())
  .withPermissionCallback { userApprovable -> 
    // Prompt the user for confirmation here
    true 
  }
  .withSamplingProvider(samplingProvider) // Register the provider
  .build()

runBlocking {
  client.start()
  client.initialize()

  // Now, if a server sends a "sampling/createMessage" request, 
  // the samplingProvider will be invoked to generate a response.
}
```

---

## Request Cancellations

mcp4k uses Kotlin coroutines for cooperative cancellation. For example, a long-running server tool:

```kotlin
@McpTool
suspend fun slowToolOperation(iterations: Int = 10): ToolContent {
  for (i in 1..iterations) {
    delay(1000)
  }
  return "Operation completed after $iterations".toTextContent()
}
```

The client can cancel mid-operation:

```kotlin
val requestJob = launch {
  client.sendRequest { id ->
    CallToolRequest(
      id = id,
      params = CallToolRequest.CallToolParams(
        name = "slowToolOperation",
        arguments = mapOf("iterations" to 20),
      ),
    )
  }
}
delay(600)
requestJob.cancel("User doesn't want to wait anymore")
```

Under the hood, mcp4k sends a notification to the server:
```json
{
  "method": "notifications/cancelled",
  "jsonrpc": "2.0",
  "params": {
    "requestId": "2",
    "reason": "Client doesn't want to wait anymore"
  }
}
```
and the server will abort the suspended tool operation.

---

## Roadmap

```
✅ Add resource capability
✅ @McpTool and @McpPrompt functions
✅ Request cancellations
✅ Pagination
✅ Sampling (client-side)
✅ Roots
✅ Transport logging
⬜ Completions
⬜ Support logging levels
⬜ Proper version negotiation
⬜ Emit progress notifications from @McpTool functions
⬜ Proper MIME detection
⬜ Add FileWatcher to automate resources/updated notifications
⬜ HTTP-SSE transport
⬜ Add references, property descriptions and validation keywords to the JSON schemas
```

---

## How mcp4k Works

- Annotated ```@McpTool``` and ```@McpPrompt``` functions are processed at compile time.
- mcp4k generates JSON schemas, request handlers, and registration code automatically.
- Generated code is injected during Kotlin's IR compilation phase, guaranteeing type-safe usage.
- If your KDoc references unknown parameters, the build fails, forcing you to keep docs in sync with code.

---

## Contributing

Issues and pull requests are welcome!
Feel free to open a discussion or contribute improvements.

**License**: mcp4k is available under the [Apache License 2.0](./LICENSE).
