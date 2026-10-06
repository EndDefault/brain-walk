package com.example.memorysteps.data

import com.example.memorysteps.game.*
import org.json.JSONArray
import org.json.JSONObject

internal object ProblemContentCodec {
    fun encode(problem: MemoryProblem): String = JSONObject().apply {
        put("version", 1)
        put("source", problem.generationSource)
        put("model", problem.modelVersion)
        put("target", itemKey(problem.target))
        put("options", JSONArray().apply {
            problem.options.forEach { option -> put(JSONObject().put("id", option.id).put("item", itemKey(option.item))) }
        })
    }.toString()

    fun decode(problem: ProblemEntity, slot: SlotEntity): MemoryProblem = decode(problem.id, problem.content,
        problem.generatorVersion, GameConditions(slot.memoryMs, slot.waitMs, slot.optionCount, slot.solveMs))

    fun decode(id: String, content: String, generatorVersion: String, conditions: GameConditions): MemoryProblem {
        val payload = JSONObject(content)
        require(payload.getInt("version") == 1) { "Unsupported problem content version" }
        val options = payload.getJSONArray("options")
        return MemoryProblem(id, generatorVersion, conditions,
            item(payload.getString("target")), List(options.length()) { index ->
                val option = options.getJSONObject(index)
                AnswerOption(option.getString("id"), item(option.getString("item")))
            }, payload.optString("source", "RULE"), payload.optString("model").takeIf { it.isNotEmpty() })
    }

    private fun itemKey(item: MemoryItem): String = when (item) {
        is ColorPair -> "COLOR:${item.left.name}:${item.right.name}"
        is PictureItem -> "PICTURE:${item.symbol.name}"
        is NumberItem -> "NUMBER:${item.value}"
    }

    private fun item(key: String): MemoryItem {
        val parts = key.split(':')
        return when (parts[0]) {
            "COLOR" -> ColorPair(BaseColor.valueOf(parts[1]), BaseColor.valueOf(parts[2]))
            "PICTURE" -> PictureItem(PictureSymbol.valueOf(parts[1]))
            "NUMBER" -> NumberItem(parts[1].toInt())
            else -> error("Unknown saved item type")
        }
    }
}
