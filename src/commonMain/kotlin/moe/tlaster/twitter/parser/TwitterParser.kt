package moe.tlaster.twitter.parser

class TwitterParser(
    private val enableAcct: Boolean = false,
    private val enableEmoji: Boolean = false,
    private val enableDotInUserName: Boolean = false,
    private val enableDomainDetection: Boolean = false,
    private val enableNonAsciiInUrl: Boolean = true,
    private val enableEscapeInUrl: Boolean = false,
    private val allowAllTextInUserName: Boolean = false,
) {
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
    private val digits = '0'..'9'
    private val letters = ('a'..'z') + ('A'..'Z')

    private val userNameCharList = letters + digits + '_'
    private val urlCharList = letters + digits + listOf(
        '.', '@', ':', '%', '_', '\\', '+', '~', '#', '?', '&', '/', '='
    )

    private enum class State {
        AccSpace,
        Content,
        InUserName,
        InUserNameAcct,
        InHashTag,
        InCashTag,
        MightUrlH,
        MightUrlT1,
        MightUrlT2,
        MightUrlS,
        MightUrlP,
        MightUrlDot,
        MightUrlSlash1,
        MightUrlPort,
        InUrl,
        InEmoji,
        MightDomain,
    }

    private enum class Type {
        Content,
        UserName,
        HashTag,
        CashTag,
        Url,
        Emoji,
    }

    fun parse(value: String): List<Token> {
        val contentBuilder = arrayListOf(
            Type.Content to StringBuilder()
        )
        var state = State.AccSpace
        for (char in value) {
            when (char) {
                in UserNameToken.Tags -> {
                    state = when {
                        state == State.AccSpace -> {
                            contentBuilder.add(Type.UserName to StringBuilder())
                            State.InUserName
                        }

                        allowAllTextInUserName && state == State.InUserName -> {
                            state
                        }

                        enableAcct && state == State.InUserName -> {
                            State.InUserNameAcct
                        }

                        enableAcct && state == State.InUserNameAcct -> {
                            userAcctCheck(contentBuilder)
                        }

                        state != State.Content && state != State.InUrl -> {
                            accept(contentBuilder)
                        }

                        else -> {
                            state
                        }
                    }
                    contentBuilder.last().second.append(char)
                }

                in HashTagToken.Tags -> {
                    state = if (state == State.AccSpace) {
                        contentBuilder.add(Type.HashTag to StringBuilder())
                        State.InHashTag
                    } else if (state != State.Content && state != State.InUrl) {
                        accept(contentBuilder)
                    } else {
                        state
                    }
                    contentBuilder.last().second.append(char)
                }

                in CashTagToken.Tags -> {
                    state = if (state == State.AccSpace) {
                        contentBuilder.add(Type.CashTag to StringBuilder())
                        State.InCashTag
                    } else if (state != State.Content && state != State.InUrl) {
                        accept(contentBuilder)
                    } else {
                        state
                    }
                    contentBuilder.last().second.append(char)
                }

                'h', 'H' -> {
                    state = when (state) {
                        State.Content, State.AccSpace -> {
                            contentBuilder.add(Type.Url to StringBuilder())
                            State.MightUrlH
                        }
                        State.MightUrlPort,
                        -> {
                            urlPortCheck(contentBuilder)
                        }
                        else -> state
                    }
                    contentBuilder.last().second.append(char)
                }

                't', 'T' -> {
                    state = when (state) {
                        State.MightUrlH -> State.MightUrlT1
                        State.MightUrlT1 -> State.MightUrlT2
                        State.MightUrlPort,
                        -> {
                            urlPortCheck(contentBuilder)
                        }
                        else -> state
                    }
                    contentBuilder.last().second.append(char)
                }

                'p', 'P' -> {
                    state = when (state) {
                        State.MightUrlT2 -> State.MightUrlP
                        State.MightUrlPort,
                        -> {
                            urlPortCheck(contentBuilder)
                        }
                        else -> state
                    }
                    contentBuilder.last().second.append(char)
                }

                's', 'S' -> {
                    state = when (state) {
                        State.MightUrlP -> State.MightUrlS
                        State.MightUrlPort,
                        -> {
                            urlPortCheck(contentBuilder)
                        }
                        else -> state
                    }
                    contentBuilder.last().second.append(char)
                }

                ':' -> {
                    state = when (state) {
                        State.MightUrlP -> State.MightUrlDot
                        State.MightUrlS -> State.MightUrlDot

                        State.InUserNameAcct -> {
                            if (!allowAllTextInUserName) {
                                userAcctCheck(contentBuilder)
                            } else {
                                state
                            }
                        }

                        State.InUserName -> {
                            if (!allowAllTextInUserName) {
                                lengthCheck(contentBuilder)
                            } else {
                                state
                            }
                        }

                        State.InCashTag,
                        State.InHashTag,
                        -> {
                            lengthCheck(contentBuilder)
                        }

                        State.AccSpace -> {
                            if (enableEmoji) {
                                contentBuilder.add(Type.Emoji to StringBuilder())
                                State.InEmoji
                            } else {
                                state
                            }
                        }

                        State.InUrl -> {
                            val last = contentBuilder.last().second
                            if (
                                last.indexOf(':', startIndex = "https://".length) < 0 &&
                                last.indexOf('/', startIndex = "https://".length) < 0 &&
                                !enableEscapeInUrl
                            ) {
                                State.MightUrlPort
                            } else if (enableEscapeInUrl) {
                                state
                            } else {
                                urlCheck(contentBuilder)
                            }
                        }

                        State.MightDomain -> {
                            domainCheck(contentBuilder)
                        }

                        else -> state
                    }
                    contentBuilder.last().second.append(char)
                }

                '/' -> {
                    state = when (state) {
                        State.MightUrlDot -> State.MightUrlSlash1
                        State.MightUrlSlash1 -> State.InUrl

                        State.InUserNameAcct -> {
                            if (!allowAllTextInUserName) {
                                userAcctCheck(contentBuilder)
                            } else {
                                state
                            }
                        }

                        State.InUserName -> {
                            if (!allowAllTextInUserName) {
                                lengthCheck(contentBuilder)
                            } else {
                                state
                            }
                        }
                        State.InHashTag,
                        State.InCashTag,
                        -> {
                            lengthCheck(contentBuilder)
                        }

                        State.MightDomain -> {
                            domainCheck(contentBuilder)
                        }
                        State.MightUrlPort,
                        -> {
                            urlPortCheck(contentBuilder)
                        }

                        else -> state
                    }
                    contentBuilder.last().second.append(char)
                }

                ' ', '\n', '　' -> {
                    when (state) {
                        State.Content -> Unit
                        State.AccSpace -> Unit
                        State.InUserNameAcct,
                        -> {
                            userAcctCheck(contentBuilder)
                        }

                        State.InUserName,
                        State.InHashTag,
                        State.InCashTag,
                        -> {
                            lengthCheck(contentBuilder)
                        }

                        State.InUrl -> {
                            urlCheck(contentBuilder)
                        }

                        State.InEmoji,
                        State.MightUrlH,
                        State.MightUrlT1,
                        State.MightUrlT2,
                        State.MightUrlS,
                        State.MightUrlP,
                        State.MightUrlDot,
                        State.MightUrlSlash1,
                        -> {
                            reject(contentBuilder)
                        }

                        State.MightUrlPort,
                        -> {
                            urlPortCheck(contentBuilder)
                        }

                        State.MightDomain -> {
                            domainCheck(contentBuilder)
                        }
                    }
                    state = State.AccSpace
                    contentBuilder.last().second.append(char)
                }

                else -> {
                    when (state) {
                        State.InUserName -> {
                            if (!userNameCharList.contains(char) && (!enableDotInUserName || char != '.') && !allowAllTextInUserName) {
                                state = if (contentBuilder.last().second.last() in UserNameToken.Tags) {
                                    reject(contentBuilder)
                                } else {
                                    accept(contentBuilder)
                                }
                            }
                        }

                        State.InUserNameAcct -> {
                            if (!userNameCharList.contains(char) && (char != '.' && enableAcct) && !allowAllTextInUserName) {
                                state = if (contentBuilder.last().second.last() in UserNameToken.Tags) {
                                    reject(contentBuilder)
                                } else {
                                    accept(contentBuilder)
                                }
                            }
                        }

                        State.InEmoji -> {
                            if (!char.isLetterOrDigit() && char != '_') {
                                state = emojiCheck(contentBuilder)
                            }
                        }


                        State.InHashTag -> {
                            if (!char.isLetterOrDigit() && char != '_') {
                                state = accept(contentBuilder)
                            }
                        }

                        State.InCashTag -> {
                            if (!letters.contains(char)) {
                                state = reject(contentBuilder)
                            }
                        }

                        State.InUrl -> {
                            if (char in urlEscapeChars && !enableEscapeInUrl) {
                                state = urlCheck(contentBuilder)
                            } else if (!enableNonAsciiInUrl && char !in urlCharList) {
                                state = accept(contentBuilder)
                            }
                        }

                        State.MightUrlH,
                        State.MightUrlT1,
                        State.MightUrlT2,
                        State.MightUrlS,
                        State.MightUrlP,
                        State.MightUrlDot,
                        State.MightUrlSlash1,
                        -> state = reject(contentBuilder)

                        State.MightUrlPort -> {
                            if (char == '?') {
                                state = State.InUrl
                            } else if (char !in '0'..'9') {
                                state = urlPortCheck(contentBuilder)
                            }
                        }

                        State.MightDomain -> {
                            val lastContent = contentBuilder.last().second.split('.')
                            val mightDomain = if (lastContent.size > 1) {
                                lastContent.last() + char
                            } else {
                                char.toString()
                            }
                            if (!UrlToken.Domains.any { it.startsWith(mightDomain, ignoreCase = true) }) {
                                state = reject(contentBuilder)
                            }
                        }

                        State.Content,
                        State.AccSpace,
                        -> {
                            if (char == '.' && enableDomainDetection) {
                                // find string after space in contentBuilder
                                val lastContentBuilder = StringBuilder()
                                for (c in contentBuilder.last().second.reversed()) {
                                    if (c == ' ' || c == '\n' || c == '　') {
                                        break
                                    }
                                    lastContentBuilder.insert(0, c)
                                }
                                if (lastContentBuilder.all { it.isLetterOrDigit() }) {
                                    state = State.MightDomain
                                    if (contentBuilder.any()) {
                                        contentBuilder.last().second.deleteRange(
                                            contentBuilder.last().second.length - lastContentBuilder.length,
                                            contentBuilder.last().second.length
                                        )
                                    } else {
                                        contentBuilder.removeLast()
                                    }
                                    contentBuilder.add(Type.Url to lastContentBuilder)
                                }
                            }
                        }
                    }
                    contentBuilder.last().second.append(char)
                }
            }
        }
        endCheck(state, contentBuilder)

        return contentBuilder.filter { it.second.isNotEmpty() }.map {
            when (it.first) {
                Type.Content -> StringToken(it.second.toString())
                Type.HashTag -> HashTagToken(it.second.toString())
                Type.CashTag -> CashTagToken(it.second.toString())
                Type.Url -> UrlToken(it.second.toString())
                Type.UserName -> UserNameToken(it.second.toString())
                Type.Emoji -> EmojiToken(it.second.toString())
            }
        }
    }

    private fun endCheck(
        state: State,
        contentBuilder: ArrayList<Pair<Type, StringBuilder>>,
    ) {
        when (state) {
            State.MightDomain -> {
                domainCheck(contentBuilder)
            }

            State.MightUrlH,
            State.MightUrlT1,
            State.MightUrlT2,
            State.MightUrlS,
            State.MightUrlP,
            State.MightUrlDot,
            State.MightUrlSlash1,
            State.MightUrlPort,
            -> {
                reject(contentBuilder)
            }

            State.InCashTag,
            State.InHashTag,
            State.InUserName,
            -> {
                lengthCheck(contentBuilder)
            }

            State.InUrl -> {
                urlCheck(contentBuilder)
            }

            State.InEmoji -> {
                emojiCheck(contentBuilder)
            }

            State.InUserNameAcct -> {
                userAcctCheck(contentBuilder)
            }

            State.AccSpace -> Unit
            State.Content -> Unit
        }
    }

    private fun lengthCheck(contentBuilder: ArrayList<Pair<Type, StringBuilder>>): State {
        val last = contentBuilder.last().second
        return if (last.length > 1) {
            accept(contentBuilder)
        } else {
            reject(contentBuilder)
        }
    }

    private fun userAcctCheck(contentBuilder: ArrayList<Pair<Type, StringBuilder>>): State {
        val last = contentBuilder.last().second
        return if (last.startsWith("@") && last.length > 1 && last.last() !in UserNameToken.Tags) {
            accept(contentBuilder)
        } else {
            reject(contentBuilder)
        }
    }

    private fun emojiCheck(contentBuilder: ArrayList<Pair<Type, StringBuilder>>): State {
        val last = contentBuilder.last().second
        return if (last.startsWith(":") && last.endsWith(":")) {
            accept(contentBuilder)
        } else {
            reject(contentBuilder)
        }
    }

    private fun domainCheck(contentBuilder: ArrayList<Pair<Type, StringBuilder>>): State {
        val last = contentBuilder.last().second.split('.').last()
        return if (UrlToken.Domains.any { it.equals(last, ignoreCase = true) }) {
            accept(contentBuilder)
        } else {
            reject(contentBuilder)
        }
    }

    private fun urlPortCheck(contentBuilder: ArrayList<Pair<Type, StringBuilder>>): State {
        val last = contentBuilder.last().second
        return if (last.lastIndexOf(':') != last.lastIndex) {
            accept(contentBuilder)
        } else {
            last.deleteAt(last.lastIndex)
            val nextState = accept(contentBuilder)
            contentBuilder.last().second.append(":")
            nextState
        }
    }

    private fun urlCheck(contentBuilder: ArrayList<Pair<Type, StringBuilder>>): State {
        // check if url is valid
        val last = contentBuilder.last().second
        if (!last.contains('.')) {
            return reject(contentBuilder)
        }
        if (last.last() == '.') {
            last.deleteAt(last.lastIndex)
            val nextState = accept(contentBuilder)
            contentBuilder.last().second.append(".")
            return nextState
        }
        // TODO: check if url is valid
        return accept(contentBuilder)
    }

    private fun reject(contentBuilder: ArrayList<Pair<Type, StringBuilder>>): State {
        // Not a valid hashtag/username/cashtag/http/emoji
        val last = contentBuilder.removeLast()
        if (contentBuilder.any()) {
            contentBuilder.last().second.append(last.second)
        } else {
            contentBuilder.add(Type.Content to last.second)
        }
        return State.Content
    }

    private fun accept(contentBuilder: ArrayList<Pair<Type, StringBuilder>>): State {
        // ACC for hashtag/username/cashtag/http/emoji
        contentBuilder.add(Type.Content to StringBuilder())
        return State.Content
    }
}

