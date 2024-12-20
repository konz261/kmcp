
<p align="center">
  <img src="./kmcp.svg" alt="KMCP banner">
</p>


<h1 align="center">KMCP - Kotlin Multiplatform MCP framework</h1>

<p align="center">
  <a href="https://search.maven.org/artifact/sh.ondr.kmcp/kmcp-runtime-jvm/0.1.0/jar">
    <img src="https://img.shields.io/maven-central/v/sh.ondr.kmcp/kmcp-runtime-jvm.svg?label=Maven%20Central&color=blue" alt="Maven Central"/>
  </a>
  <a href="https://www.apache.org/licenses/LICENSE-2.0">
    <img src="https://img.shields.io/badge/License-Apache%202.0-blue.svg?color=blue" alt="License"/>
  </a>
</p>


KMCP is a compiler-driven [Model Context Protocol](https://modelcontextprotocol.io) framework that lets you build MCP apps for Kotlin Multiplatform targets (native, JVM, Android, iOS and JS). It automatically generates the runtime glue and schemas that LLMs need to interact with your code.

Tools and prompts are just functions - simply add annotations and let KMCP handle all the boiler-plate:

```kotlin
@Tool
fun greet(name: String) = "Hello, $name!".toTextContent()
```

Done! Now, you just need to register the tool and start the server:

```kotlin
fun main() = runBlocking {
  val server = Server.Builder()
    .withTool(::greet)
    .withTransport(StdioTransport())
    .build()

  server.start()

  while(true) {
    delay(1000)
  }
}
```

That's it! Compile the above code and link the native binary to Claude Desktop by adding it to your `claude_desktop_config.json` file.

The `greet` tool will now be exposed over MCP.

## Installation

Just add this plugin:
```
plugins {
  id("sh.ondr.kmcp") version "0.1.0"
}
```

## Examples

### Tools

KMCP supports `@Tool` functions with various argument types, including
  * Primitives
  * Strings
  * Lists
  * Maps
  * Enums
  * @Serializable classes


```kotlin
@Serializable
enum class Priority {
  LOW, NORMAL, HIGH
}

@Serializable
data class Email(
  val title: String,
  val body: String?,
  val priority: Priority = Priority.NORMAL,
)

/**
 * Sends an email to all recipients
 */
@Tool
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

```
{
  "name": "sendEmail",
  "description": "Sends an email to all recipients",
  "inputSchema": {
    "type": "object",
    "properties": {
      "recipients": {
        "type": "array",
        "items": {
          "type": "string"
        }
      },
      "email": {
        "type": "object",
        "properties": {
          "title": {
            "type": "string"
          },
          "body": {
            "type": "string"
          },
          "priority": {
            "type": "string",
            "enum": ["LOW","NORMAL","HIGH"]
          }
        },
        "required": ["title"]
      }
    },
    "required": ["recipients","email"]
  }
}
```

Clients can now send a `tools/call` request with a JSON object describing the above schema. Invocation and type-safe deserialization will be handled by KMCP.


### Prompts

Just annotate functions with `@Prompt` and return a `GetPromptResult`. Arguments must be Strings:

```kotlin
@Prompt
fun codeReviewPrompt(code: String) = buildPrompt {
  user("Please review the following code:")
  user("'''\n$code\n'''")
}
```

### Resources (Coming Soon)

Should be ready by next week.

## How It Works

- Annotated `@Tool` and `@Prompt` functions are processed at compile time.
- KMCP generates schemas, handlers, and registrations automatically.
- Generated code is injected during the IR phase.
- If you mess something up, you (hopefully) get a compile-time error.

## Limitations

- **KMCP is currently experimental and in early development. Use at your own risk.**
- Many MCP features (notifications, pagination, roots, sampling, resources) are still missing, but will be added.
- @Tool and @Prompt functions can't be suspendable at the moment. This will be fixed.
- The JSON schema doesn't provide references, property descriptions or validation keywords as of today, but this [will be added](https://github.com/ondrsh/kotlin-json-schema/tree/main)
- Only Stdio transport for now.

## Contributing
Issues and pull requests are welcome.

## License
Licensed under the [Apache License 2.0](./LICENSE).
