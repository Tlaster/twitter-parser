package moe.tlaster.twitter.parser

class TwitterParser(
    private val enableAcct: Boolean = false,
    private val enableEmoji: Boolean = false,
    private val enableDotInUserName: Boolean = false,
    private val enableDomainDetection: Boolean = false,
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

                        enableAcct && state == State.InUserName -> {
                            State.InUserNameAcct
                        }

                        enableAcct && state == State.InUserNameAcct -> {
                            accept(contentBuilder)
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
                    if (state == State.Content || state == State.AccSpace) {
                        state = State.MightUrlH
                        contentBuilder.add(Type.Url to StringBuilder())
                    }
                    contentBuilder.last().second.append(char)
                }

                't', 'T' -> {
                    state = when (state) {
                        State.MightUrlH -> State.MightUrlT1
                        State.MightUrlT1 -> State.MightUrlT2
                        else -> state
                    }
                    contentBuilder.last().second.append(char)
                }

                'p', 'P' -> {
                    state = when (state) {
                        State.MightUrlT2 -> State.MightUrlP
                        else -> state
                    }
                    contentBuilder.last().second.append(char)
                }

                's', 'S' -> {
                    state = when (state) {
                        State.MightUrlP -> State.MightUrlS
                        else -> state
                    }
                    contentBuilder.last().second.append(char)
                }

                ':' -> {
                    state = when (state) {
                        State.MightUrlP -> State.MightUrlDot
                        State.MightUrlS -> State.MightUrlDot
                        State.InUserName,
                        State.InUserNameAcct,
                        State.InCashTag,
                        State.InHashTag,
                        -> accept(contentBuilder)

                        State.AccSpace -> {
                            if (enableEmoji) {
                                contentBuilder.add(Type.Emoji to StringBuilder())
                                State.InEmoji
                            } else {
                                state
                            }
                        }

                        State.InUrl -> {
                            accept(contentBuilder)
                        }

                        State.MightDomain -> {
                            val last = contentBuilder.last().second.split('.').last()
                            if (UrlToken.Domains.any { it.equals(last, ignoreCase = true) }) {
                                accept(contentBuilder)
                            } else {
                                reject(contentBuilder)
                            }
                        }

                        else -> state
                    }
                    contentBuilder.last().second.append(char)
                }

                '/' -> {
                    state = when (state) {
                        State.MightUrlDot -> State.MightUrlSlash1
                        State.MightUrlSlash1 -> State.InUrl
                        State.InUserName,
                        State.InUserNameAcct,
                        State.InHashTag,
                        State.InCashTag,
                        -> accept(contentBuilder)
                        State.MightDomain -> {
                            val last = contentBuilder.last().second.split('.').last()
                            if (UrlToken.Domains.any { it.equals(last, ignoreCase = true) }) {
                                accept(contentBuilder)
                            } else {
                                reject(contentBuilder)
                            }
                        }

                        else -> state
                    }
                    contentBuilder.last().second.append(char)
                }

                ' ', '\n', '　' -> {
                    when (state) {
                        State.Content -> Unit
                        State.AccSpace -> Unit
                        State.InUserName,
                        State.InUserNameAcct,
                        State.InHashTag,
                        State.InCashTag,
                        State.InUrl,
                        -> {
                            accept(contentBuilder)
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
                        State.MightDomain -> {
                            val last = contentBuilder.last().second.split('.').last()
                            if (UrlToken.Domains.any { it.equals(last, ignoreCase = true) }) {
                                accept(contentBuilder)
                            } else {
                                reject(contentBuilder)
                            }
                        }
                    }
                    state = State.AccSpace
                    contentBuilder.last().second.append(char)
                }

                else -> {
                    when (state) {
                        State.InUserName -> {
                            if (!userNameCharList.contains(char) && (!enableDotInUserName || char != '.')) {
                                state = if (contentBuilder.last().second.last() in UserNameToken.Tags) {
                                    reject(contentBuilder)
                                } else {
                                    accept(contentBuilder)
                                }
                            }
                        }

                        State.InUserNameAcct -> {
                            if (!userNameCharList.contains(char) && (char != '.' && enableAcct)) {
                                state = if (contentBuilder.last().second.last() in UserNameToken.Tags) {
                                    reject(contentBuilder)
                                } else {
                                    accept(contentBuilder)
                                }
                            }
                        }

                        State.InEmoji -> {
                            if (!char.isLetterOrDigit() && char != '_') {
                                state = if (contentBuilder.last().second.last() in EmojiToken.Tags) {
                                    reject(contentBuilder)
                                } else {
                                    accept(contentBuilder)
                                }
                            }
                        }


                        State.InHashTag -> {
                            if (!char.isLetterOrDigit() && char != '_') {
                                state = if (contentBuilder.last().second.last() in HashTagToken.Tags) {
                                    reject(contentBuilder)
                                } else {
                                    accept(contentBuilder)
                                }
                            }
                        }

                        State.InCashTag -> {
                            if (!letters.contains(char)) {
                                state = if (contentBuilder.last().second.last() in CashTagToken.Tags) {
                                    reject(contentBuilder)
                                } else {
                                    accept(contentBuilder)
                                }
                            }
                        }

                        State.InUrl -> {
                            if (char in urlEscapeChars) {
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
        return contentBuilder.filter { it.second.isNotEmpty() }.map {
            when (it.first) {
                Type.Content -> StringToken(it.second.toString())
                Type.HashTag -> HashTagToken(it.second.toString())
                Type.CashTag -> CashTagToken(it.second.toString())
                Type.Url -> {
                    val second = it.second.toString()
                    UrlToken(second)
                }

                Type.UserName -> UserNameToken(it.second.toString())
                Type.Emoji -> EmojiToken(it.second.toString())
            }
        }
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

