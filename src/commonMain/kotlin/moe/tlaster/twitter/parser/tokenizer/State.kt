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
private val urlChar = asciiAlphanumeric + "-._~:/?#[]@!\$&'()*+,;=".toList()

private fun prevIsSpace(reader: Reader): Boolean {
    // position == 1 means it is at the beginning of the string, since we consume the first character
    return reader.position == 1 || reader.readAt(reader.position - 2) in emptyChar
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
        var index = reader.position - 2
        while (index > 0) {
            if (tokenizer.readAt(index) != TokenCharacterType.Url) {
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
//                    tokenizer.accept()
//                    tokenizer.switch(DataState)
//                    reader.pushback()
                        urlCheck(tokenizer, reader)
                    }
                }
            }

            else -> {
                if (current in urlEscapeChars) {
                    if (tokenizer.enableEscapeInUrl) {
                        tokenizer.emit(TokenCharacterType.Url, reader.position)
                    } else {
//                        tokenizer.accept()
//                        tokenizer.switch(DataState)
//                        reader.pushback()
                        urlCheck(tokenizer, reader)
                    }
                } else {
                    if (!current.isLetterOrDigit() && current != '/') {
                        val next = reader.next()
                        if (next in emptyChar + eof) {
//                            tokenizer.accept()
//                            tokenizer.switch(DataState)
//                            reader.pushback()
                            urlCheck(tokenizer, reader)
                        } else {
                            tokenizer.emit(TokenCharacterType.Url, reader.position)
                        }
                    } else {
                        if (tokenizer.enableNonAsciiInUrl) {
                            tokenizer.emit(TokenCharacterType.Url, reader.position)
                        } else {
                            if (current in urlChar) {
                                tokenizer.emit(TokenCharacterType.Url, reader.position)
                            } else {
//                                tokenizer.accept()
//                                tokenizer.switch(DataState)
//                                reader.pushback()
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
                in asciiAlphanumeric -> {
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
    override fun read(tokenizer: Tokenizer, reader: Reader) {
        when (val current = reader.consume()) {
            in asciiAlphanumeric -> {
                tokenizer.emit(TokenCharacterType.Cash, reader.position)
            }

            else -> {
                tokenizer.accept()
                tokenizer.switch(DataState)
                reader.pushback()
            }
        }
    }
}

internal data object AtState : State {
    override fun read(tokenizer: Tokenizer, reader: Reader) {
        if (prevIsSpace(reader)) {
            when (val current = reader.consume()) {
                in asciiAlphanumericUnderscoreDash -> {
                    tokenizer.emit(TokenCharacterType.UserName, reader.position - 1)
                    tokenizer.switch(UserNameState)
                    reader.pushback()
                }

                else -> {
                    if (tokenizer.allowAllTextInUserName) {
                        if (current in emptyChar + eof) {
                            tokenizer.accept()
                            tokenizer.switch(DataState)
                            reader.pushback()
                        } else {
                            tokenizer.emit(TokenCharacterType.UserName, reader.position - 1)
                            tokenizer.switch(UserNameState)
                            reader.pushback()
                        }
                    } else {
                        tokenizer.emit(TokenCharacterType.Character, reader.position - 1)
                        tokenizer.switch(DataState)
                        reader.pushback()
                    }
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
        val userNameTokens = if (tokenizer.enableDotInUserName) {
            asciiAlphanumericUnderscore + '.'
        } else {
            asciiAlphanumericUnderscore
        }
        when (val current = reader.consume()) {
            in userNameTokens -> {
                tokenizer.emit(TokenCharacterType.UserName, reader.position)
            }

            '@' -> {
                if (tokenizer.enableAcct) {
                    val next = reader.next()
                    if (next.isLetterOrDigit()) {
                        tokenizer.emit(TokenCharacterType.UserName, reader.position)
                        tokenizer.switch(UserAcctState)
                    } else {
                        var start = reader.position - 1
                        while (start > 0) {
                            if (tokenizer.readAt(start - 1) != TokenCharacterType.UserName) {
                                break
                            }
                            start--
                        }
                        tokenizer.emitRange(TokenCharacterType.Character, start, reader.position)
                        tokenizer.accept()
                        tokenizer.switch(DataState)
                        reader.pushback()
                    }
                } else if (tokenizer.allowAllTextInUserName) {
                    tokenizer.emit(TokenCharacterType.UserName, reader.position)
                } else {
                    tokenizer.accept()
                    tokenizer.switch(DataState)
                    reader.pushback()
                }
            }

            else -> {
                if (tokenizer.allowAllTextInUserName) {
                    if (current in emptyChar + eof + '#' + '$') {
                        tokenizer.accept()
                        tokenizer.switch(DataState)
                        reader.pushback()
                    } else {
                        tokenizer.emit(TokenCharacterType.UserName, reader.position)
                    }
                } else {
                    tokenizer.accept()
                    tokenizer.switch(DataState)
                    reader.pushback()
                }
            }
        }
    }
}

internal data object UserAcctState : State {
    override fun read(tokenizer: Tokenizer, reader: Reader) {
        when (val current = reader.consume()) {
            in asciiAlphanumericUnderscore + '.' -> {
                tokenizer.emit(TokenCharacterType.UserName, reader.position)
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
        if (prevIsSpace(reader)) {
            val current = reader.consume()
            if (current.isLetterOrDigit() || current == '_') {
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
        if (current.isLetterOrDigit() || current == '_') {
            tokenizer.emit(TokenCharacterType.HashTag, reader.position)
        } else {
            tokenizer.accept()
            tokenizer.switch(DataState)
            reader.pushback()
        }
    }
}
