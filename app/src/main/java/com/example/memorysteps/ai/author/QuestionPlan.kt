package com.example.memorysteps.ai.author

import com.example.memorysteps.game.*
import java.util.Locale
import java.util.UUID

object AuthorCatalog {
    fun id(item: MemoryItem): String = when (item) {
        is ColorPair -> "C%02d".format(Locale.ROOT, item.left.ordinal * 4 + item.right.ordinal)
        is PictureItem -> "P%02d".format(Locale.ROOT, item.symbol.ordinal)
        is NumberItem -> "N${item.value}"
    }
    fun item(id: String): MemoryItem {
        require(id.matches(Regex("[CPN][0-9]{2}")))
        val index = id.substring(1).toInt()
        return when (id[0]) {
            'C' -> { require(index in 0..15); ColorPair(BaseColor.entries[index / 4], BaseColor.entries[index % 4]) }
            'P' -> { require(index in 0..15); PictureItem(PictureSymbol.entries[index]) }
            else -> NumberItem(index)
        }
    }
}

data class AuthorRequest(val key: String, val slots: List<GameType>, val conditions: GameConditions,
                         val context: String, val recent: Set<String>)
data class AuthoredPlan(val focus: String, val questions: List<List<String>>) {
    fun validate(request: AuthorRequest): AuthoredPlan {
        require(focus in setOf("COLOR", "PICTURE", "NUMBER", "BALANCED", "INSUFFICIENT_DATA"))
        require(questions.size == 10 && request.slots.size == 10)
        val targets = mutableSetOf<String>()
        questions.forEachIndexed { index, ids ->
            require(ids.size == request.conditions.optionCount && ids.distinct().size == ids.size)
            require(ids.all { AuthorCatalog.item(it).type == request.slots[index] })
            require(targets.add(ids.first()) && ids.first() !in request.recent)
        }
        return this
    }
    fun problems(request: AuthorRequest): List<MemoryProblem> {
        validate(request)
        return questions.mapIndexed { index, ids ->
            MemoryProblem(UUID.randomUUID().toString(), ProblemGenerator.version(request.slots[index]), request.conditions,
                AuthorCatalog.item(ids.first()), ids.shuffled().mapIndexed { option, id ->
                    AnswerOption("option-${option + 1}", AuthorCatalog.item(id))
                }, generationSource = "MODEL", modelVersion = NativeQuestionAuthor.MODEL_VERSION)
        }
    }
}
