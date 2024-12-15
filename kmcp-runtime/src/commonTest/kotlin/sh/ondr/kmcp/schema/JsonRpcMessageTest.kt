package sh.ondr.kmcp.schema

import kotlinx.serialization.json.jsonPrimitive
import sh.ondr.kmcp.runtime.KMCP
import sh.ondr.kmcp.schema.requests.CallToolRequest
import sh.ondr.kmcp.schema.requests.JsonRpcRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JsonRpcMessageTest {
	private val json = KMCP.json

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

		val message = json.decodeFromString<JsonRpcRequest>(input)
		assertTrue(message is CallToolRequest, "Expected a CallToolRequest")
		val request = message
		assertEquals("1", request.id)
		assertEquals("tools/call", request.method)
		assertEquals("sendEmail", request.params.name)
		assertEquals("me@test.com", request.params.arguments!!["recipient"]?.jsonPrimitive?.content)
	}
}
