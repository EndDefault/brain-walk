package com.example.memorysteps.ui

import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.memorysteps.R
import com.example.memorysteps.data.AdaptiveCodec
import com.example.memorysteps.difficulty.AlgorithmMode
import com.example.memorysteps.game.GameConditions
import java.util.Locale
import org.json.JSONArray

private fun seconds(ms: Long): String = if (ms % 1000 == 0L) (ms / 1000).toString() else String.format(Locale.KOREA, "%.1f", ms / 1000.0)

@Composable
internal fun conditionsText(conditions: GameConditions): String {
    val solve = conditions.solveLimitMs?.let { stringResource(R.string.adaptive_seconds, seconds(it)) }
        ?: stringResource(R.string.adaptive_unlimited)
    return stringResource(R.string.adaptive_conditions, seconds(conditions.memoryLimitMs), seconds(conditions.waitMs), conditions.optionCount, solve)
}

/** Reachable only in debug builds. Diagnostic values never appear in the game itself. */
@Composable
internal fun DiagnosticsScreen(model: TrainingViewModel, enabled: Boolean, onBack: () -> Unit) {
    val epoch by model.algorithmEpoch.collectAsStateWithLifecycle()
    val decisions by model.decisions.collectAsStateWithLifecycle()
    val bundles by model.bundles.collectAsStateWithLifecycle()
    val arms by model.arms.collectAsStateWithLifecycle()
    Page {
        PageTitle(stringResource(R.string.ai_diagnostics))
        Text(stringResource(R.string.ai_mode_notice))
        Text("현재 모드: ${epoch?.mode ?: "준비 중"}")
        AlgorithmMode.entries.forEach { mode ->
            ActionButton(stringResource(if (mode == AlgorithmMode.BANDIT) R.string.ai_mode_bandit else R.string.ai_mode_comparison),
                { model.changeAlgorithm(mode) }, enabled = enabled && epoch?.mode != mode.name,
                primary = epoch?.mode == mode.name)
        }
        ActionButton(stringResource(R.string.ai_back), onBack, primary = false)
        HorizontalDivider()
        PageTitle("통합 학습값")
        arms.forEach { arm ->
            Text(arm.action, style = MaterialTheme.typography.titleLarge)
            Text("적용 경험 ${arm.hasEverApplied} · 유효 횟수 ${decimal(arm.effectiveCount)} · 보상 합계 ${decimal(arm.rewardSum)}")
        }
        HorizontalDivider()
        PageTitle("최근 판단 30개")
        if (decisions.isEmpty()) Text("종합 게임의 10문제를 마치면 판단이 표시됩니다.")
        decisions.forEach { decision ->
            Text("종합 게임 · ${decision.action}", style = MaterialTheme.typography.titleLarge)
            Text("${decision.source} · ${decision.status} · 보상 ${decision.rewardStatus}")
            Text("선택 이유: ${decision.reason}")
            bundles.firstOrNull { it.id == decision.inputBundleId }?.let { bundle ->
                Text("첫 정답 C=${bundle.firstCorrect}/10 · M=${mean(bundle.memorySumMs, bundle.memoryCount)}초" +
                    " · A=${mean(bundle.solveSumMs, bundle.solvedCount)}초 · W=${mean(bundle.weightedSumMs, bundle.solvedCount)}초")
            }
            Text("변경 전\n${conditionsText(AdaptiveCodec.conditions(decision.beforeConditions))}")
            Text("변경 후\n${conditionsText(AdaptiveCodec.conditions(decision.afterConditions))}")
            val candidates = JSONArray(decision.candidates)
            repeat(candidates.length()) { index ->
                val candidate = candidates.getJSONObject(index)
                Text("후보 ${candidate.getString("action")} · UCB ${if (candidate.isNull("score")) "미계산" else candidate.get("score")}")
            }
            Text("판단 ID ${decision.id}\n입력 묶음 ${decision.inputBundleId}\n모드 구간 ${decision.epochId}", style = MaterialTheme.typography.bodyMedium)
            HorizontalDivider()
        }
    }
}

private fun decimal(value: Double): String = String.format(Locale.KOREA, "%.4f", value)
private fun mean(sumMs: Long?, count: Int?): String = if (sumMs == null || count == null || count == 0) "없음" else decimal(sumMs / (1000.0 * count))
