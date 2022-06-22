package moe.tlaster.twitter.parser

class TwitterParser {
    private val urlRegex =
        "https?:\\/\\/(www\\.)?[-a-zA-Z0-9@:%._\\+~#=]{1,256}\\.[a-zA-Z0-9()]{1,6}\\b([-a-zA-Z0-9()@:%_\\+.~#?&//=]*)".toRegex()
    private val digits = '0'..'9'
    private val letters = ('a'..'z') + ('A'..'Z')

    private val userNameCharList = letters + digits + '_'

    private enum class State {
        AccSpace,
        Content,
        InUserName,
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
    }

    private enum class Type {
        Content,
        UserName,
        HashTag,
        CashTag,
        Url,
    }

    fun parse(value: String): List<Token> {
        val contentBuilder = arrayListOf(
            Type.Content to StringBuilder()
        )
        var state = State.AccSpace
        for (char in value) {
            when (char) {
                UserNameToken.Tag -> {
                    state = if (state == State.AccSpace) {
                        contentBuilder.add(Type.UserName to StringBuilder())
                        State.InUserName
                    } else if (state != State.Content && state != State.InUrl) {
                        accept(contentBuilder)
                    } else {
                        state
                    }
                    contentBuilder.last().second.append(char)
                }

                HashTagToken.Tag -> {
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

                CashTagToken.Tag -> {
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
                        State.InCashTag,
                        State.InHashTag -> accept(contentBuilder)
                        else -> state
                    }
                    contentBuilder.last().second.append(char)
                }

                '/' -> {
                    state = when (state) {
                        State.MightUrlDot -> State.MightUrlSlash1
                        State.MightUrlSlash1 -> State.InUrl
                        State.InUserName,
                        State.InHashTag,
                        State.InCashTag -> accept(contentBuilder)
                        else -> state
                    }
                    contentBuilder.last().second.append(char)
                }

                ' ' -> {
                    state = when (state) {
                        State.Content -> State.AccSpace
                        State.AccSpace -> State.AccSpace
                        State.InUserName,
                        State.InHashTag,
                        State.InCashTag,
                        State.InUrl -> {
                            accept(contentBuilder)
                            State.AccSpace
                        }

                        State.MightUrlH,
                        State.MightUrlT1,
                        State.MightUrlT2,
                        State.MightUrlS,
                        State.MightUrlP,
                        State.MightUrlDot,
                        State.MightUrlSlash1 -> reject(contentBuilder)
                    }
                    contentBuilder.last().second.append(char)
                }

                else -> {
                    when (state) {
                        State.InUserName -> {
                            if (!userNameCharList.contains(char)) {
                                state = if (contentBuilder.last().second.last() == UserNameToken.Tag) {
                                    reject(contentBuilder)
                                } else {
                                    accept(contentBuilder)
                                }
                            }
                        }

                        State.InHashTag -> {
                            if (!char.isLetterOrDigit()) {
                                state = if (contentBuilder.last().second.last() == HashTagToken.Tag) {
                                    reject(contentBuilder)
                                } else {
                                    accept(contentBuilder)
                                }
                            }
                        }

                        State.InCashTag -> {
                            if (!letters.contains(char)) {
                                state = if (contentBuilder.last().second.last() == CashTagToken.Tag) {
                                    reject(contentBuilder)
                                } else {
                                    accept(contentBuilder)
                                }
                            }
                        }

                        State.MightUrlH,
                        State.MightUrlT1,
                        State.MightUrlT2,
                        State.MightUrlS,
                        State.MightUrlP,
                        State.MightUrlDot,
                        State.MightUrlSlash1 -> state = reject(contentBuilder)
                        else -> Unit
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
                    if (urlRegex.matches(second)) {
                        UrlToken(second)
                    } else {
                        StringToken(second)
                    }
                }

                Type.UserName -> UserNameToken(it.second.toString())
            }
        }
    }

    private fun reject(contentBuilder: ArrayList<Pair<Type, StringBuilder>>): State {
        // Not a valid hashtag/username/cashtag/http
        val last = contentBuilder.removeLast()
        contentBuilder.last().second.append(last.second)
        return State.Content
    }

    private fun accept(contentBuilder: ArrayList<Pair<Type, StringBuilder>>): State {
        // ACC for hashtag/username/cashtag/http
        contentBuilder.add(Type.Content to StringBuilder())
        return State.Content
    }
}