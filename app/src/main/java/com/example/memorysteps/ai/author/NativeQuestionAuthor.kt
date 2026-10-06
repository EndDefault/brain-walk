package com.example.memorysteps.ai.author

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

interface QuestionAuthor {
    suspend fun compose(request: AuthorRequest): AuthoredPlan
    fun cancel() {}
}

/** Internal test seam: tests provide a deterministic author before Activity creation. */
object QuestionAuthors {
    internal var testFactory: ((Context) -> QuestionAuthor)? = null
    fun create(context: Context): QuestionAuthor = testFactory?.invoke(context) ?: NativeQuestionAuthor(context.applicationContext)
}

class NativeQuestionAuthor(private val context: Context) : QuestionAuthor {
    companion object {
        const val MODEL_VERSION = "qwen3-0.6b-common-v0.2-q8-android-v1"
        private val mutex = Mutex()
        private var loaded = false
    }
    override suspend fun compose(request: AuthorRequest): AuthoredPlan = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (!loaded) { System.loadLibrary("memory_author"); loaded = true }
            val model = prepareModel()
            val template = context.assets.open("models/question-prompt.txt").bufferedReader().use { it.readText() }
            require(request.context.length < 12000)
            ensureActive()
            val text = generate(model.absolutePath, template.replace("{{CONTEXT}}", request.context),
                request.slots.map { it.ordinal }.toIntArray(), request.conditions.optionCount, request.recent.toTypedArray(), 180_000)
            require(text.length <= 16000 && text.startsWith("<tool_call>\n") && text.endsWith("\n</tool_call>"))
            val root = JSONObject(text.removePrefix("<tool_call>\n").removeSuffix("\n</tool_call>"))
            require(root.length() == 2 && root.getString("name") == "submit_question_plan")
            val args = root.getJSONObject("arguments")
            require(args.length() == 2)
            val questions = args.getJSONArray("questions")
            AuthoredPlan(args.getString("focus"), List(questions.length()) { slot ->
                val ids = questions.getJSONArray(slot)
                List(ids.length()) { ids.getString(it) }
            }).validate(request)
        }
    }
    private fun prepareModel(): File {
        val manifest = context.assets.open("models/question-author-v2.json").bufferedReader().use { JSONObject(it.readText()) }
        val sha = manifest.getString("sha256")
        require(sha.matches(Regex("[a-f0-9]{64}")))
        val directory = File(context.noBackupFilesDir, "question-model").apply { mkdirs() }
        val target = File(directory, "$sha.gguf")
        if (target.isFile && target.length() == manifest.getLong("bytes")) return target
        val pending = File(directory, "$sha.partial")
        val digest = MessageDigest.getInstance("SHA-256")
        try {
            context.assets.open("models/question-author-v2-q8.gguf").use { input ->
                pending.outputStream().use { output ->
                    val bytes = ByteArray(1024 * 1024)
                    while (true) {
                        val count = input.read(bytes)
                        if (count == -1) break
                        digest.update(bytes, 0, count); output.write(bytes, 0, count)
                    }
                    output.fd.sync()
                }
            }
            require(digest.digest().joinToString("") { "%02x".format(it) } == sha) { "MODEL_CHECKSUM" }
            check(pending.renameTo(target)) { "MODEL_INSTALL" }
        } finally { pending.delete() }
        return target
    }
    private external fun generate(path: String, prompt: String, slots: IntArray, optionCount: Int, recent: Array<String>, timeoutMs: Int): String
    override fun cancel() { if (loaded) cancelNative() }
    private external fun cancelNative()
}
