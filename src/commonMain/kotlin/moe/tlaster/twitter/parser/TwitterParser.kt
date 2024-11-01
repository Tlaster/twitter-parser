package moe.tlaster.twitter.parser

import moe.tlaster.twitter.parser.builder.TreeBuilder
import moe.tlaster.twitter.parser.tokenizer.StringReader
import moe.tlaster.twitter.parser.tokenizer.Tokenizer

class TwitterParser(
    private val enableEmoji: Boolean = false,
    private val enableDomainDetection: Boolean = false,
    private val enableNonAsciiInUrl: Boolean = true,
    private val enableEscapeInUrl: Boolean = false,
    private val validMarkInUserName: List<Char> = listOf(),
    private val validMarkInHashTag: List<Char> = listOf(),
) {

    fun parse(input: String): List<Token> {
        val tokenizer = Tokenizer(
            enableEmoji = enableEmoji,
            enableDomainDetection = enableDomainDetection,
            enableNonAsciiInUrl = enableNonAsciiInUrl,
            enableEscapeInUrl = enableEscapeInUrl,
            validMarkInUserName = validMarkInUserName,
            validMarkInHashTag = validMarkInHashTag,
        )
        val tokenCharacterTypes = tokenizer.parse(StringReader(input))
        return TreeBuilder().build(StringReader(input), tokenCharacterTypes)
    }
}

