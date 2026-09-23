package com.example.memorysteps.game

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ProblemGeneratorTest {
    @Test
    fun allTypesAndLayoutsHaveOneAnswerAndUniqueOptionsAcrossSeeds() {
        repeat(100) { seed ->
            val generator = ProblemGenerator(Random(seed))
            for (type in GameType.entries) {
                for (layout in ChoiceLayout.entries) {
                    val problem = generator.generate(type, GameConditions(optionCount = layout.count))
                    assertEquals(layout.count, problem.options.size)
                    assertEquals(1, problem.options.count { it.item == problem.target })
                    assertEquals(layout.count, problem.options.map { it.item }.toSet().size)
                    assertEquals(layout.count, problem.options.map { it.id }.toSet().size)
                    assertTrue(problem.options.all { it.item.type == type })
                    assertTrue(problem.id.isNotBlank())
                    assertTrue(problem.generatorVersion.isNotBlank())
                }
            }
        }
    }

    @Test
    fun colorsUseAllOrderedPairsOfFourColorsAtSixteenOptions() {
        val problem = ProblemGenerator(Random(1)).generate(GameType.COLOR, GameConditions(optionCount = 16))
        val expected = BaseColor.entries.flatMap { a -> BaseColor.entries.map { b -> ColorPair(a, b) } }.toSet()
        assertEquals(expected, problem.options.map { it.item }.toSet())
        assertNotEquals(ColorPair(BaseColor.BLUE, BaseColor.ORANGE), ColorPair(BaseColor.ORANGE, BaseColor.BLUE))
        assertEquals(4, BaseColor.entries.map { it.argb }.toSet().size)
    }

    @Test
    fun picturesUseSixteenDifferentSymbols() {
        val problem = ProblemGenerator(Random(2)).generate(GameType.PICTURE, GameConditions(optionCount = 16))
        assertEquals(PictureSymbol.entries.toSet(), problem.options.map { (it.item as PictureItem).symbol }.toSet())
    }

    @Test
    fun numbersAreAlwaysTwoDigits() {
        val generator = ProblemGenerator(Random(3))
        repeat(50) {
            val problem = generator.generate(GameType.NUMBER, GameConditions(optionCount = 16))
            assertTrue(problem.options.all { (it.item as NumberItem).value.toString().length == 2 })
        }
    }

    @Test
    fun seededRandomAndIdSourceReproduceTheFullSequence() {
        fun generator(): ProblemGenerator {
            var sequence = 0
            return ProblemGenerator(Random(42), ProblemIdSource { "problem-${sequence++}" })
        }
        val first = generator()
        val second = generator()
        repeat(30) { index ->
            val type = GameType.entries[index % 3]
            val a = first.generate(type)
            val b = second.generate(type)
            assertEquals(a.id, b.id)
            assertEquals(a.generatorVersion, b.generatorVersion)
            assertEquals(a.conditions, b.conditions)
            assertEquals(a.target, b.target)
            assertEquals(a.options, b.options)
        }
    }

    @Test
    fun replacementKeepsConditionsButChangesTargetAndId() {
        var id = 0
        val generator = ProblemGenerator(Random(5), ProblemIdSource { "replacement-${id++}" })
        for (type in GameType.entries) {
            val conditions = GameConditions(7_000, 9_500, 16, 15_000)
            var previous = generator.generate(type, conditions)
            repeat(20) {
                val next = generator.generate(type, conditions, previous.target)
                assertEquals(previous.conditions, next.conditions)
                assertEquals(previous.generatorVersion, next.generatorVersion)
                assertNotEquals(previous.target, next.target)
                assertNotEquals(previous.id, next.id)
                previous = next
            }
        }
    }

    @Test
    fun answersAreNotAlwaysPlacedAtOneIndex() {
        val generator = ProblemGenerator(Random(7))
        val indices = (1..100).map {
            val problem = generator.generate(GameType.NUMBER)
            problem.options.indexOfFirst { it.item == problem.target }
        }.toSet()
        assertEquals(setOf(0, 1, 2, 3), indices)
    }

    @Test
    fun mismatchedPreviousTargetIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            ProblemGenerator().generate(GameType.PICTURE, previousTarget = NumberItem(10))
        }
    }
}
