package com.example.memorysteps.game

enum class GameType { COLOR, PICTURE, NUMBER }

/** Columns × rows, shared by every game type. */
enum class ChoiceLayout(val count: Int, val columns: Int, val rows: Int) {
    FOUR(4, 2, 2), SIX(6, 3, 2), NINE(9, 3, 3), TWELVE(12, 4, 3), SIXTEEN(16, 4, 4);

    companion object {
        fun forCount(count: Int): ChoiceLayout =
            entries.firstOrNull { it.count == count }
                ?: throw IllegalArgumentException("Unsupported option count: $count")
    }
}

data class GameConditions(
    val memoryLimitMs: Long = 20_000L,
    val waitMs: Long = 3_000L,
    val optionCount: Int = 4,
    val solveLimitMs: Long? = null,
) {
    init {
        require(memoryLimitMs in 5_000L..20_000L) { "Memory limit must be 5–20 seconds" }
        require(waitMs in 3_000L..10_000L) { "Wait must be 3–10 seconds" }
        require(solveLimitMs == null || solveLimitMs in 15_000L..40_000L) {
            "Solve limit must be absent or 15–40 seconds"
        }
        ChoiceLayout.forCount(optionCount)
    }

    val layout: ChoiceLayout get() = ChoiceLayout.forCount(optionCount)
}
