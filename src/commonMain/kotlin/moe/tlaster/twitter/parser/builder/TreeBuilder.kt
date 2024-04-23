package moe.tlaster.twitter.parser.builder

import moe.tlaster.twitter.parser.CashTagToken
import moe.tlaster.twitter.parser.EmojiToken
import moe.tlaster.twitter.parser.HashTagToken
import moe.tlaster.twitter.parser.StringToken
import moe.tlaster.twitter.parser.Token
import moe.tlaster.twitter.parser.UrlToken
import moe.tlaster.twitter.parser.UserNameToken
import moe.tlaster.twitter.parser.tokenizer.Reader
import moe.tlaster.twitter.parser.tokenizer.TokenCharacterType


internal class TreeBuilder {
    fun build(reader: Reader, tokenCharacterTypes: List<TokenCharacterType>): List<Token> {
        val nodes = mutableListOf<Token>()
        var currentType: TokenCharacterType? = null
        var currentStart = 0
        for (i in 0 until reader.lengthWithoutEof) {
            val type = tokenCharacterTypes[i]
            if (currentType == null) {
                currentType = type
                currentStart = i
            } else if (currentType != type) {
                nodes.add(buildNode(currentType, reader, currentStart, i))
                currentType = type
                currentStart = i
            }
        }
        if (currentType != null) {
            nodes.add(buildNode(currentType, reader, currentStart, reader.lengthWithoutEof))
        }
        return nodes
    }

    private fun buildNode(currentType: TokenCharacterType, reader: Reader, currentStart: Int, i: Int): Token {
        val content = reader.readAt(currentStart, i - currentStart)
        return when (currentType) {
            TokenCharacterType.Character -> StringToken(content)
            TokenCharacterType.Url -> UrlToken(content)
            TokenCharacterType.Cash -> CashTagToken(content)
            TokenCharacterType.UserName -> UserNameToken(content)
            TokenCharacterType.UnKnown -> StringToken(content)
            TokenCharacterType.HashTag -> HashTagToken(content)
            TokenCharacterType.Eof -> StringToken("")
            TokenCharacterType.Emoji -> EmojiToken(content)
        }
    }
}
