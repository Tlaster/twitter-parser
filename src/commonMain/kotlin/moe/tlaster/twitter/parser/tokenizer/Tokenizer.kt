package moe.tlaster.twitter.parser.tokenizer


internal class Tokenizer(
    val enableEmoji: Boolean = false,
    val enableDomainDetection: Boolean = false,
    val enableNonAsciiInUrl: Boolean = true,
    val enableEscapeInUrl: Boolean = false,
    val validMarkInUserName: List<Char> = listOf(),
    val validMarkInHashTag: List<Char> = listOf(),
) {
    private var currentState: State = DataState
    private lateinit var tokens: ArrayList<TokenCharacterType>
    fun parse(reader: Reader): List<TokenCharacterType> {
        tokens = (0..<reader.length).map { TokenCharacterType.UnKnown }.toCollection(ArrayList())
        while (reader.hasNext()) {
            currentState.read(this, reader)
        }
        return tokens.toList()
    }

    fun emit(tokenCharacterType: TokenCharacterType, index: Int) {
        tokens[index - 1] = tokenCharacterType
    }

    /**
     * Emit a range of token character type
     * @param tokenCharacterType the token character type to emit
     * @param start the start index of the range, not included
     * @param end the end index of the range, included
     */
    fun emitRange(tokenCharacterType: TokenCharacterType, start: Int, end: Int) {
        repeat(end - start) {
            tokens[start + it] = tokenCharacterType
        }
    }

    fun switch(state: State) {
        currentState = state
    }

    fun reject(position: Int) {
        val index = tokens.subList(0, position).indexOfLast { it == TokenCharacterType.Character } + 1
        repeat(position - index - 1) {
            tokens[index + it] = TokenCharacterType.Character
        }
    }

    fun readAt(position: Int): TokenCharacterType {
        return tokens[position]
    }

    fun accept() {
    }
}
