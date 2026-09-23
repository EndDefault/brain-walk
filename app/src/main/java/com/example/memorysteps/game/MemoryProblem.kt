package com.example.memorysteps.game

import java.util.Collections

data class AnswerOption(val id: String, val item: MemoryItem) {
    init {
        require(id.isNotBlank()) { "Option ID must not be blank" }
    }
}

class MemoryProblem(
    val id: String,
    val generatorVersion: String,
    val conditions: GameConditions,
    val target: MemoryItem,
    options: List<AnswerOption>,
) {
    // Copy and protect the caller's list so validated content cannot change later.
    val options: List<AnswerOption> = frozenCopy(options)
    val type: GameType get() = target.type

    init {
        require(id.isNotBlank()) { "Problem ID must not be blank" }
        require(generatorVersion.isNotBlank()) { "Generator version must not be blank" }
        require(this.options.size == conditions.optionCount) { "Option count must match conditions" }
        require(this.options.map { it.id }.distinct().size == this.options.size) { "Duplicate option IDs" }
        require(this.options.map { it.item }.distinct().size == this.options.size) { "Duplicate content" }
        require(this.options.all { it.item.type == type }) { "All options must have the target type" }
        require(this.options.count { it.item == target } == 1) { "Exactly one answer is required" }
    }
}

internal fun <T> frozenCopy(items: Collection<T>): List<T> =
    Collections.unmodifiableList(ArrayList(items))
