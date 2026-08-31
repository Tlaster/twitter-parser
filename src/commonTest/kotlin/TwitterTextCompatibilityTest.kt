import moe.tlaster.twitter.parser.CashTagToken
import moe.tlaster.twitter.parser.DomainList
import moe.tlaster.twitter.parser.HashTagToken
import moe.tlaster.twitter.parser.StringToken
import moe.tlaster.twitter.parser.Token
import moe.tlaster.twitter.parser.TwitterTextExcludedDomainList
import moe.tlaster.twitter.parser.TwitterTextLegacyDomainList
import moe.tlaster.twitter.parser.TwitterParser
import moe.tlaster.twitter.parser.UrlToken
import moe.tlaster.twitter.parser.UserNameToken
import kotlin.test.Test
import kotlin.test.assertEquals

class TwitterTextCompatibilityTest {
    private val parser = TwitterParser(
        enableDomainDetection = true,
        enableNonAsciiInUrl = false,
        enableEscapeInUrl = true,
    )

    @Test
    fun matchesTwitterTextBoundaryBehavior() {
        val longUrl = "https://example.com/" + "a".repeat(4_100)
        val cases = listOf(
            case("@username", mention("@username")),
            case("(@user)", mention("@user")),
            case("＠user", mention("＠user")),
            case("a@user"),
            case("@user.name", mention("@user")),
            case("@user/list-name", mention("@user/list-name")),
            case("@abcdefghijklmnopqrst", mention("@abcdefghijklmnopqrst")),
            case("@abcdefghijklmnopqrstu", mention("@abcdefghijklmnopqrst")),
            case("!@user"),
            case("\$@user"),
            case("😀@user", mention("@user")),
            case("@用户名"),
            case(" #hashtag", hashtag("#hashtag")),
            case(" #中文标签", hashtag("#中文标签")),
            case(" ＃tag", hashtag("＃tag")),
            case("(#tag)", hashtag("#tag")),
            case("hello,#tag", hashtag("#tag")),
            case("#123"),
            case("#_123"),
            case("#e\u0301", hashtag("#e\u0301")),
            case("abc#tag"),
            case("中文#tag"),
            case("😀#tag", hashtag("#tag")),
            case("\u00A0#tag", hashtag("#tag")),
            case("\r#tag", hashtag("#tag")),
            case(" \$AAPL", cashtag("\$AAPL")),
            case("\$ABCDEF", cashtag("\$ABCDEF")),
            case("\$ABCDEFG"),
            case("\$BRK.A", cashtag("\$BRK.A")),
            case("\$BRK_B", cashtag("\$BRK_B")),
            case("\$AAPL2"),
            case("＄AAPL"),
            case("(\$AAPL)"),
            case("\u00A0\$AAPL", cashtag("\$AAPL")),
            case("\r\$AAPL", cashtag("\$AAPL")),
            case("https://example.com/path?q=1", url("https://example.com/path?q=1")),
            case("hTTps://EXAMPLE.COM/path", url("hTTps://EXAMPLE.COM/path")),
            case("HTTP://EXAMPLE.COM/path", url("HTTP://EXAMPLE.COM/path")),
            case("httpſ://example.com/path", url("httpſ://example.com/path")),
            case("https://example.com/a%20b", url("https://example.com/a%20b")),
            case("https://example.com/(test)", url("https://example.com/(test)")),
            case("https://example.com/foo).", url("https://example.com/foo")),
            case("https://example.com/路径", url("https://example.com/")),
            case("https://例子.公司/路径", url("https://例子.公司/")),
            case("http://localhost/path"),
            case("http://127.0.0.1/path"),
            case("ftp://example.com/path"),
            case("mailto:user@example.com"),
            case("user@example.com"),
            case("example.com", url("example.com")),
            case("example.com/path?q=1", url("example.com/path?q=1")),
            case("example.com:8080/path", url("example.com:8080/path")),
            case("(example.com)", url("example.com")),
            case("sub.example.co.uk", url("sub.example.co.uk")),
            case("www.example.com", url("www.example.com")),
            case("example.com.", url("example.com")),
            case("foo..com"),
            case("example.com2"),
            case("example.xn--p1ai", url("example.xn--p1ai")),
            case("example.xn--p1ai@", url("example.xn--p1ai")),
            case("example.zip", url("example.zip")),
            case("example.mov", url("example.mov")),
            case("example.ing", url("example.ing")),
            case("foo_bar.com"),
            case(longUrl),
        )

        cases.forEach { testCase ->
            val tokens = parser.parse(testCase.input)
            assertEquals(testCase.input, tokens.joinToString("") { it.value }, testCase.input)
            assertEquals(testCase.entities, tokens.filterNot { it is StringToken }, testCase.input)
        }
    }

    @Test
    fun matchesTwitterTextConformanceRules() {
        val cases = listOf(
            case("RT@username RT:@mention RT @test", mention("@username"), mention("@mention"), mention("@test")),
            case("@@username mention", mention("@username")),
            case("@aliceìnheiro something"),
            case("@http://twitter.com"),
            case("#http://twitter.com #https://twitter.com"),
            case("#one#two"),
            case("#云々 #学問のすゝめ #il·lusió", hashtag("#云々"), hashtag("#学問のすゝめ"), hashtag("#il·lusió")),
            case("#_ #1_2 #122 #〃"),
            case("Example: \$TEST.T test \$symbol_ab", cashtag("\$TEST.T"), cashtag("\$symbol_ab")),
            case("\$OK\$NG\$BAD text\$NO .\$NG \$\$NG", cashtag("\$OK")),
            case("\$co.https＄", cashtag("\$co")),
            case("http://foo.com?#foo", url("http://foo.com?#foo")),
            case("http://twitter.com/. ", url("http://twitter.com/")),
            case("http://sub_domain-dash.twitter.com", url("http://sub_domain-dash.twitter.com")),
            case("http://twitter_underscore.com"),
            case("http://xn--はじめよう.com/index.html"),
            case("\u0301文Z.co?cot＃@", url("Z.co?cot")),
            case("ìco.co", url("co.co")),
            case(";ìco.co", url("ìco.co")),
            case("/ìco.uk"),
            case(
                "example.amazon example.kids example.music example.spa example.active example.zippo",
                url("example.active"),
                url("example.zippo"),
            ),
            case(
                "#test.com @test.com #http://test.com @http://test.com",
                hashtag("#test"),
                mention("@test"),
            ),
            case("http://t.co/pbY2NfTZ's", url("http://t.co/pbY2NfTZ")),
            case("http://t.co/abcdefghijklmnopqrstuvwxyz012345678901234"),
            case(
                "http://t.co/abc123!https://example.com/a%20b",
                url("http://t.co/abc123"),
            ),
            case("@user/example.com/http://t.co/abc123", mention("@user/example")),
            case("@user/list-name-@x"),
            case("@user/list-name-@@user"),
            case("@user/list-nameéexample.com"),
            case("#tag_example.com/http://example.com/path", hashtag("#tag_example")),
            case("＃中文example.com?example.xn--p1ai", hashtag("＃中文example")),
            case("http://a.co/#abc\uFE0F#tag", url("http://a.co/#abc")),
            case("_example.xn--p1ai/http://example.com/path"),
            case("..plain..example.com?example.com"),
            case("_ſexample.xn--p1aiuser"),
            case("example.xn--p1aiéexample.com"),
            case("example.xn--p1aiſexample.com", url("example.xn--p1aiſexample.com")),
            case("example.xn--p1aißexample.com", url("example.xn--p1aißexample.com")),
            case("xn--p1ai_b.example.com", url("xn--p1ai_b.example.com")),
            case("http://example.xn--p1aiéexample.com"),
            case("http://a.co/(x)[](y)", url("http://a.co/(x)")),
            case("http://a.co/(x):(y)", url("http://a.co/(x)")),
            case("http://a.co/a\$(x)b", url("http://a.co/a\$(x)b")),
            case(
                "http://a.co/?q=_ſexample.com",
                url("http://a.co/?q=_"),
                url("example.com"),
            ),
            case(
                "https://例子.公司/路径éhttps://example.com/a%20b",
                url("https://例子.公司/"),
                url("https://example.com/a%20b"),
            ),
            case(
                "example.com-https://example.com/(x)-#tag",
                url("https://example.com/(x)-#tag"),
            ),
        )

        cases.forEach { testCase ->
            val tokens = parser.parse(testCase.input)
            assertEquals(testCase.input, tokens.joinToString("") { it.value }, testCase.input)
            assertEquals(testCase.entities, tokens.filterNot { it is StringToken }, testCase.input)
        }
    }

    @Test
    fun recognizesEveryConfiguredAsciiTld() {
        val excluded = TwitterTextExcludedDomainList.toSet()
        (DomainList + TwitterTextLegacyDomainList).forEach { tld ->
            val input = "example.${tld.lowercase()}"
            val urls = parser.parse(input).filterIsInstance<UrlToken>()
            val expected = if (tld in excluded) emptyList() else listOf(UrlToken(input))
            assertEquals(expected, urls, tld)
        }
    }

    private data class Case(val input: String, val entities: List<Token>)

    private fun case(input: String, vararg entities: Token) = Case(input, entities.toList())

    private fun mention(value: String) = UserNameToken(value)

    private fun hashtag(value: String) = HashTagToken(value)

    private fun cashtag(value: String) = CashTagToken(value)

    private fun url(value: String) = UrlToken(value)
}
