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
    private val twitterTextEngine = if (
        enableDomainDetection && !enableNonAsciiInUrl && enableEscapeInUrl
    ) {
        ParserEngine(
            enableEmoji = enableEmoji,
            extractUrlWithoutProtocol = true,
            enableNonAsciiInUrl = false,
            enableEscapeInUrl = true,
            enableCJKInCashTag = enableCJKInCashTag,
            validMarkInUserName = validMarkInUserName,
            validMarkInHashTag = validMarkInHashTag,
        )
    } else {
        null
    }

    private val legacyEngine = if (twitterTextEngine == null) {
        LegacyParserEngine(
            enableEmoji = enableEmoji,
            enableDomainDetection = enableDomainDetection,
            enableNonAsciiInUrl = enableNonAsciiInUrl,
            enableEscapeInUrl = enableEscapeInUrl,
            enableCJKInCashTag = enableCJKInCashTag,
            validMarkInUserName = validMarkInUserName,
            validMarkInHashTag = validMarkInHashTag,
        )
    } else {
        null
    }

    fun parse(input: String): List<Token> = twitterTextEngine?.parse(input) ?: legacyEngine!!.parse(input)
}
