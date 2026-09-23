package com.example.memorysteps.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ProblemValidationTest {
    private fun options() = (10..13).map { AnswerOption("$it", NumberItem(it)) }
    private fun problem(options: List<AnswerOption>, target: MemoryItem = NumberItem(10)) =
        MemoryProblem("test", "number-test-v1", GameConditions(), target, options)

    @Test
    fun duplicateContentEvenWithDifferentIdsIsRejected() {
        val options = options().toMutableList()
        options[1] = AnswerOption("different-id", NumberItem(10))
        assertThrows(IllegalArgumentException::class.java) { problem(options) }
    }

    @Test
    fun duplicateIdsEvenWithDifferentContentAreRejected() {
        val options = options().toMutableList()
        options[1] = AnswerOption("10", NumberItem(11))
        assertThrows(IllegalArgumentException::class.java) { problem(options) }
    }

    @Test
    fun missingAnswerMixedTypesAndWrongCountsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { problem(options(), NumberItem(99)) }
        assertThrows(IllegalArgumentException::class.java) {
            problem(options().dropLast(1) + AnswerOption("picture", PictureItem(PictureSymbol.CIRCLE)))
        }
        assertThrows(IllegalArgumentException::class.java) { problem(options().dropLast(1)) }
    }

    @Test
    fun callerCannotMutateValidatedOptions() {
        val source = options().toMutableList()
        val problem = problem(source)
        source.clear()
        assertEquals(4, problem.options.size)
        assertThrows(UnsupportedOperationException::class.java) {
            (problem.options as MutableList<AnswerOption>).clear()
        }
    }

    @Test
    fun blankIdentifiersAndInvalidNumbersAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { AnswerOption(" ", NumberItem(10)) }
        assertThrows(IllegalArgumentException::class.java) {
            MemoryProblem("", "v1", GameConditions(), NumberItem(10), options())
        }
        assertThrows(IllegalArgumentException::class.java) {
            MemoryProblem("id", "", GameConditions(), NumberItem(10), options())
        }
        for (number in listOf(9, 100)) {
            assertThrows(IllegalArgumentException::class.java) { NumberItem(number) }
        }
    }

    @Test
    fun acceptsBoundsAndFractionalWaitButRejectsInvalidConditions() {
        GameConditions(5_000, 3_000, 4, 15_000)
        GameConditions(20_000, 10_000, 16, 40_000)
        GameConditions(waitMs = 9_500, solveLimitMs = null)
        for (limit in listOf(4_999L, 20_001L)) {
            assertThrows(IllegalArgumentException::class.java) { GameConditions(memoryLimitMs = limit) }
        }
        for (wait in listOf(2_999L, 10_001L)) {
            assertThrows(IllegalArgumentException::class.java) { GameConditions(waitMs = wait) }
        }
        for (limit in listOf(0L, 14_999L, 40_001L)) {
            assertThrows(IllegalArgumentException::class.java) { GameConditions(solveLimitMs = limit) }
        }
        for (count in listOf(0, 3, 5, 8, 17)) {
            assertThrows(IllegalArgumentException::class.java) { GameConditions(optionCount = count) }
        }
    }

    @Test
    fun gridDimensionsMatchTheSpecification() {
        val expected = mapOf(4 to (2 to 2), 6 to (3 to 2), 9 to (3 to 3), 12 to (4 to 3), 16 to (4 to 4))
        for ((count, grid) in expected) {
            val layout = GameConditions(optionCount = count).layout
            assertEquals(grid, layout.columns to layout.rows)
            assertEquals(count, layout.columns * layout.rows)
        }
    }
}
