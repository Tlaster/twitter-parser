package moe.tlaster.twitter.parser

class TwitterParser(
    private val enableEmoji: Boolean = false,
    private val enableDomainDetection: Boolean = false,
    private val enableNonAsciiInUrl: Boolean = true,
    private val enableEscapeInUrl: Boolean = false,
    private val enableCJKInCashTag: Boolean = false,
    private val validMarkInUserName: List<Char> = listOf(),
    private val validMarkInHashTag: List<Char> = listOf(),
) {
    private val engine = ParserEngine(
        enableEmoji = enableEmoji,
        enableDomainDetection = enableDomainDetection,
        enableNonAsciiInUrl = enableNonAsciiInUrl,
        enableEscapeInUrl = enableEscapeInUrl,
        enableCJKInCashTag = enableCJKInCashTag,
        validMarkInUserName = validMarkInUserName,
        validMarkInHashTag = validMarkInHashTag,
    )

    fun parse(input: String): List<Token> = engine.parse(input)
}
