import moe.tlaster.twitter.parser.*
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ParserTest {
    private val parser = TwitterParser()

    @Test
    fun testSimpleTweet() {
        val text = "twitter"
        val link = "https://cards.twitter.com/cards/gsby/4ztbu"
        val userName = "@username"
        val hashtag = "#hashtag"
        val cashtag = "\$CASHTAG"
        val content = "$text $link $userName $hashtag $cashtag"
        val result = parser.parse(content)
        assertEquals(8, result.size)
        assertIs<StringToken>(result[0])
        assertEquals("$text ", result[0].value)
        assertIs<UrlToken>(result[1])
        assertEquals(link, result[1].value)
        assertIs<StringToken>(result[2])
        assertEquals(" ", result[2].value)
        assertIs<UserNameToken>(result[3])
        assertEquals(userName, result[3].value)
        assertIs<StringToken>(result[4])
        assertEquals(" ", result[4].value)
        assertIs<HashTagToken>(result[5])
        assertEquals(hashtag, result[5].value)
        assertIs<StringToken>(result[6])
        assertEquals(" ", result[6].value)
        assertIs<CashTagToken>(result[7])
        assertEquals(cashtag, result[7].value)
    }

    @Test
    fun testUserNameWithSymbol() {
        val username = "@username"
        val content = "$username: hello"
        val result = parser.parse(content)
        assertEquals(2, result.size)
        assertIs<UserNameToken>(result[0])
        assertEquals(username, result[0].value)
        assertIs<StringToken>(result[1])
        assertEquals(": hello", result[1].value)
    }

    @Test
    fun testHashTagWithSymbol() {
        val hashtag = "#hashtag"
        val content = "$hashtag: hello"
        val result = parser.parse(content)
        assertEquals(2, result.size)
        assertIs<HashTagToken>(result[0])
        assertEquals(hashtag, result[0].value)
        assertIs<StringToken>(result[1])
        assertEquals(": hello", result[1].value)
    }

    @Test
    fun testHashTagWithUnderline() {
        val hashtag = "#hashtag_hashtag"
        val content = "$hashtag: hello"
        val result = parser.parse(content)
        assertEquals(2, result.size)
        assertIs<HashTagToken>(result[0])
        assertEquals(hashtag, result[0].value)
        assertIs<StringToken>(result[1])
        assertEquals(": hello", result[1].value)
    }

    @Test
    fun testCashTagWithSymbol() {
        val cashtag = "\$CASHTAG"
        val content = "$cashtag: hello"
        val result = parser.parse(content)
        assertEquals(2, result.size)
        assertIs<CashTagToken>(result[0])
        assertEquals(cashtag, result[0].value)
        assertIs<StringToken>(result[1])
        assertEquals(": hello", result[1].value)
    }

    @Test
    fun testUserNameWithHashTagSymbol() {
        val username = "@username"
        val hashtag = "#hashtag"
        val content = "$username$hashtag: hello"
        val result = parser.parse(content)
        assertEquals(2, result.size)
        assertIs<UserNameToken>(result[0])
        assertEquals(username, result[0].value)
        assertIs<StringToken>(result[1])
        assertEquals("$hashtag: hello", result[1].value)
    }

    @Test
    fun testUserNameWithCashTagSymbol() {
        val username = "@username"
        val cashtag = "\$CASHTAG"
        val content = "$username$cashtag: hello"
        val result = parser.parse(content)
        assertEquals(2, result.size)
        assertIs<UserNameToken>(result[0])
        assertEquals(username, result[0].value)
        assertIs<StringToken>(result[1])
        assertEquals("$cashtag: hello", result[1].value)
    }

    @Test
    fun testHashTagWithCashTagSymbol() {
        val hashtag = "#hashtag"
        val cashtag = "\$CASHTAG"
        val content = "$hashtag$cashtag: hello"
        val result = parser.parse(content)
        assertEquals(2, result.size)
        assertIs<HashTagToken>(result[0])
        assertEquals(hashtag, result[0].value)
        assertIs<StringToken>(result[1])
        assertEquals("$cashtag: hello", result[1].value)
    }

    @Test
    fun testUserNameWithHashTagCashTagSymbol() {
        val username = "@username"
        val hashtag = "#hashtag"
        val cashtag = "\$CASHTAG"
        val content = "$username$hashtag$cashtag: hello"
        val result = parser.parse(content)
        assertEquals(2, result.size)
        assertIs<UserNameToken>(result[0])
        assertEquals(username, result[0].value)
        assertIs<StringToken>(result[1])
        assertEquals("$hashtag$cashtag: hello", result[1].value)
    }

    @Test
    fun testHashTagWithUserNameCashTagSymbol() {
        val username = "@username"
        val hashtag = "#hashtag"
        val cashtag = "\$CASHTAG"
        val content = "$hashtag$username$cashtag: hello"
        val result = parser.parse(content)
        assertEquals(2, result.size)
        assertIs<HashTagToken>(result[0])
        assertEquals(hashtag, result[0].value)
        assertIs<StringToken>(result[1])
        assertEquals("$username$cashtag: hello", result[1].value)
    }

    @Test
    fun testCashTagWithUserNameHashTagSymbol() {
        val username = "@username"
        val hashtag = "#hashtag"
        val cashtag = "\$CASHTAG"
        val content = "$cashtag$username$hashtag: hello"
        val result = parser.parse(content)
        assertEquals(2, result.size)
        assertIs<CashTagToken>(result[0])
        assertEquals(cashtag, result[0].value)
        assertIs<StringToken>(result[1])
        assertEquals("$username$hashtag: hello", result[1].value)
    }

    @Test
    fun testContentWithUserName() {
        val text = "hello"
        val username = "@username"
        val content = "$text$username"
        val result = parser.parse(content)
        assertEquals(1, result.size)
        assertIs<StringToken>(result[0])
        assertEquals(content, result[0].value)
    }

    @Test
    fun testContentWithHashTag() {
        val text = "hello"
        val hashtag = "#hashtag"
        val content = "$text$hashtag"
        val result = parser.parse(content)
        assertEquals(1, result.size)
        assertIs<StringToken>(result[0])
        assertEquals(content, result[0].value)
    }

    @Test
    fun testContentWithCashTag() {
        val text = "hello"
        val cashtag = "\$CASHTAG"
        val content = "$text$cashtag"
        val result = parser.parse(content)
        assertEquals(1, result.size)
        assertIs<StringToken>(result[0])
        assertEquals(content, result[0].value)
    }

    @Test
    fun testCJKUserName() {
        val username = "@用户名"
        val content = "$username: hello"
        val result = parser.parse(content)
        assertEquals(1, result.size)
        assertIs<StringToken>(result[0])
        assertEquals(content, result[0].value)
    }

    @Test
    fun testCJKHashTag() {
        val hashtag = "#標籤"
        val content = "$hashtag: hello"
        val result = parser.parse(content)
        assertEquals(2, result.size)
        assertIs<HashTagToken>(result[0])
        assertEquals(hashtag, result[0].value)
        assertIs<StringToken>(result[1])
        assertEquals(": hello", result[1].value)
    }

    @Test
    fun testCJKCashTag() {
        val cashtag = "\$標籤"
        val content = "$cashtag: hello"
        val result = parser.parse(content)
        assertEquals(1, result.size)
        assertIs<StringToken>(result[0])
        assertEquals(content, result[0].value)
    }

    @Test
    fun testUrlHashTag() {
        val url = "https://example.com/"
        val hashtag = "#hashtag"
        val content = "$url$hashtag"
        val result = parser.parse(content)
        assertEquals(1, result.size)
        assertIs<UrlToken>(result[0])
        assertEquals(content, result[0].value)
    }

    @Test
    fun testLineBreakAfterUrl() {
        val url = "https://example.com/"
        val content = "$url\n"
        val result = parser.parse(content)
        assertEquals(2, result.size)
        assertIs<UrlToken>(result[0])
        assertEquals(url, result[0].value)
        assertIs<StringToken>(result[1])
        assertEquals("\n", result[1].value)
    }

    @Test
    fun testLineBreakBeforeUrl() {
        val url = "https://example.com/"
        val content = "\n$url"
        val result = parser.parse(content)
        assertEquals(2, result.size)
        assertIs<StringToken>(result[0])
        assertEquals("\n", result[0].value)
        assertIs<UrlToken>(result[1])
        assertEquals(url, result[1].value)
    }

    @Test
    fun testLineBreakAfterUserName() {
        val username = "@username"
        val content = "$username\n"
        val result = parser.parse(content)
        assertEquals(2, result.size)
        assertIs<UserNameToken>(result[0])
        assertEquals(username, result[0].value)
        assertIs<StringToken>(result[1])
        assertEquals("\n", result[1].value)
    }

    @Test
    fun testLineBreakBeforeUserName() {
        val username = "@username"
        val content = "\n$username"
        val result = parser.parse(content)
        assertEquals(2, result.size)
        assertIs<StringToken>(result[0])
        assertEquals("\n", result[0].value)
        assertIs<UserNameToken>(result[1])
        assertEquals(username, result[1].value)
    }

    @Test
    fun testLineBreakAfterHashTag() {
        val hashtag = "#hashtag"
        val content = "$hashtag\n"
        val result = parser.parse(content)
        assertEquals(2, result.size)
        assertIs<HashTagToken>(result[0])
        assertEquals(hashtag, result[0].value)
        assertIs<StringToken>(result[1])
        assertEquals("\n", result[1].value)
    }

    @Test
    fun testLineBreakBeforeHashTag() {
        val hashtag = "#hashtag"
        val content = "\n$hashtag"
        val result = parser.parse(content)
        assertEquals(2, result.size)
        assertIs<StringToken>(result[0])
        assertEquals("\n", result[0].value)
        assertIs<HashTagToken>(result[1])
        assertEquals(hashtag, result[1].value)
    }

    @Test
    fun testLineBreakAfterCashTag() {
        val cashtag = "\$CASHTAG"
        val content = "$cashtag\n"
        val result = parser.parse(content)
        assertEquals(2, result.size)
        assertIs<CashTagToken>(result[0])
        assertEquals(cashtag, result[0].value)
        assertIs<StringToken>(result[1])
        assertEquals("\n", result[1].value)
    }

    @Test
    fun testLineBreakBeforeCashTag() {
        val cashtag = "\$CASHTAG"
        val content = "\n$cashtag"
        val result = parser.parse(content)
        assertEquals(2, result.size)
        assertIs<StringToken>(result[0])
        assertEquals("\n", result[0].value)
        assertIs<CashTagToken>(result[1])
        assertEquals(cashtag, result[1].value)
    }

    @Test
    fun testAltUserName() {
        val username = "＠username"
        val content = "$username: hello"
        val result = parser.parse(content)
        assertEquals(2, result.size)
        assertIs<UserNameToken>(result[0])
        assertEquals(username, result[0].value)
        assertIs<StringToken>(result[1])
        assertEquals(": hello", result[1].value)
    }

    @Test
    fun testAltHashTag() {
        val hashtag = "＃hashtag"
        val content = "$hashtag: hello"
        val result = parser.parse(content)
        assertEquals(2, result.size)
        assertIs<HashTagToken>(result[0])
        assertEquals(hashtag, result[0].value)
        assertIs<StringToken>(result[1])
        assertEquals(": hello", result[1].value)
    }

    @Test
    fun testAltCashTag() {
        val cashtag = "＄CASHTAG"
        val content = "$cashtag: hello"
        val result = parser.parse(content)
        assertEquals(2, result.size)
        assertIs<CashTagToken>(result[0])
        assertEquals(cashtag, result[0].value)
        assertIs<StringToken>(result[1])
        assertEquals(": hello", result[1].value)
    }

    @Test
    fun testAltSpaceWithUserName() {
        val username = "@username"
        val content = "$username　hello"
        val result = parser.parse(content)
        assertEquals(2, result.size)
        assertIs<UserNameToken>(result[0])
        assertEquals(username, result[0].value)
        assertIs<StringToken>(result[1])
        assertEquals("　hello", result[1].value)
    }

    @Test
    fun testAltSpaceWithHashTag() {
        val hashtag = "#hashtag"
        val content = "$hashtag:　hello"
        val result = parser.parse(content)
        assertEquals(2, result.size)
        assertIs<HashTagToken>(result[0])
        assertEquals(hashtag, result[0].value)
        assertIs<StringToken>(result[1])
        assertEquals(":　hello", result[1].value)
    }

    @Test
    fun testAltSpaceWithCashTag() {
        val cashtag = "\$CASHTAG"
        val content = "$cashtag:　hello"
        val result = parser.parse(content)
        assertEquals(2, result.size)
        assertIs<CashTagToken>(result[0])
        assertEquals(cashtag, result[0].value)
        assertIs<StringToken>(result[1])
        assertEquals(":　hello", result[1].value)
    }

    @Test
    fun testUserNameWithQuote() {
        val content = "animation practice with @enarane's Goat-chan \uD83E\uDEE1"
        val result = parser.parse(content)
        assertEquals(3, result.size)
        assertIs<StringToken>(result[0])
        assertEquals("animation practice with ", result[0].value)
        assertIs<UserNameToken>(result[1])
        assertEquals("@enarane", result[1].value)
        assertIs<StringToken>(result[2])
        assertEquals("'s Goat-chan \uD83E\uDEE1", result[2].value)
    }

    @Test
    fun testAcctUserName() {
        val userName = "@user@domain"
        val content = "$userName: hello"
        val result = parser.parse(content)
        assertEquals(2, result.size)
        assertIs<UserNameToken>(result[0])
        assertEquals("@user", result[0].value)
        assertIs<StringToken>(result[1])
        assertEquals("@domain: hello", result[1].value)
    }

    @Test
    fun testAcctUserNameWithFullDomain() {
        val userName = "@user@domain.host"
        val content = "$userName: hello"
        val result = parser.parse(content)
        assertEquals(2, result.size)
        assertIs<UserNameToken>(result[0])
        assertEquals("@user", result[0].value)
        assertIs<StringToken>(result[1])
        assertEquals("@domain.host: hello", result[1].value)
    }

    @Test
    fun testEmoji() {
        val emoji = ":smile:"
        val content = "hello $emoji"
        val result = parser.parse(content)
        assertEquals(1, result.size)
        assertIs<StringToken>(result[0])
        assertEquals("hello $emoji", result[0].value)
    }

    @Test
    fun testDotInUserName() {
        val userName = "@user.name"
        val content = "$userName: hello"
        val result = parser.parse(content)
        assertEquals(2, result.size)
        assertIs<UserNameToken>(result[0])
        assertEquals("@user", result[0].value)
        assertIs<StringToken>(result[1])
        assertEquals(".name: hello", result[1].value)
    }

    @Test
    fun testUrlEndWithEscapes() {
        val url = "http://www.cool.com.au/ersdfs?dfd=dfgd@s=1"
        val content = "$url: hello"
        val result = parser.parse(content)
        assertEquals(2, result.size)
        assertIs<UrlToken>(result[0])
        assertEquals(url, result[0].value)
        assertIs<StringToken>(result[1])
        assertEquals(": hello", result[1].value)
    }

    @Test
    fun testURlWithNonAscII() {
        val url = "http://de.wikipedia.org/wiki/Fürth"
        val content = "$url hello"
        val result = parser.parse(content)
        assertEquals(2, result.size)
        assertIs<UrlToken>(result[0])
        assertEquals(url, result[0].value)
        assertIs<StringToken>(result[1])
        assertEquals(" hello", result[1].value)
    }

    @Test
    fun testDomain() {
        val domain = "cool.com"
        val content = "$domain: hello"
        val result = parser.parse(content)
        assertEquals(1, result.size)
        assertIs<StringToken>(result[0])
        assertEquals("$domain: hello", result[0].value)
    }

    @Test
    fun testUrlWithLineBreak() {
        val text =
            "Certified Shit Poster \uD83C\uDF3F | ADHD is My Superpower \uD83D\uDD0B\nToken Gated Telegram: https://guild.xyz/ghostgang100x\n#GhostGang100x @ghostgang100x \uD83C\uDFAB                              "
        val result = parser.parse(text)
        assertEquals(7, result.size)
        assertIs<StringToken>(result[0])
        assertEquals(
            "Certified Shit Poster \uD83C\uDF3F | ADHD is My Superpower \uD83D\uDD0B\nToken Gated Telegram: ",
            result[0].value
        )
        assertIs<UrlToken>(result[1])
        assertEquals("https://guild.xyz/ghostgang100x", result[1].value)
        assertIs<StringToken>(result[2])
        assertEquals("\n", result[2].value)
        assertIs<HashTagToken>(result[3])
        assertEquals("#GhostGang100x", result[3].value)
        assertIs<StringToken>(result[4])
        assertEquals(" ", result[4].value)
        assertIs<UserNameToken>(result[5])
        assertEquals("@ghostgang100x", result[5].value)
        assertIs<StringToken>(result[6])
        assertEquals(" \uD83C\uDFAB                              ", result[6].value)
    }

    @Test
    fun testLastH() {
        val text = "test h"
        val result = parser.parse(text)
        assertEquals(1, result.size)
        assertIs<StringToken>(result[0])
        assertEquals("test h", result[0].value)
    }

    @Test
    fun testFakeUrl() {
        val text = "test http://test"
        val result = parser.parse(text)
        assertEquals(1, result.size)
        assertIs<StringToken>(result[0])
        assertEquals("test http://test", result[0].value)
    }

    @Test
    fun testUrlWithEscapes() {
        val text = "test http://test.com?test=[1&test2=2 haha"
        val result = parser.parse(text)
        assertEquals(3, result.size)
        assertIs<StringToken>(result[0])
        assertEquals("test ", result[0].value)
        assertIs<UrlToken>(result[1])
        assertEquals("http://test.com?test=", result[1].value)
        assertIs<StringToken>(result[2])
        assertEquals("[1&test2=2 haha", result[2].value)
    }

    @Test
    fun testFakeCashTag() {
        val text = "test $"
        val result = parser.parse(text)
        assertEquals(1, result.size)
        assertIs<StringToken>(result[0])
        assertEquals("test $", result[0].value)
    }

    @Test
    fun testFakeTags() {
        val text = "test # $ @ haha"
        val result = parser.parse(text)
        assertEquals(1, result.size)
        assertIs<StringToken>(result[0])
        assertEquals("test # $ @ haha", result[0].value)
    }

    @Test
    fun testFakeTags2() {
        val text = "test #: $: @: haha"
        val result = parser.parse(text)
        assertEquals(1, result.size)
        assertIs<StringToken>(result[0])
        assertEquals("test #: $: @: haha", result[0].value)
    }

    @Test
    fun testFakeTags3() {
        val text = "test #/ $/ @/ haha"
        val result = parser.parse(text)
        assertEquals(1, result.size)
        assertIs<StringToken>(result[0])
        assertEquals("test #/ $/ @/ haha", result[0].value)
    }

    @Test
    fun testHashTagWithSpecialChar() {
        val text = "test #haha!"
        val result = parser.parse(text)
        assertEquals(3, result.size)
        assertIs<StringToken>(result[0])
        assertEquals("test ", result[0].value)
        assertIs<HashTagToken>(result[1])
        assertEquals("#haha", result[1].value)
        assertIs<StringToken>(result[2])
        assertEquals("!", result[2].value)
    }

    @Test
    fun testHashTagWithUnderLine() {
        val text = "test #haha_"
        val result = parser.parse(text)
        assertEquals(2, result.size)
        assertIs<StringToken>(result[0])
        assertEquals("test ", result[0].value)
        assertIs<HashTagToken>(result[1])
        assertEquals("#haha_", result[1].value)
    }


    @Test
    fun testUrlPort() {
        val url = "http://127.0.0.1:8000"
        val content = "test${url}end"
        val result = parser.parse(content)
        assertEquals(3, result.size)
        assertIs<StringToken>(result[0])
        assertEquals("test", result[0].value)
        assertIs<UrlToken>(result[1])
        assertEquals(url, result[1].value)
        assertIs<StringToken>(result[2])
        assertEquals("end", result[2].value)
    }


    @Test
    fun testFakeUrlPort() {
        val url = "http://127.0.0.1"
        val content = "test${url}:testend"
        val result = parser.parse(content)
        assertEquals(3, result.size)
        assertIs<StringToken>(result[0])
        assertEquals("test", result[0].value)
        assertIs<UrlToken>(result[1])
        assertEquals(url, result[1].value)
        assertIs<StringToken>(result[2])
        assertEquals(":testend", result[2].value)
    }

    @Test
    fun testFakeUrlPort2() {
        val url = "http://127.0.0.1"
        val content = "test${url}:hestend"
        val result = parser.parse(content)
        assertEquals(3, result.size)
        assertIs<StringToken>(result[0])
        assertEquals("test", result[0].value)
        assertIs<UrlToken>(result[1])
        assertEquals(url, result[1].value)
        assertIs<StringToken>(result[2])
        assertEquals(":hestend", result[2].value)
    }

    @Test
    fun testFakeUrlPort3() {
        val url = "http://127.0.0.1"
        val content = "test${url}:sestend"
        val result = parser.parse(content)
        assertEquals(3, result.size)
        assertIs<StringToken>(result[0])
        assertEquals("test", result[0].value)
        assertIs<UrlToken>(result[1])
        assertEquals(url, result[1].value)
        assertIs<StringToken>(result[2])
        assertEquals(":sestend", result[2].value)
    }

    @Test
    fun testFakeUrlPort4() {
        val url = "http://127.0.0.1"
        val content = "test${url}:pestend"
        val result = parser.parse(content)
        assertEquals(3, result.size)
        assertIs<StringToken>(result[0])
        assertEquals("test", result[0].value)
        assertIs<UrlToken>(result[1])
        assertEquals(url, result[1].value)
        assertIs<StringToken>(result[2])
        assertEquals(":pestend", result[2].value)
    }


    @Test
    fun testFakeUrlPortWithSpace() {
        val url = "http://127.0.0.1"
        val content = "test${url}: estend"
        val result = parser.parse(content)
        assertEquals(3, result.size)
        assertIs<StringToken>(result[0])
        assertEquals("test", result[0].value)
        assertIs<UrlToken>(result[1])
        assertEquals(url, result[1].value)
        assertIs<StringToken>(result[2])
        assertEquals(": estend", result[2].value)
    }

    @Test
    fun testFakeUrlPortWithSlash() {
        val url = "http://127.0.0.1"
        val content = "test${url}:/estend"
        val result = parser.parse(content)
        assertEquals(3, result.size)
        assertIs<StringToken>(result[0])
        assertEquals("test", result[0].value)
        assertIs<UrlToken>(result[1])
        assertEquals(url, result[1].value)
        assertIs<StringToken>(result[2])
        assertEquals(":/estend", result[2].value)
    }

    @Test
    fun testUrlPortWithQuery() {
        val url = "http://test.com:8000?test=1&test2=2"
        val content = "test ${url} end"
        val result = parser.parse(content)
        assertEquals(3, result.size)
        assertIs<StringToken>(result[0])
        assertEquals("test ", result[0].value)
        assertIs<UrlToken>(result[1])
        assertEquals(url, result[1].value)
        assertIs<StringToken>(result[2])
        assertEquals(" end", result[2].value)
    }

    @Test
    fun testUrlWithLastDot() {
        val url = "https://t.co/NECo3WQMJQ"
        val content = "test ${url}."
        val result = parser.parse(content)
        assertEquals(3, result.size)
        assertIs<StringToken>(result[0])
        assertEquals("test ", result[0].value)
        assertIs<UrlToken>(result[1])
        assertEquals(url, result[1].value)
        assertIs<StringToken>(result[2])
        assertEquals(".", result[2].value)
    }

    @Test
    fun testNotCash() {
        val url = "$123"
        val content = "test ${url}."
        val result = parser.parse(content)
        assertEquals(1, result.size)
        assertIs<StringToken>(result[0])
        assertEquals(content, result[0].value)
    }

    @Test
    fun testNonAlphBeforeName() {
        val content = "test。@test"
        val result = parser.parse(content)
        assertEquals(2, result.size)
        assertIs<StringToken>(result[0])
        assertEquals("test。", result[0].value)
        assertIs<UserNameToken>(result[1])
        assertEquals("@test", result[1].value)
    }

    @Test
    fun testNonAlphInHashTag() {
        val parser = TwitterParser(validMarkInHashTag = listOf(':'))
        val content = "test #test:test"
        val result = parser.parse(content)
        assertEquals(2, result.size)
        assertIs<StringToken>(result[0])
        assertEquals("test ", result[0].value)
        assertIs<HashTagToken>(result[1])
        assertEquals("#test:test", result[1].value)
    }

    @Test
    fun testNonAlphBeforeUserName() {
        val content = "测试@test"
        val result = parser.parse(content)
        assertEquals(2, result.size)
        assertIs<StringToken>(result[0])
        assertEquals("测试", result[0].value)
        assertIs<UserNameToken>(result[1])
        assertEquals("@test", result[1].value)
    }

    @Test
    fun testToeknWithNumber() {
        val content = "test$100"
        val result = parser.parse(content)
        assertEquals(1, result.size)
        assertIs<StringToken>(result[0])
        assertEquals(content, result[0].value)
    }

    @Test
    fun testUrl2() {
        val content = "成人向けな絵とか漫画とか描いています ファンティア【http://fantia.jp/fanclubs/6173】 FANBOX【http://cmyu6slv.fanbox.cc】skeb【https://skeb.jp/@Aoiro_Banana】無断転載AI学習禁止"
        val parser =
            TwitterParser(
                enableNonAsciiInUrl = false,
            )
        val result = parser.parse(content)
        assertEquals(7, result.size)
        assertContentEquals(
            listOf(
                StringToken("成人向けな絵とか漫画とか描いています ファンティア【"),
                UrlToken("http://fantia.jp/fanclubs/6173"),
                StringToken("】 FANBOX【"),
                UrlToken("http://cmyu6slv.fanbox.cc"),
                StringToken("】skeb【"),
                UrlToken("https://skeb.jp/@Aoiro_Banana"),
                StringToken("】無断転載AI学習禁止"),
            ),
            result
        )
    }

    @Test
    fun testUserName2() {
        val content = "み(@akemitan_ )大先生のshizu本&メイキングset着弾！！"
        val parser = TwitterParser(
            enableNonAsciiInUrl = false,
        )
        val result = parser.parse(content)
        assertEquals(3, result.size)
        assertContentEquals(
            listOf(
                StringToken("み("),
                UserNameToken("@akemitan_"),
                StringToken(" )大先生のshizu本&メイキングset着弾！！")
            ),
            result
        )
    }

    @Test
    fun testHashtag() {
        val content = """『#バック・トゥ・ザ・フューチャー』
公開40周年記念上映！
⚡️12/12（金）より1週間限定公開⚡

世代を超えて愛され続ける
伝説に残る壮大なタイムトラベルSFをIMAXで🚗🔥
圧倒的な没入体験を。

#IMAXで体感せよ #BTTF40周年
#バック・トゥ・ザ・フューチャーの日""".trimIndent()
        val parser = TwitterParser(
            enableNonAsciiInUrl = false,
        )
        val result = parser.parse(content)
        assertContentEquals(
            listOf(
                StringToken("『"),
                HashTagToken("#バック・トゥ・ザ・フューチャー"),
                StringToken("』\n公開40周年記念上映！\n⚡️12/12（金）より1週間限定公開⚡\n\n世代を超えて愛され続ける\n伝説に残る壮大なタイムトラベルSFをIMAXで🚗🔥\n圧倒的な没入体験を。\n\n"),
                HashTagToken("#IMAXで体感せよ"),
                StringToken(" "),
                HashTagToken("#BTTF40周年"),
                StringToken("\n"),
                HashTagToken("#バック・トゥ・ザ・フューチャーの日"),
            ),
            result
        )
    }


    @Test
    fun testUserName3() {
        val content = "み『@akemitan_』大先生のshizu本&メイキングset着弾！！"
        val parser = TwitterParser(
            enableNonAsciiInUrl = false,
        )
        val result = parser.parse(content)
        assertEquals(3, result.size)
        assertContentEquals(
            listOf(
                StringToken("み『"),
                UserNameToken("@akemitan_"),
                StringToken("』大先生のshizu本&メイキングset着弾！！")
            ),
            result
        )
    }

    @Test
    fun testCashTag2() {
        val content = "投資初心者は \$XYZをチェック！"
        val parser = TwitterParser(
            enableCJKInCashTag = true,
        )
        val result = parser.parse(content)
        assertContentEquals(
            listOf(
                StringToken("投資初心者は \$XYZをチェック！"),
            ),
            result
        )
    }

    @Test
    fun testCashTag3() {
        val content = "\$abcdefghijklmnopqrstuvwxyzABCD"
        val parser = TwitterParser()
        val result = parser.parse(content)
        assertContentEquals(
            listOf(
                CashTagToken("\$abcdefghijklmnopqrst"),
                StringToken("uvwxyzABCD"),
            ),
            result
        )
    }

    @Test
    fun testCashTag4() {
        val content = "\$あいうえおabcdefghijklmnopqrstuvwxyzABCD"
        val parser = TwitterParser(
            enableCJKInCashTag = true,
        )
        val result = parser.parse(content)
        assertContentEquals(
            listOf(
                CashTagToken("\$あいうえおabcde"),
                StringToken("fghijklmnopqrstuvwxyzABCD"),
            ),
            result
        )
    }

    @Test
    fun testCashTag5() {
        val content = "\$あいうえお、かきくけこ"
        val parser = TwitterParser(
            enableCJKInCashTag = true,
        )
        val result = parser.parse(content)
        assertContentEquals(
            listOf(
                CashTagToken("\$あいうえお"),
                StringToken("、かきくけこ"),
            ),
            result
        )
    }

    @Test
    fun testCashTag6() {
        val content = "\$abc\$def"
        val parser = TwitterParser()
        val result = parser.parse(content)
        assertContentEquals(
            listOf(
                CashTagToken("\$abc"),
                StringToken("\$def"),
            ),
            result
        )
    }

    @Test
    fun testCashTag7() {
        val content = "\$12345"
        val parser = TwitterParser(
            enableCJKInCashTag = true,
        )
        val result = parser.parse(content)
        assertContentEquals(
            listOf(
                CashTagToken("\$12345"),
            ),
            result
        )
    }

    @Test
    fun testCashTag8() {
        val content = "\$12345k"
        val parser = TwitterParser(
            enableCJKInCashTag = true,
        )
        val result = parser.parse(content)
        assertContentEquals(
            listOf(
                StringToken("\$12345k"),
            ),
            result
        )
    }

    @Test
    fun testCashTag9() {
        val content = "\$12345etc"
        val parser = TwitterParser(
            enableCJKInCashTag = true,
        )
        val result = parser.parse(content)
        assertContentEquals(
            listOf(
                CashTagToken("\$12345etc"),
            ),
            result
        )
    }

    @Test
    fun testCashTag10() {
        val content = "\$1234/5etc"
        val parser = TwitterParser(
            enableCJKInCashTag = true,
        )
        val result = parser.parse(content)
        assertContentEquals(
            listOf(
                CashTagToken("\$1234/5etc"),
            ),
            result
        )
    }
}