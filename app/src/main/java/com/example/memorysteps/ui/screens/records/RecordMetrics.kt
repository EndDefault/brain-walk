package com.example.memorysteps.ui.screens.records

import com.example.memorysteps.data.CycleHistory
import kotlin.math.roundToInt

/** No observations is different from having answered every question incorrectly. */
internal fun accuracyPercent(correct: Int, total: Int): Int? =
    if (total <= 0) null else (correct.coerceIn(0, total).toDouble() * 100 / total).roundToInt()

/** History is capped at 30 in the DAO; this is a recent trend, never an all-time best. */
internal fun recentCompletedGames(history: List<CycleHistory>): List<CycleHistory> = history
    .filter { it.status == "COMPLETED" && it.problems > 0 }
    .sortedWith(compareBy<CycleHistory> { it.completedAt ?: it.createdAt }.thenBy { it.id })
    .takeLast(10)
