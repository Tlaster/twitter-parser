import moe.tlaster.twitter.parser.*
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ParserTest {
    @Test
    fun testSimpleTweet() {
        val parser = TwitterParser()
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
        val parser = TwitterParser()
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
        val parser = TwitterParser()
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
    fun testCashTagWithSymbol() {
        val parser = TwitterParser()
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
        val parser = TwitterParser()
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
        val parser = TwitterParser()
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
        val parser = TwitterParser()
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
        val parser = TwitterParser()
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
        val parser = TwitterParser()
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
        val parser = TwitterParser()
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
        val parser = TwitterParser()
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
        val parser = TwitterParser()
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
        val parser = TwitterParser()
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
        val parser = TwitterParser()
        val username = "@用户名"
        val content = "$username: hello"
        val result = parser.parse(content)
        assertEquals(1, result.size)
        assertIs<StringToken>(result[0])
        assertEquals(content, result[0].value)
    }

    @Test
    fun testCJKHashTag() {
        val parser = TwitterParser()
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
        val parser = TwitterParser()
        val cashtag = "\$標籤"
        val content = "$cashtag: hello"
        val result = parser.parse(content)
        assertEquals(1, result.size)
        assertIs<StringToken>(result[0])
        assertEquals(content, result[0].value)
    }

    @Test
    fun testUrlHashTag() {
        val parser = TwitterParser()
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
        val parser = TwitterParser()
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
        val parser = TwitterParser()
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
        val parser = TwitterParser()
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
        val parser = TwitterParser()
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
        val parser = TwitterParser()
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
        val parser = TwitterParser()
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
        val parser = TwitterParser()
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
        val parser = TwitterParser()
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
        val parser = TwitterParser()
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
        val parser = TwitterParser()
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
        val parser = TwitterParser()
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
        val parser = TwitterParser()
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
        val parser = TwitterParser()
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
        val parser = TwitterParser()
        val cashtag = "\$CASHTAG"
        val content = "$cashtag:　hello"
        val result = parser.parse(content)
        assertEquals(2, result.size)
        assertIs<CashTagToken>(result[0])
        assertEquals(cashtag, result[0].value)
        assertIs<StringToken>(result[1])
        assertEquals(":　hello", result[1].value)
    }
}