package moe.tlaster.twitter.parser

sealed interface Token {
    val value: String
}
data class CashTagToken(
    override val value: String,
) : Token {
    companion object {
        internal val Tags = listOf('$', '＄')
    }
}

data class HashTagToken(
    override val value: String,
) : Token {
    companion object {
        internal val Tags = listOf('#', '＃')
    }
}

data class StringToken(
    override val value: String,
) : Token

data class UrlToken(
    override val value: String,
) : Token {
    companion object {
        internal val Domains = DomainList
    }
}

data class UserNameToken(
    override val value: String,
) : Token {
    companion object {
        internal val Tags = listOf('@', '＠')
    }
}

data class EmojiToken(
    override val value: String,
) : Token {
    companion object {
        internal val Tags = listOf(':', '：')
    }
}