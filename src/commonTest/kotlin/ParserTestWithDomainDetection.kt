import moe.tlaster.twitter.parser.*
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ParserTestWithDomainDetection {
    private val parser = TwitterParser(enableDomainDetection = true)

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
        assertEquals(2, result.size)
        assertIs<UrlToken>(result[0])
        assertEquals(domain, result[0].value)
        assertIs<StringToken>(result[1])
        assertEquals(": hello", result[1].value)
    }

    @Test
    fun testDomainMiddle() {
        val domain = "cool.com"
        val content = "asd $domain: hello"
        val result = parser.parse(content)
        assertEquals(3, result.size)
        assertIs<StringToken>(result[0])
        assertEquals("asd ", result[0].value)
        assertIs<UrlToken>(result[1])
        assertEquals(domain, result[1].value)
        assertIs<StringToken>(result[2])
        assertEquals(": hello", result[2].value)
    }

    @Test
    fun testErrorDomain() {
        val domain = "cool.asd"
        val content = "$domain: hello"
        val result = parser.parse(content)
        assertEquals(1, result.size)
        assertIs<StringToken>(result[0])
        assertEquals("$domain: hello", result[0].value)
    }

    @Test
    fun testErrorDomainMiddle() {
        val domain = "cool.asd"
        val content = "asd $domain: hello"
        val result = parser.parse(content)
        assertEquals(1, result.size)
        assertIs<StringToken>(result[0])
        assertEquals("asd $domain: hello", result[0].value)
    }

    @Test
    fun testDomainWithAt() {
        val domain = "cool.com"
        val content = "asd $domain@: hello"
        val result = parser.parse(content)
        assertEquals(3, result.size)
        assertIs<StringToken>(result[0])
        assertEquals("asd ", result[0].value)
        assertIs<UrlToken>(result[1])
        assertEquals(domain, result[1].value)
        assertIs<StringToken>(result[2])
        assertEquals("@: hello", result[2].value)
    }

    @Test
    fun testDomainWithCashTag() {
        val domain = "cool.com"
        val content = "asd $domain\$hello"
        val result = parser.parse(content)
        assertEquals(3, result.size)
        assertIs<StringToken>(result[0])
        assertEquals("asd ", result[0].value)
        assertIs<UrlToken>(result[1])
        assertEquals(domain, result[1].value)
        assertIs<StringToken>(result[2])
        assertEquals("\$hello", result[2].value)
    }

    @Test
    fun testDomainWithHashTag() {
        val domain = "cool.com"
        val content = "asd $domain#hello"
        val result = parser.parse(content)
        assertEquals(3, result.size)
        assertIs<StringToken>(result[0])
        assertEquals("asd ", result[0].value)
        assertIs<UrlToken>(result[1])
        assertEquals(domain, result[1].value)
        assertIs<StringToken>(result[2])
        assertEquals("#hello", result[2].value)
    }
}