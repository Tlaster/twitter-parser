package moe.tlaster.twitter.parser.tokenizer

private val asciiUppercase = 'A'..'Z'
private val asciiLowercase = 'a'..'z'
private val asciiAlpha = asciiUppercase + asciiLowercase
private val asciiDigit = '0'..'9'
private val asciiAlphanumeric = asciiAlpha + asciiDigit
private val asciiAlphanumericUnderscore = asciiAlphanumeric + '_'
private val asciiAlphanumericUnderscoreDash = asciiAlphanumericUnderscore + '-'
private val asciiAlphanumericUnderscoreDashPlus = asciiAlphanumericUnderscoreDash + '+'
private val asciiUpperHexDigit = 'A'..'F'
private val asciiLowerHexDigit = 'a'..'f'
private val asciiHexDigit = asciiUpperHexDigit + asciiLowerHexDigit
private const val NULL = '\u0000'
private const val TAB = '\u0009'
private const val LF = '\u000A'
private val emptyChar = listOf(TAB, LF, '\u000C', '\u0020', '　')
private val asciiAlphanumericAndEmpty = asciiAlphanumeric + ' ' + TAB + LF
private val marks = "-._~:/?#[]@!\$&'()*+,;=".toList()
private val urlDisallowedEndMarks = ".".toList()
private val urlChar = asciiAlphanumeric + marks

private fun Int.isFullWidthCodePoint(): Boolean {
    val cp = this
    if (cp < 0) return false
    return (cp in 0x1100..0x115F) ||            // Hangul Jamo
            (cp == 0x2329 || cp == 0x232A) ||    // 〈 〉
            (cp in 0x2E80..0xA4CF && cp != 0x303F) || // CJK radicals/strokes… Yi (exclude 0x303F)
            (cp in 0xAC00..0xD7A3) ||            // Hangul Syllables
            (cp in 0xF900..0xFAFF) ||            // CJK Compatibility Ideographs
            (cp in 0xFE10..0xFE19) ||            // Vertical forms
            (cp in 0xFE30..0xFE6F) ||            // CJK Compatibility Forms… Small Form Variants
            (cp in 0xFF01..0xFF60) ||            // Fullwidth ASCII variants etc.
            (cp in 0xFFE0..0xFFE6) ||            // Fullwidth symbols
            (cp in 0x1B000..0x1B001) ||          // Kana Supplement
            (cp in 0x1F200..0x1F251) ||          // Enclosed Ideographic Supplement
            (cp in 0x20000..0x3FFFD)             // CJK Unified Ideographs Ext. B–(up to T)
}


private fun Char.isFullWidthChar(): Boolean = this.code.isFullWidthCodePoint()


private fun Int.isFullWidthSymbolCodePoint(): Boolean {
    val cp = this
    if (cp < 0) return false

    // CJK Symbols & Punctuation
    if (cp in 0x3001..0x303D) return true  // 「」『』、《》、。 、、
    // Vertical Forms
    if (cp in 0xFE10..0xFE19) return true
    // CJK Compatibility Forms
    if (cp in 0xFE30..0xFE4F) return true
    // Small Form Variants
    if (cp in 0xFE50..0xFE6F) return true
    // Fullwidth ASCII
    if (cp in 0xFF01..0xFF0F) return true   // ！"#$%&'()*+,-./
    if (cp in 0xFF1A..0xFF20) return true   // ：；＜＝＞？＠
    if (cp in 0xFF3B..0xFF40) return true   // ［＼］＾＿`
    if (cp in 0xFF5B..0xFF60) return true   // ｛｜｝～
    return false
}

private fun Char.isFullWidthSymbol(): Boolean = this.code.isFullWidthSymbolCodePoint()

private fun prevIsSpace(reader: Reader): Boolean {
    return prevIsIn(reader, emptyChar)
}

private fun prevIsFullWidthChar(reader: Reader): Boolean {
    return reader.position >= 2 && reader.readAt(reader.position - 2).isFullWidthChar()
}

private fun prevIsIn(reader: Reader, chars: List<Char>): Boolean {
    // position == 1 means it is at the beginning of the string, since we consume the first character
    return reader.position == 1 || reader.readAt(reader.position - 2) in chars
}

internal sealed interface State {
    fun read(tokenizer: Tokenizer, reader: Reader)
}

internal data object DataState : State {
    override fun read(tokenizer: Tokenizer, reader: Reader) {
        when (val current = reader.consume()) {
            'h' -> tokenizer.switch(HState)
            '$', '＄' -> tokenizer.switch(DollarState)
            '@', '＠' -> tokenizer.switch(AtState)
            '.' -> tokenizer.switch(DotState)
            '#', '＃' -> tokenizer.switch(HashState)
            ':' -> tokenizer.switch(ColonState)
            eof -> tokenizer.emit(TokenCharacterType.Eof, reader.position)
            else -> tokenizer.emit(TokenCharacterType.Character, reader.position)
        }
    }
}

internal data object ColonState : State {
    override fun read(tokenizer: Tokenizer, reader: Reader) {
        val current = reader.consume()
        if (current in asciiAlphanumericUnderscore && tokenizer.enableEmoji) {
            tokenizer.emit(TokenCharacterType.Emoji, reader.position - 1)
            tokenizer.switch(EmojiState)
            reader.pushback()
        } else {
            tokenizer.emit(TokenCharacterType.Character, reader.position - 1)
            tokenizer.switch(DataState)
            reader.pushback()
        }
    }
}

internal data object EmojiState : State {
    override fun read(tokenizer: Tokenizer, reader: Reader) {
        when (val current = reader.consume()) {
            in asciiAlphanumericUnderscore -> {
                tokenizer.emit(TokenCharacterType.Emoji, reader.position)
            }

            ':' -> {
                tokenizer.emit(TokenCharacterType.Emoji, reader.position)
                tokenizer.accept()
                tokenizer.switch(DataState)
            }

            else -> {
                // reject
                var start = reader.position - 1
                while (start > 0) {
                    if (tokenizer.readAt(start - 1) != TokenCharacterType.Emoji) {
                        break
                    }
                    start--
                }
                tokenizer.emitRange(TokenCharacterType.Character, start, reader.position)
                tokenizer.accept()
                tokenizer.switch(DataState)
                reader.pushback()
            }
        }
    }
}

internal data object HState : State {
    override fun read(tokenizer: Tokenizer, reader: Reader) {
        if (reader.isFollowedBy("ttps://", ignoreCase = true)) {
            tokenizer.emitRange(TokenCharacterType.Url, reader.position - 1, reader.position - 1 + "https://".length)
            tokenizer.switch(UrlState)
            reader.consume("ttps://".length)
        } else if (reader.isFollowedBy("ttp://", ignoreCase = true)) {
            tokenizer.emitRange(TokenCharacterType.Url, reader.position - 1, reader.position - 1 + "http://".length)
            tokenizer.switch(UrlState)
            reader.consume("ttp://".length)
        } else {
            tokenizer.emit(TokenCharacterType.Character, reader.position)
            tokenizer.switch(DataState)
        }
    }
}

internal data object UrlState : State {

    private val urlEscapeChars = listOf(
        '!',
        '~',
        '*',
        '\'',
        '(',
        ')',
        ';',
        ':',
        '+',
        ',',
        '%',
        '[',
        ']',
    )

    private fun urlCheck(tokenizer: Tokenizer, reader: Reader) {
        var index = reader.position - 1
        while (index > 0) {
            val token = tokenizer.readAt(index)
            if (token != TokenCharacterType.Url && token != TokenCharacterType.UnKnown) {
                break
            }
            index--
        }
        val url = reader.readAt(index, reader.position - index)
        if (!url.contains(".")) {
            tokenizer.emitRange(TokenCharacterType.Character, index, reader.position)
            tokenizer.switch(DataState)
            reader.pushback()
        } else {
            tokenizer.accept()
            tokenizer.switch(DataState)
            reader.pushback()
        }
    }

    override fun read(tokenizer: Tokenizer, reader: Reader) {
        when (val current = reader.consume()) {
            in emptyChar + eof -> {
                urlCheck(tokenizer, reader)
            }

            ':' -> {
                if (tokenizer.enableEscapeInUrl) {
                    tokenizer.emit(TokenCharacterType.Url, reader.position)
                } else {
                    val next = reader.next()
                    if (next in asciiDigit) {
                        tokenizer.emit(TokenCharacterType.Url, reader.position)
                        tokenizer.switch(UrlPortState)
                    } else {
                        urlCheck(tokenizer, reader)
                    }
                }
            }

            else -> {
                if (current in urlEscapeChars) {
                    if (tokenizer.enableEscapeInUrl) {
                        val next = reader.next()
                        if (next in emptyChar + eof) {
                            urlCheck(tokenizer, reader)
                        } else {
                            tokenizer.emit(TokenCharacterType.Url, reader.position)
                        }
                    } else {
                        urlCheck(tokenizer, reader)
                    }
                } else {
                    if (!current.isLetterOrDigit()) {
                        val next = reader.next()
                        if (current in marks) {
                            if (next in emptyChar + eof) {
                                if (current in urlDisallowedEndMarks) {
                                    urlCheck(tokenizer, reader)
                                } else {
                                    tokenizer.emit(TokenCharacterType.Url, reader.position)
                                }
                            } else {
                                tokenizer.emit(TokenCharacterType.Url, reader.position)
                            }
                        } else {
                            if (next in emptyChar + eof) {
                                urlCheck(tokenizer, reader)
                            } else {
                                if (tokenizer.enableNonAsciiInUrl) {
                                    tokenizer.emit(TokenCharacterType.Url, reader.position)
                                } else {
                                    urlCheck(tokenizer, reader)
                                }
                            }
                        }
                    } else {
                        if (tokenizer.enableNonAsciiInUrl) {
                            tokenizer.emit(TokenCharacterType.Url, reader.position)
                        } else {
                            if (current in urlChar) {
                                tokenizer.emit(TokenCharacterType.Url, reader.position)
                            } else {
                                urlCheck(tokenizer, reader)
                            }
                        }
                    }
                }
            }
        }
    }
}

internal data object UrlPortState : State {
    override fun read(tokenizer: Tokenizer, reader: Reader) {
        when (val current = reader.consume()) {
            in asciiDigit -> {
                tokenizer.emit(TokenCharacterType.Url, reader.position)
            }

            in listOf('/', '?') -> {
                tokenizer.emit(TokenCharacterType.Url, reader.position)
                tokenizer.switch(UrlState)
            }

            in emptyChar + eof -> {
                tokenizer.accept()
                tokenizer.switch(DataState)
                reader.pushback()
            }

            else -> {
                tokenizer.accept()
                tokenizer.switch(DataState)
                reader.pushback()
            }
        }
    }
}

internal data object DollarState : State {
    override fun read(tokenizer: Tokenizer, reader: Reader) {
        if (prevIsSpace(reader)) {
            when (val current = reader.consume()) {
                in asciiAlpha -> {
                    tokenizer.emit(TokenCharacterType.Cash, reader.position - 1)
                    tokenizer.switch(CashTagState)
                    reader.pushback()
                }

                else -> {
                    tokenizer.emit(TokenCharacterType.Character, reader.position - 1)
                    tokenizer.switch(DataState)
                    reader.pushback()
                }
            }
        } else {
            tokenizer.emit(TokenCharacterType.Character, reader.position)
            tokenizer.switch(DataState)
        }
    }
}

internal data object CashTagState : State {
    private fun cashCheck(tokenizer: Tokenizer, reader: Reader) {
        var index = reader.position - 2
        while (index > 0) {
            if (tokenizer.readAt(index) != TokenCharacterType.Cash) {
                break
            }
            index--
        }
        val cash = reader.readAt(index + 1, reader.position - index - 2).trimStart('$').trimStart('＄')
        if (cash.all { it in asciiDigit }) {
            tokenizer.emitRange(TokenCharacterType.Character, index, reader.position)
            tokenizer.switch(DataState)
            reader.pushback()
        } else {
            tokenizer.accept()
            tokenizer.switch(DataState)
            reader.pushback()
        }
    }


    override fun read(tokenizer: Tokenizer, reader: Reader) {
        when (val current = reader.consume()) {
            in asciiAlphanumeric -> {
                tokenizer.emit(TokenCharacterType.Cash, reader.position)
            }

            else -> {
                cashCheck(tokenizer, reader)
//                tokenizer.accept()
//                tokenizer.switch(DataState)
//                reader.pushback()
            }
        }
    }
}

internal data object AtState : State {
    override fun read(tokenizer: Tokenizer, reader: Reader) {
        val availblePrevChar = asciiAlphanumeric + tokenizer.validMarkInUserName
        if (prevIsSpace(reader) || !prevIsIn(reader, availblePrevChar) || prevIsFullWidthChar(reader)) {
            val userNameTokens = asciiAlphanumericUnderscore + tokenizer.validMarkInUserName
            when (val current = reader.consume()) {
                in userNameTokens -> {
                    tokenizer.emit(TokenCharacterType.UserName, reader.position - 1)
                    tokenizer.switch(UserNameState)
                    reader.pushback()
                }

                else -> {
                    tokenizer.emit(TokenCharacterType.Character, reader.position - 1)
                    tokenizer.switch(DataState)
                    reader.pushback()
                }
            }
        } else {
            tokenizer.emit(TokenCharacterType.Character, reader.position)
            tokenizer.switch(DataState)
        }
    }
}

internal data object UserNameState : State {
    override fun read(tokenizer: Tokenizer, reader: Reader) {
        val userNameTokens = asciiAlphanumericUnderscore
        when (val current = reader.consume()) {
            in userNameTokens -> {
                tokenizer.emit(TokenCharacterType.UserName, reader.position)
            }
            in tokenizer.validMarkInUserName -> {
                val next = reader.next()
                if (next.isLetterOrDigit()) {
                    tokenizer.emit(TokenCharacterType.UserName, reader.position)
                } else {
                    tokenizer.accept()
                    tokenizer.switch(DataState)
                    reader.pushback()
                }
            }
            else -> {
                tokenizer.accept()
                tokenizer.switch(DataState)
                reader.pushback()
            }
        }
    }
}

private fun findBackwardValidUrl(reader: Reader): Int {
    var position = reader.position
    while (position > 0) {
        if (reader.readAt(position - 1) !in urlChar) {
            // not -1 because we want the position right after the text
            return position
        }
        position--
    }
    // is at the beginning of the string
    return 0
}

internal data object DotState : State {
    override fun read(tokenizer: Tokenizer, reader: Reader) {
        if (reader.readAt(reader.position) in asciiAlphanumeric && tokenizer.enableDomainDetection &&
            reader.position > 1 &&
            tokenizer.readAt(reader.position - 2) == TokenCharacterType.Character) {
            val start = findBackwardValidUrl(reader)
            if (!reader.readAt(start).isLetterOrDigit()) {
                tokenizer.emit(TokenCharacterType.Character, reader.position)
                tokenizer.switch(DataState)
            } else {
                tokenizer.emitRange(TokenCharacterType.Url, start, reader.position)
                tokenizer.switch(HeadlessUrlState)
            }
        } else {
            tokenizer.emit(TokenCharacterType.Character, reader.position)
            tokenizer.switch(DataState)
        }
    }
}

internal data object HeadlessUrlState : State {
    override fun read(tokenizer: Tokenizer, reader: Reader) {
        when (val current = reader.consume()) {
            !in asciiAlphanumeric -> {
                reader.pushback()
                val start = findBackwardValidUrl(reader)
                val value = reader.readAt(start, reader.position - start).split('.').lastOrNull()?.takeWhile {
                    it in asciiAlpha
                }
                if (value != null && DomainList.any { it.equals(value, ignoreCase = true) }) {
                    tokenizer.emitRange(TokenCharacterType.Url, start, reader.position)
                } else {
                    tokenizer.emitRange(TokenCharacterType.Character, start, reader.position)
                }
                tokenizer.accept()
                tokenizer.switch(DataState)
            }

            else -> {
                tokenizer.emit(TokenCharacterType.Url, reader.position)
            }
        }
    }
}

internal data object HashState : State {
    override fun read(tokenizer: Tokenizer, reader: Reader) {
        if (prevIsSpace(reader) || prevIsFullWidthChar(reader)) {
            val current = reader.consume()
            if (current.isLetterOrDigit() || current == '_' || current in tokenizer.validMarkInHashTag || current.isFullWidthChar()) {
                tokenizer.emit(TokenCharacterType.HashTag, reader.position - 1)
                tokenizer.switch(HashTagState)
                reader.pushback()
            } else {
                tokenizer.emit(TokenCharacterType.Character, reader.position - 1)
                tokenizer.switch(DataState)
                reader.pushback()
            }
        } else {
            tokenizer.emit(TokenCharacterType.Character, reader.position)
            tokenizer.switch(DataState)
        }
    }
}

internal data object HashTagState : State {
    override fun read(tokenizer: Tokenizer, reader: Reader) {
        val current = reader.consume()
        if (current.isLetterOrDigit() || current == '_' || current in tokenizer.validMarkInHashTag || (current.isFullWidthChar() && !current.isFullWidthSymbol())) {
            tokenizer.emit(TokenCharacterType.HashTag, reader.position)
        } else {
            tokenizer.accept()
            tokenizer.switch(DataState)
            reader.pushback()
        }
    }
}
