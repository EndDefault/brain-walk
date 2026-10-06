package com.example.memorysteps.game

/** Content identity, independent of a Compose renderer or translated label. */
sealed interface MemoryItem {
    val type: GameType
}

enum class BaseColor(val argb: Long) {
    BLUE(0xFF1E5AA8), ORANGE(0xFFD47713), GREEN(0xFF23754B), PURPLE(0xFF743C9B),
}

/** Always render both equal-sized cells in left-to-right order, even in RTL locales. */
data class ColorPair(val left: BaseColor, val right: BaseColor) : MemoryItem {
    override val type: GameType get() = GameType.COLOR
}

/** Original drawings for these IDs will be provided by the game-screen renderer. */
enum class PictureSymbol {
    CIRCLE, SQUARE, TRIANGLE, STAR, HEART, MOON, FLOWER, HOUSE,
    TREE, FISH, APPLE, CUP, KEY, UMBRELLA, BELL, CAR,
}

data class PictureItem(val symbol: PictureSymbol) : MemoryItem {
    override val type: GameType get() = GameType.PICTURE
}

/** Generator v1 uses two-digit numbers without leading zeros. */
data class NumberItem(val value: Int) : MemoryItem {
    init {
        require(value in 10..99) { "Number v1 supports two-digit numbers" }
    }

    override val type: GameType get() = GameType.NUMBER
}
