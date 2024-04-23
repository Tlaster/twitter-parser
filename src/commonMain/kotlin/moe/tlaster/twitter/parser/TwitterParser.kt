package moe.tlaster.twitter.parser

import moe.tlaster.twitter.parser.builder.TreeBuilder
import moe.tlaster.twitter.parser.tokenizer.StringReader
import moe.tlaster.twitter.parser.tokenizer.TokenCharacterType
import moe.tlaster.twitter.parser.tokenizer.Tokenizer

class TwitterParser(
    private val enableAcct: Boolean = false,
    private val enableEmoji: Boolean = false,
    private val enableDotInUserName: Boolean = false,
    private val enableDomainDetection: Boolean = false,
    private val enableNonAsciiInUrl: Boolean = true,
    private val enableEscapeInUrl: Boolean = false,
    private val allowAllTextInUserName: Boolean = false,
) {

    fun parse(input: String): List<Token> {
        val tokenizer = Tokenizer(
            enableAcct = enableAcct,
            enableEmoji = enableEmoji,
            enableDotInUserName = enableDotInUserName,
            enableDomainDetection = enableDomainDetection,
            enableNonAsciiInUrl = enableNonAsciiInUrl,
            enableEscapeInUrl = enableEscapeInUrl,
            allowAllTextInUserName = allowAllTextInUserName,
        )
        val tokenCharacterTypes = tokenizer.parse(StringReader(input))
        return TreeBuilder().build(StringReader(input), tokenCharacterTypes)
    }
}

