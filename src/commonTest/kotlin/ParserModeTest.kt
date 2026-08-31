import moe.tlaster.twitter.parser.CashTagToken
import moe.tlaster.twitter.parser.EmojiToken
import moe.tlaster.twitter.parser.HashTagToken
import moe.tlaster.twitter.parser.StringToken
import moe.tlaster.twitter.parser.TwitterParser
import moe.tlaster.twitter.parser.UserNameToken
import kotlin.test.Test
import kotlin.test.assertContentEquals

class ParserModeTest {
    @Test
    fun onlyTheCompleteTwitterTextConfigurationUsesTwitterTextBehavior() {
        val legacyConfigurations = listOf(
            TwitterParser(),
            TwitterParser(enableNonAsciiInUrl = false),
            TwitterParser(enableEscapeInUrl = true),
            TwitterParser(enableNonAsciiInUrl = false, enableEscapeInUrl = true),
            TwitterParser(enableDomainDetection = true),
            TwitterParser(enableDomainDetection = true, enableNonAsciiInUrl = false),
            TwitterParser(enableDomainDetection = true, enableEscapeInUrl = true),
        )

        legacyConfigurations.forEach { parser ->
            assertContentEquals(listOf(CashTagToken("\$CASHTAG")), parser.parse("\$CASHTAG"))
        }

        assertContentEquals(
            listOf(StringToken("\$CASHTAG")),
            twitterTextParser().parse("\$CASHTAG"),
        )
    }

    @Test
    fun parserExtensionsRemainAvailableInTwitterTextMode() {
        val parser = TwitterParser(
            enableEmoji = true,
            enableDomainDetection = true,
            enableNonAsciiInUrl = false,
            enableEscapeInUrl = true,
            enableCJKInCashTag = true,
            validMarkInUserName = listOf('@', '.'),
            validMarkInHashTag = listOf('-'),
        )

        assertContentEquals(
            listOf(
                EmojiToken(":smile:"),
                StringToken(" "),
                UserNameToken("@user@domain"),
                StringToken(" "),
                HashTagToken("#tag-name"),
                StringToken(" "),
                CashTagToken("\$標籤"),
            ),
            parser.parse(":smile: @user@domain #tag-name \$標籤"),
        )
        assertContentEquals(
            listOf(UserNameToken("@user"), StringToken("@")),
            parser.parse("@user@"),
        )
        assertContentEquals(listOf(HashTagToken("#---")), parser.parse("#---"))
        assertContentEquals(listOf(StringToken("hello,#tag-name")), parser.parse("hello,#tag-name"))
        assertContentEquals(listOf(StringToken("\u00A0\$標籤")), parser.parse("\u00A0\$標籤"))
    }

    private fun twitterTextParser() = TwitterParser(
        enableDomainDetection = true,
        enableNonAsciiInUrl = false,
        enableEscapeInUrl = true,
    )
}
