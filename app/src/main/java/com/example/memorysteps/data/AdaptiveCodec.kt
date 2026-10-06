package com.example.memorysteps.data

import com.example.memorysteps.difficulty.Candidate
import com.example.memorysteps.difficulty.LearningAction
import com.example.memorysteps.difficulty.Restoration
import com.example.memorysteps.game.GameConditions
import org.json.JSONArray
import org.json.JSONObject

internal object AdaptiveCodec {
    fun conditions(c: GameConditions): String = JSONObject().put("version", 1)
        .put("memoryMs", c.memoryLimitMs).put("waitMs", c.waitMs).put("options", c.optionCount)
        .put("solveMs", c.solveLimitMs ?: JSONObject.NULL).toString()
    fun conditions(value: String): GameConditions = JSONObject(value).let {
        require(it.getInt("version") == 1)
        GameConditions(it.getLong("memoryMs"), it.getLong("waitMs"), it.getInt("options"),
            if (it.isNull("solveMs")) null else it.getLong("solveMs"))
    }
    fun restorations(items: List<Restoration>): String = JSONArray().apply {
        items.forEach { put(JSONObject().put("id", it.reductionId).put("amountMs", it.amountMs)) }
    }.toString()
    fun restorations(value: String): List<Restoration> = JSONArray(value).let { array ->
        List(array.length()) { array.getJSONObject(it).let { item -> Restoration(item.getLong("id"), item.getLong("amountMs")) } }
    }
    fun candidates(items: List<Candidate>, scores: Map<LearningAction, Double>): String = JSONArray().apply {
        items.forEach { item -> put(JSONObject().put("action", item.action.name)
            .put("conditions", JSONObject(conditions(item.conditions)))
            .put("score", scores[item.action]?.let { if (it.isInfinite()) "INFINITY" else it } ?: JSONObject.NULL)) }
    }.toString()
}
