package sh.ondr.kmcp.runtime.sampling

import sh.ondr.kmcp.schema.sampling.CreateMessageRequest.CreateMessageParams
import sh.ondr.kmcp.schema.sampling.CreateMessageResult

/**
 * A pluggable interface for how the client handles a sampling/createMessage request.
 * Providing model selection, LLM-calling logic, and ultimately returning a [CreateMessageResult].
 */
fun interface SamplingProvider {
	/**
	 * Called when a `sampling/createMessage` request arrives from the server and
	 * the client grants permission.
	 *
	 * The implementation should:
	 *  - Pick a model
	 *  - Call an LLM
	 *  - Return a [CreateMessageResult] with containing the final model, role, content, etc.
	 *  - Throw an error if something goes wrong.
	 */
	suspend fun createMessage(params: CreateMessageParams): CreateMessageResult
}
