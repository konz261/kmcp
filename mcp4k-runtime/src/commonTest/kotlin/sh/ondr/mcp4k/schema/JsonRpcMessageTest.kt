package sh.ondr.mcp4k.schema

import kotlinx.serialization.json.jsonPrimitive
import sh.ondr.mcp4k.runtime.serialization.toJsonRpcMessage
import sh.ondr.mcp4k.schema.tools.CallToolRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class JsonRpcMessageTest {
	@Test
	fun testCallToolRequestDeserialization() {
		val input = """{
            "jsonrpc":"2.0",
            "id":"1",
            "method":"tools/call",
            "params":{
               "name":"sendEmail",
               "arguments":{
                  "recipient":"me@test.com",
                  "title":"Test",
                  "body":"Hello"
               }
            }
        }"""

		val message = input.toJsonRpcMessage()
		assertTrue(message is CallToolRequest, "Expected a CallToolRequest")
		val request = message
		assertEquals("1", request.id)
		assertIs<CallToolRequest>(request)
		assertEquals("sendEmail", request.params.name)
		assertEquals("me@test.com", request.params.arguments!!["recipient"]?.jsonPrimitive?.content)
	}
}
