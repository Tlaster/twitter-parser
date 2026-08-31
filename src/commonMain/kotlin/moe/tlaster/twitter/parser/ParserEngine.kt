package moe.tlaster.twitter.parser

internal class ParserEngine(
    private val enableEmoji: Boolean,
    private val enableDomainDetection: Boolean,
    private val enableNonAsciiInUrl: Boolean,
    private val enableEscapeInUrl: Boolean,
    private val enableCJKInCashTag: Boolean,
    private val validMarkInUserName: List<Char>,
    private val validMarkInHashTag: List<Char>,
) {
    fun parse(input: String): List<Token> {
        if (input.isEmpty()) return emptyList()

        val tokens = ArrayList<Token>(8)
        var plainStart = 0
        var index = 0
        while (index < input.length) {
            if (input[index] == '.' && enableDomainDetection) {
                val domain = scanDomain(input, index, plainStart)
                if (domain != null) {
                    if (domain.start >= 0) {
                        addPlain(tokens, input, plainStart, domain.start)
                        tokens.add(UrlToken(input.slice(domain.start, domain.end)))
                        plainStart = domain.end
                    }
                    index = domain.end
                    continue
                }
            }

            var kind: TokenKind? = null
            val end = when (input[index]) {
                'h' -> scanUrl(input, index).also { if (it > index) kind = TokenKind.Url }
                '@', '＠' -> scanUserName(input, index).also { if (it > index) kind = TokenKind.UserName }
                '#', '＃' -> scanHashTag(input, index).also { if (it > index) kind = TokenKind.HashTag }
                '$', '＄' -> scanCashTag(input, index).also { if (it > index) kind = TokenKind.CashTag }
                ':' -> scanEmoji(input, index).also { if (it > index) kind = TokenKind.Emoji }
                else -> -1
            }
            if (kind != null) {
                addPlain(tokens, input, plainStart, index)
                tokens.add(kind.create(input.slice(index, end)))
                index = end
                plainStart = end
            } else {
                index++
            }
        }
        addPlain(tokens, input, plainStart, input.length)
        return tokens
    }

    private fun scanUrl(input: String, start: Int): Int {
        var index = when {
            input.startsWith("https://", start, ignoreCase = true) -> start + 8
            input.startsWith("http://", start, ignoreCase = true) -> start + 7
            else -> return -1
        }
        var hasDot = false
        while (index < input.length) {
            val current = input[index]
            if (current.isEmptyCharacter()) return if (hasDot) index else -1

            if (current == ':') {
                if (enableEscapeInUrl) {
                    index++
                    continue
                }
                if (index + 1 < input.length && input[index + 1].isAsciiDigit()) {
                    index += 2
                    while (index < input.length && input[index].isAsciiDigit()) index++
                    if (index < input.length && (input[index] == '/' || input[index] == '?')) {
                        index++
                        continue
                    }
                    return index
                }
                return if (hasDot) index else -1
            }

            if (current.isUrlEscapeCharacter()) {
                if (!enableEscapeInUrl || index + 1 == input.length || input[index + 1].isEmptyCharacter()) {
                    return if (hasDot) index else -1
                }
                index++
                continue
            }

            if (!current.isLetterOrDigit()) {
                val endsHere = index + 1 == input.length || input[index + 1].isEmptyCharacter()
                if (current.isUrlMark()) {
                    if (endsHere && current == '.') return if (hasDot) index else -1
                    if (current == '.') hasDot = true
                    index++
                    continue
                }
                if (endsHere || !enableNonAsciiInUrl) return if (hasDot) index else -1
                index++
                continue
            }

            if (!enableNonAsciiInUrl && !current.isAsciiAlphanumeric()) {
                return if (hasDot) index else -1
            }
            index++
        }
        return if (hasDot) index else -1
    }

    private fun scanUserName(input: String, start: Int): Int {
        if (start > 0) {
            val previous = input[start - 1]
            val validBoundary = previous.isEmptyCharacter() ||
                (previous !in validMarkInUserName && !previous.isAsciiAlphanumeric()) ||
                previous.isFullWidthCharacter()
            if (!validBoundary) return -1
        }
        if (start + 1 >= input.length) return -1
        val first = input[start + 1]
        if (!first.isAsciiAlphanumericUnderscore() && first !in validMarkInUserName) return -1

        var index = start + 1
        while (index < input.length) {
            val current = input[index]
            when {
                current.isAsciiAlphanumericUnderscore() -> index++
                current in validMarkInUserName -> {
                    if (index + 1 < input.length && input[index + 1].isLetterOrDigit()) index++ else break
                }
                current == '/' && validMarkInUserName.isEmpty() -> return -1
                else -> break
            }
        }
        return index
    }

    private fun scanHashTag(input: String, start: Int): Int {
        if (start > 0) {
            val previous = input[start - 1]
            if (!previous.isEmptyCharacter() && !previous.isFullWidthCharacter()) return -1
        }
        if (start + 1 >= input.length) return -1
        val first = input[start + 1]
        if (!first.isLetterOrDigit() && first != '_' && first !in validMarkInHashTag && !first.isFullWidthCharacter()) {
            return -1
        }

        var index = start + 1
        while (index < input.length) {
            val current = input[index]
            if (current.isLetterOrDigit() || current == '_' || current in validMarkInHashTag ||
                (current.isFullWidthCharacter() && !current.isFullWidthSymbol())
            ) {
                index++
            } else {
                break
            }
        }
        return index
    }

    private fun scanCashTag(input: String, start: Int): Int {
        if (start > 0 && !input[start - 1].isEmptyCharacter()) return -1
        if (start + 1 >= input.length) return -1
        val first = input[start + 1]
        return when {
            first.isAsciiAlpha() -> scanAsciiCashTag(input, start)
            enableCJKInCashTag && first.isFullWidthCharacter() && !first.isFullWidthSymbol() -> {
                scanCjkCashTag(input, start)
            }
            enableCJKInCashTag && first.isAsciiDigit() -> scanDigitCashTag(input, start)
            else -> -1
        }
    }

    private fun scanAsciiCashTag(input: String, start: Int): Int {
        var index = start + 1
        var length = 0
        while (index < input.length && input[index].isAsciiAlphanumeric() && length < 20) {
            index++
            length++
        }
        if (length == 20 || index == input.length) return index
        return if (input[index].isEmptyCharacter() || input[index].isUrlMark()) index else -1
    }

    private fun scanCjkCashTag(input: String, start: Int): Int {
        var index = start + 1
        var length = 0
        while (index < input.length && length < 10) {
            val current = input[index]
            if ((current.isFullWidthCharacter() && !current.isFullWidthSymbol()) ||
                current.isAsciiAlphanumericUnderscore()
            ) {
                index++
                length++
            } else {
                break
            }
        }
        return index
    }

    private fun scanDigitCashTag(input: String, start: Int): Int {
        var index = start + 1
        var length = 0
        while (index < input.length && length < 10) {
            val current = input[index]
            if (current == 'k' || current == 'K' || current == 'm' || current == 'M' ||
                current == 'b' || current == 'B' || current == '.' || current == ','
            ) {
                return -1
            }
            if (current.isEmptyCharacter() || current.isDigitCashDelimiter()) return index
            index++
            length++
        }
        return index
    }

    private fun scanEmoji(input: String, start: Int): Int {
        if (!enableEmoji || start + 1 >= input.length || !input[start + 1].isAsciiAlphanumericUnderscore()) {
            return -1
        }
        var index = start + 2
        while (index < input.length && input[index].isAsciiAlphanumericUnderscore()) index++
        return if (index < input.length && input[index] == ':') index + 1 else -1
    }

    private fun scanDomain(input: String, dot: Int, plainStart: Int): DomainScan? {
        if (dot == plainStart || dot + 1 >= input.length || !input[dot + 1].isAsciiAlphanumeric()) return null

        var start = dot
        while (start > plainStart && input[start - 1].isUrlCharacter()) start--
        if (!input[start].isLetterOrDigit()) return null

        var end = dot + 1
        var lastDot = dot
        while (end < input.length) {
            val current = input[end]
            if (current == '.') {
                lastDot = end
                end++
            } else if (current.isAsciiAlphanumeric()) {
                end++
            } else {
                break
            }
        }
        var tldEnd = lastDot + 1
        while (tldEnd < end && input[tldEnd].isAsciiAlpha()) tldEnd++
        val valid = tldEnd > lastDot + 1 && isKnownDomain(input, lastDot + 1, tldEnd)
        return DomainScan(if (valid) start else -1, end)
    }

    private fun isKnownDomain(input: String, start: Int, end: Int): Boolean {
        var low = 0
        var high = DomainList.lastIndex
        while (low <= high) {
            val middle = (low + high) ushr 1
            val comparison = compareAsciiUppercase(input, start, end, DomainList[middle])
            when {
                comparison < 0 -> high = middle - 1
                comparison > 0 -> low = middle + 1
                else -> return true
            }
        }
        return false
    }

    private fun compareAsciiUppercase(input: String, start: Int, end: Int, value: String): Int {
        val length = end - start
        val commonLength = minOf(length, value.length)
        repeat(commonLength) { offset ->
            val inputChar = input[start + offset].asciiUppercase()
            val valueChar = value[offset]
            if (inputChar != valueChar) return inputChar.code - valueChar.code
        }
        return length - value.length
    }

    private fun addPlain(tokens: ArrayList<Token>, input: String, start: Int, end: Int) {
        if (start < end) tokens.add(StringToken(input.slice(start, end)))
    }

    private fun String.slice(start: Int, end: Int): String {
        return if (start == 0 && end == length) this else substring(start, end)
    }
}

private data class DomainScan(
    val start: Int,
    val end: Int,
)

private enum class TokenKind {
    Url,
    CashTag,
    UserName,
    HashTag,
    Emoji,
    ;

    fun create(value: String): Token = when (this) {
        Url -> UrlToken(value)
        CashTag -> CashTagToken(value)
        UserName -> UserNameToken(value)
        HashTag -> HashTagToken(value)
        Emoji -> EmojiToken(value)
    }
}

private fun Char.isAsciiAlpha(): Boolean = this in 'A'..'Z' || this in 'a'..'z'

private fun Char.isAsciiDigit(): Boolean = this in '0'..'9'

private fun Char.isAsciiAlphanumeric(): Boolean = isAsciiAlpha() || isAsciiDigit()

private fun Char.isAsciiAlphanumericUnderscore(): Boolean = isAsciiAlphanumeric() || this == '_'

private fun Char.isEmptyCharacter(): Boolean {
    return this == '\u0009' || this == '\u000A' || this == '\u000C' || this == '\u0020' || this == '　'
}

private fun Char.isUrlMark(): Boolean = when (this) {
    '-', '.', '_', '~', ':', '/', '?', '#', '[', ']', '@', '!', '$', '&', '\'', '(', ')', '*', '+', ',', ';', '=' -> true
    else -> false
}

private fun Char.isUrlEscapeCharacter(): Boolean = when (this) {
    '!', '~', '*', '\'', '(', ')', ';', ':', '+', ',', '%', '[', ']' -> true
    else -> false
}

private fun Char.isUrlCharacter(): Boolean = isAsciiAlphanumeric() || isUrlMark()

private fun Char.isDigitCashDelimiter(): Boolean = when (this) {
    '-', '.', '_', '~', ':', '?', '#', '[', ']', '@', '!', '$', '&', '\'', '(', ')', '*', '+', ',', ';', '=' -> true
    else -> false
}

private fun Char.asciiUppercase(): Char = if (this in 'a'..'z') (code - 32).toChar() else this

private fun Char.isFullWidthCharacter(): Boolean {
    val codePoint = code
    return codePoint in 0x1100..0x115F ||
        codePoint == 0x2329 || codePoint == 0x232A ||
        codePoint in 0x2E80..0xA4CF && codePoint != 0x303F ||
        codePoint in 0xAC00..0xD7A3 ||
        codePoint in 0xF900..0xFAFF ||
        codePoint in 0xFE10..0xFE19 ||
        codePoint in 0xFE30..0xFE6F ||
        codePoint in 0xFF01..0xFF60 ||
        codePoint in 0xFFE0..0xFFE6
}

private fun Char.isFullWidthSymbol(): Boolean {
    val codePoint = code
    return codePoint in 0x3001..0x303D ||
        codePoint in 0xFE10..0xFE19 ||
        codePoint in 0xFE30..0xFE4F ||
        codePoint in 0xFE50..0xFE6F ||
        codePoint in 0xFF01..0xFF0F ||
        codePoint in 0xFF1A..0xFF20 ||
        codePoint in 0xFF3B..0xFF40 ||
        codePoint in 0xFF5B..0xFF60
}
