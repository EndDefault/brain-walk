package com.example.memorysteps.game

import java.util.UUID
import kotlin.random.Random

fun interface ProblemIdSource {
    fun nextId(): String
}

/** Rule-based sampling without replacement. The RNG and IDs can be deterministic in tests. */
class ProblemGenerator(
    private val random: Random = Random.Default,
    private val ids: ProblemIdSource = ProblemIdSource { UUID.randomUUID().toString() },
) {
    fun generate(
        type: GameType,
        conditions: GameConditions = GameConditions(),
        previousTarget: MemoryItem? = null,
    ): MemoryProblem {
        require(previousTarget == null || previousTarget.type == type) { "Previous target type mismatch" }
        val catalog = catalog(type)
        val possibleTargets = catalog.filter { it != previousTarget }
        val target = possibleTargets[random.nextInt(possibleTargets.size)]
        val distractors = catalog.filter { it != target }.shuffled(random).take(conditions.optionCount - 1)
        val options = (distractors + target).shuffled(random).mapIndexed { index, item ->
            AnswerOption(id = "option-${index + 1}", item = item)
        }
        return MemoryProblem(ids.nextId(), version(type), conditions, target, options)
    }

    private fun catalog(type: GameType): List<MemoryItem> = when (type) {
        GameType.COLOR -> BaseColor.entries.flatMap { left ->
            BaseColor.entries.map { right -> ColorPair(left, right) }
        }
        GameType.PICTURE -> PictureSymbol.entries.map(::PictureItem)
        GameType.NUMBER -> (10..99).map(::NumberItem)
    }

    private fun version(type: GameType): String = when (type) {
        GameType.COLOR -> "color-pair-v1"
        GameType.PICTURE -> "picture-symbol-v1"
        GameType.NUMBER -> "two-digit-number-v1"
    }
}
