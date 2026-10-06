package com.example.memorysteps.ai

import com.example.memorysteps.ai.author.*
import com.example.memorysteps.game.*
import org.junit.Assert.*
import org.junit.Test

class QuestionPlanTest {
    private val slots = List(4) { GameType.COLOR } + List(3) { GameType.PICTURE } + List(3) { GameType.NUMBER }
    private fun request(count: Int = 4, recent: Set<String> = emptySet()) = AuthorRequest("test", slots, GameConditions(optionCount = count), "{}", recent)
    private fun plan(count: Int = 4): AuthoredPlan = AuthoredPlan("BALANCED", slots.mapIndexed { i, type ->
        val ids = when(type) {
            GameType.COLOR -> (0..15).map { "C%02d".format(it) }
            GameType.PICTURE -> (0..15).map { "P%02d".format(it) }
            GameType.NUMBER -> (10..99).map { "N$it" }
        }
        val target = ids[i]
        listOf(target) + ids.filter { it != target }.take(count - 1)
    })
    @Test fun allSupportedCountsHaveOneAnswerAndKeepTheModelSelectedContent() {
        listOf(4,6,9,12,16).forEach { count ->
            val selected = plan(count)
            val problems = selected.problems(request(count))
            problems.forEachIndexed { index, p ->
                assertEquals(selected.questions[index].first(), AuthorCatalog.id(p.target))
                assertEquals(selected.questions[index].toSet(), p.options.map { AuthorCatalog.id(it.item) }.toSet())
                assertEquals("MODEL", p.generationSource)
                assertEquals(1, p.options.count { it.item == p.target })
            }
        }
    }
    @Test fun invalidTypesCountsDuplicatesAndRecentTargetsAreRejected() {
        val good = plan()
        val bad = listOf(good.copy(questions = good.questions.drop(1)),
            good.copy(questions = good.questions.toMutableList().also { it[0] = listOf("N10","N11","N12","N13") }),
            good.copy(questions = good.questions.toMutableList().also { it[0] = listOf("C00","C00","C01","C02") }),
            good.copy(focus = "UNKNOWN"))
        bad.forEach { invalid -> assertThrows(IllegalArgumentException::class.java) { invalid.validate(request()) } }
        assertThrows(IllegalArgumentException::class.java) { good.validate(request(recent = setOf("C00"))) }
        assertThrows(IllegalArgumentException::class.java) { AuthorCatalog.item("C16") }
    }
}
