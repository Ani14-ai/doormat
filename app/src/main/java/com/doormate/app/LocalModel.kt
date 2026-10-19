package com.doormate.app

import com.google.ai.edge.litertlm.*
import com.doormate.app.core.Conversation
import java.io.File

/** Real local language model. No network inference or scripted fallback. */
class LocalModel(private val path: String) : Conversation.Model {
    private var engine: Engine? = null
    @Synchronized fun prepare() {
        check(File(path).isFile) { "Download the AI model in Setup first." }
        if (engine == null) {
            val candidate = Engine(EngineConfig(modelPath = path, backend = Backend.CPU(), maxNumTokens = 4096))
            try { candidate.initialize(); engine = candidate }
            catch (e: Exception) { candidate.close(); throw e }
        }
    }
    @Synchronized override fun generate(instructions: String, transcript: String): String {
        prepare()
        val config = ConversationConfig(
            systemInstruction = Contents.of(instructions),
            samplerConfig = SamplerConfig(topK = 20, topP = 0.9, temperature = 0.35),
            maxOutputToken = 160
        )
        // Fresh native conversation with explicit bounded history prevents cross-caller leakage.
        return engine!!.createConversation(config).use { it.sendMessage(transcript).toString() }
    }
    @Synchronized fun close() { engine?.close(); engine = null }
}
