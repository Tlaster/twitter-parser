package moe.tlaster.twitter.parser

private const val MAX_URL_LENGTH = 4_096
private const val MAX_TCO_SLUG_LENGTH = 40
private const val URL_CHARACTER = 1
private const val URL_ENDING_CHARACTER = 2
private const val HASH_TAG_CHARACTER = 1
private const val HASH_TAG_LETTER_OR_MARK = 2
private const val ENTITY_MARKER_FLAG = 0x8000_0000L
private const val SCAN_END_MASK = 0x7FFF_FFFFL

internal class ParserEngine(
    private val enableEmoji: Boolean,
    private val extractUrlWithoutProtocol: Boolean,
    private val enableNonAsciiInUrl: Boolean,
    private val enableEscapeInUrl: Boolean,
    private val enableCJKInCashTag: Boolean,
    private val validMarkInUserName: List<Char>,
    private val validMarkInHashTag: List<Char>,
) {
    fun parse(input: String): List<Token> {
        if (input.isEmpty()) return emptyList()

        val canContainUrl = input.indexOf('.') >= 0
        var plainStart = 0
        var index = if (canContainUrl) 0 else input.nextEntityMarker(0, enableEmoji)
        if (index == input.length) {
            return ArrayList<Token>(1).also { it.add(StringToken(input)) }
        }
        val tokens = ArrayList<Token>(32)
        var urlSearchResume = 0
        var entitySearchResume = 0
        while (index < input.length) {
            var kind: TokenKind? = null
            var failedUrlResume = index + 1
            var urlHasEntityMarker = false
            var end = -1
            val mayStartUrl = canContainUrl && index >= urlSearchResume &&
                input[index].canStartDomainWithoutProtocol()
            val protocolLength = if (mayStartUrl) input.protocolLengthAt(index) else 0
            if (mayStartUrl && mayStartUrlCandidate(input, index, protocolLength, urlSearchResume)) {
                val urlScan = scanUrl(input, index, protocolLength)
                if (urlScan >= 0) {
                    val candidateEnd = urlScan.candidateEnd()
                    urlSearchResume = maxOf(urlSearchResume, candidateEnd)
                    val entityEnd = urlScan.entityEnd()
                    urlHasEntityMarker = urlScan.hasEntityMarker()
                    val canExtract = protocolLength > 0 || extractUrlWithoutProtocol &&
                        hasValidUrlBoundary(input, index, withoutProtocol = true)
                    if (canExtract) end = entityEnd
                } else if (urlScan < -1) {
                    val failedHostEnd = -urlScan.toInt() - 1
                    failedUrlResume = input.nextUrlCandidateStart(index + 1, failedHostEnd)
                }
            }
            if (end > index) {
                kind = TokenKind.Url
            } else {
                end = if (index < entitySearchResume) {
                    -1
                } else {
                    when (input[index]) {
                        '@', '＠' -> scanUserName(input, index).also {
                            if (it > index) kind = TokenKind.UserName
                        }
                        '#', '＃' -> scanHashTag(input, index).also {
                            if (it > index) kind = TokenKind.HashTag
                        }
                        '$', '＄' -> scanCashTag(input, index).also {
                            if (it > index) kind = TokenKind.CashTag
                        }
                        ':' -> scanEmoji(input, index).also {
                            if (it > index) kind = TokenKind.Emoji
                        }
                        else -> -1
                    }
                }
            }
            val failedEntityResume = if (end < -1) -end - 1 else index + 1

            if (kind == null) {
                if (canContainUrl && failedEntityResume > index + 1) {
                    urlSearchResume = scanOverlappedUrlCandidates(
                        input = input,
                        start = index + 1,
                        end = failedEntityResume,
                        currentResume = urlSearchResume,
                    )
                }
                val resume = maxOf(index + 1, failedUrlResume, failedEntityResume)
                index = if (canContainUrl) resume else input.nextEntityMarker(resume, enableEmoji)
                continue
            }

            val overlappedUrlStart = when (kind) {
                TokenKind.UserName -> if (validMarkInUserName.isEmpty()) {
                    input.indexOfBefore('/', index + 1, end).takeIf { it >= 0 }?.plus(1) ?: -1
                } else {
                    -1
                }
                TokenKind.HashTag -> if (validMarkInHashTag.isEmpty()) index + 1 else -1
                TokenKind.CashTag -> index + 1
                else -> -1
            }
            if (canContainUrl && overlappedUrlStart >= 0) {
                urlSearchResume = scanOverlappedUrlCandidates(
                    input = input,
                    start = overlappedUrlStart,
                    end = end,
                    currentResume = urlSearchResume,
                )
            }
            if (kind == TokenKind.Url && urlHasEntityMarker) {
                entitySearchResume = maxOf(
                    entitySearchResume,
                    scanOverlappedInvalidEntityResume(input, index, end),
                )
            }
            addPlain(tokens, input, plainStart, index)
            tokens.add(kind.create(input.slice(index, end)))
            plainStart = end
            index = if (canContainUrl) end else input.nextEntityMarker(end, enableEmoji)
        }
        addPlain(tokens, input, plainStart, input.length)
        return tokens
    }

    private fun mayStartUrlCandidate(
        input: String,
        start: Int,
        protocolLength: Int,
        searchStart: Int,
    ): Boolean {
        if (protocolLength == 0) {
            if (start == searchStart && input[start].isLatinAccent() && start + 1 < input.length &&
                input[start + 1].canStartDomainWithoutProtocol()
            ) {
                return false
            }
        }
        return hasValidUrlBoundary(input, start, withoutProtocol = false)
    }

    private fun scanOverlappedUrlCandidates(
        input: String,
        start: Int,
        end: Int,
        currentResume: Int,
    ): Int {
        var resume = currentResume
        var index = maxOf(start, resume)
        while (index < end) {
            if (!input[index].canStartDomainWithoutProtocol()) {
                index++
                continue
            }
            val protocolLength = input.protocolLengthAt(index)
            if (!mayStartUrlCandidate(input, index, protocolLength, resume)) {
                index++
                continue
            }
            val urlScan = scanUrl(input, index, protocolLength)
            if (urlScan >= 0) {
                resume = maxOf(resume, urlScan.candidateEnd())
                index = resume
            } else if (urlScan < -1) {
                index++
            } else {
                index++
            }
        }
        return resume
    }

    private fun scanOverlappedInvalidEntityResume(input: String, start: Int, end: Int): Int {
        var resume = 0
        var index = start
        while (index < end) {
            val candidateEnd = when (input[index]) {
                '@', '＠' -> scanUserName(input, index)
                '#', '＃' -> scanHashTag(input, index)
                else -> -1
            }
            if (candidateEnd < -1) resume = maxOf(resume, -candidateEnd - 1)
            index++
        }
        return resume
    }

    private fun scanUrl(input: String, start: Int, protocolLength: Int): Long {
        val hostStart = start + protocolLength
        val hostEnd = scanHost(input, hostStart, protocolLength > 0)
        if (hostEnd < 0) return hostEnd.toLong()

        var end = hostEnd
        var hasEntityMarker = false
        if (end < input.length && input[end] == ':') {
            var portEnd = end + 1
            while (portEnd < input.length && input[portEnd].isAsciiDigit()) portEnd++
            if (portEnd > end + 1) end = portEnd
        }
        if (end < input.length && input[end] == '/') {
            val pathScan = scanPath(input, end)
            end = pathScan.scanEnd()
            hasEntityMarker = pathScan.hasEntityMarker()
        }
        if (end < input.length && input[end] == '?') {
            val queryScan = scanQuery(input, end)
            end = queryScan.scanEnd()
            hasEntityMarker = hasEntityMarker || queryScan.hasEntityMarker()
        }
        if (enableNonAsciiInUrl && end < input.length && input[end].isExtendedUrlCharacter()) {
            val tailScan = scanUrlTail(input, end)
            end = tailScan.scanEnd()
            hasEntityMarker = hasEntityMarker || tailScan.hasEntityMarker()
        }

        val lengthWithProtocol = end - start + if (protocolLength == 0) 7 else 0
        if (lengthWithProtocol > MAX_URL_LENGTH) {
            return packUrlScan(entityEnd = -1, candidateEnd = end, hasEntityMarker = hasEntityMarker)
        }

        val isTco = protocolLength > 0 && input.regionEquals(hostStart, hostEnd, "t.co") &&
            hostEnd < input.length && input[hostEnd] == '/'
        if (!isTco) return packUrlScan(end, end, hasEntityMarker)

        val tcoEnd = scanTcoUrl(input, hostEnd)
        return packUrlScan(tcoEnd, end, hasEntityMarker)
    }

    private fun scanHost(input: String, start: Int, allowUnicode: Boolean): Int {
        if (start >= input.length || !input[start].isDomainBaseChar(allowUnicode)) return -1

        var bestEnd = -1
        var candidateEnd = -1
        var retryStart = -1
        var index = start
        while (index < input.length && input[index].isPotentialHostChar(allowUnicode)) {
            if (input[index] == '.' && index + 2 < input.length && input[index + 2] != '.' &&
                input[index + 2].isPotentialHostChar(true)
            ) {
                val tldEnd = scanTld(input, index + 1)
                if (tldEnd > index + 1) {
                    val validDomain = tldEnd - start <= 255 &&
                        isValidDomain(input, start, index, allowUnicode)
                    if (validDomain) {
                        bestEnd = maxOf(bestEnd, tldEnd)
                        candidateEnd = maxOf(candidateEnd, tldEnd)
                    } else if (isSyntacticDomain(input, start, index, allowUnicode)) {
                        candidateEnd = maxOf(candidateEnd, tldEnd)
                    } else if (retryStart < 0) {
                        retryStart = findSyntacticDomainStart(input, start + 1, index, allowUnicode)
                    }
                }
            }
            index++
        }
        return when {
            candidateEnd > bestEnd -> -candidateEnd - 1
            bestEnd >= 0 -> bestEnd
            retryStart >= 0 -> -retryStart - 1
            else -> -index - 1
        }
    }

    private fun findSyntacticDomainStart(
        input: String,
        start: Int,
        tldDot: Int,
        allowUnicode: Boolean,
    ): Int {
        for (candidate in start until tldDot) {
            if (input[candidate].isDomainBaseChar(allowUnicode) &&
                hasValidUrlBoundary(input, candidate, withoutProtocol = false) &&
                isSyntacticDomain(input, candidate, tldDot, allowUnicode)
            ) {
                return candidate
            }
        }
        return -1
    }

    private fun isSyntacticDomain(input: String, start: Int, tldDot: Int, allowUnicode: Boolean): Boolean {
        var labelStart = start
        while (labelStart < tldDot) {
            var labelEnd = labelStart
            while (labelEnd < tldDot && input[labelEnd] != '.') labelEnd++
            if (labelEnd == labelStart || !input[labelStart].isDomainBaseChar(allowUnicode) ||
                !input[labelEnd - 1].isDomainBaseChar(allowUnicode)
            ) {
                return false
            }

            val isDomainName = labelEnd == tldDot
            for (index in labelStart until labelEnd) {
                val current = input[index]
                if (!current.isDomainBaseChar(allowUnicode) && current != '-' &&
                    (isDomainName || current != '_')
                ) {
                    return false
                }
            }
            labelStart = labelEnd + 1
        }
        return labelStart == tldDot + 1
    }

    private fun scanTld(input: String, start: Int): Int {
        if (start >= input.length) return -1
        var bestEnd = -1
        val hasPunycodePrefix = input.hasPunycodeTldPrefix(start)

        var end = start
        while (end < input.length && (input[end].isAsciiAlphanumeric() || input[end] == '-')) {
            end++
            if (end - start >= 2 &&
                (hasPunycodePrefix && end - start > 4 ||
                    isValidTldBoundary(input, end) && isKnownDomain(input, start, end))
            ) {
                bestEnd = end
            }
        }

        val stoppedAtUnicode = end < input.length && input[end].isPotentialHostChar(true) && input[end] != '.'
        if (!input[start].isAsciiAlphanumeric() || stoppedAtUnicode) {
            UnicodeDomainList.forEach { tld ->
                val candidateEnd = start + tld.length
                if (candidateEnd <= input.length && input.regionEquals(start, candidateEnd, tld) &&
                    isValidTldBoundary(input, candidateEnd)
                ) {
                    bestEnd = maxOf(bestEnd, candidateEnd)
                }
            }
        }
        return bestEnd
    }

    private fun isValidDomain(input: String, start: Int, tldDot: Int, allowUnicode: Boolean): Boolean {
        var labelStart = start
        while (labelStart < tldDot) {
            var labelEnd = labelStart
            while (labelEnd < tldDot && input[labelEnd] != '.') labelEnd++
            if (labelEnd == labelStart || labelEnd - labelStart > 63) return false

            val isDomainName = labelEnd == tldDot
            if (!input[labelStart].isDomainBaseChar(allowUnicode) ||
                !input[labelEnd - 1].isDomainBaseChar(allowUnicode)
            ) {
                return false
            }
            if (labelEnd - labelStart > 4 && input.regionEquals(labelStart, labelStart + 4, "xn--") &&
                (labelStart + 4 until labelEnd).any {
                    !input[it].isAsciiAlphanumeric() && input[it] != '-' && input[it] != '_' &&
                        !input[it].isAsciiMappedByIdn()
                }
            ) {
                return false
            }
            for (index in labelStart until labelEnd) {
                val current = input[index]
                val valid = current.isDomainBaseChar(allowUnicode) || current == '-' ||
                    (!isDomainName && current == '_')
                if (!valid) return false
            }
            labelStart = labelEnd + 1
        }
        return labelStart == tldDot + 1
    }

    private fun scanTcoUrl(input: String, slash: Int): Int {
        var end = slash + 1
        val slugStart = end
        while (end < input.length && input[end].isAsciiAlphanumeric()) end++
        val slugLength = end - slugStart
        if (slugLength == 0) return slash + 1
        if (slugLength > MAX_TCO_SLUG_LENGTH) return -1
        if (end < input.length && input[end] == '?') end = scanQuery(input, end).scanEnd()
        return end
    }

    private fun scanPath(input: String, slash: Int): Long {
        var index = slash + 1
        var lastValidEnd = index
        var hasEntityMarker = false
        while (index < input.length) {
            val current = input[index]
            when {
                current == '?' -> break
                enableEscapeInUrl && current == '(' -> {
                    val canEndAtParenthesis = index == lastValidEnd
                    val balancedScan = scanBalancedParentheses(input, index, 1)
                    if (balancedScan < 0) break
                    index = balancedScan.scanEnd()
                    hasEntityMarker = hasEntityMarker || balancedScan.hasEntityMarker()
                    if (canEndAtParenthesis) lastValidEnd = index
                }
                else -> {
                    val classification = urlPathCharacterClass(current)
                    if (classification == 0) break
                    if (current.isMentionOrHashMarker()) hasEntityMarker = true
                    index++
                    if (classification and URL_ENDING_CHARACTER != 0) lastValidEnd = index
                }
            }
        }
        return packSegmentScan(lastValidEnd, hasEntityMarker)
    }

    private fun scanBalancedParentheses(input: String, start: Int, depth: Int): Long {
        if (depth > 2) return -1L
        var index = start + 1
        var hasContent = false
        var hasEntityMarker = false
        while (index < input.length) {
            when {
                input[index] == '(' -> {
                    val nestedScan = scanBalancedParentheses(input, index, depth + 1)
                    if (nestedScan < 0) return -1L
                    index = nestedScan.scanEnd()
                    hasEntityMarker = hasEntityMarker || nestedScan.hasEntityMarker()
                    hasContent = true
                }
                input[index] == ')' -> {
                    return if (hasContent) packSegmentScan(index + 1, hasEntityMarker) else -1L
                }
                urlPathCharacterClass(input[index]) != 0 -> {
                    if (input[index].isMentionOrHashMarker()) hasEntityMarker = true
                    index++
                    hasContent = true
                }
                else -> return -1L
            }
        }
        return -1L
    }

    private fun scanQuery(input: String, questionMark: Int): Long {
        var index = questionMark + 1
        var lastValidEnd = questionMark
        var hasEntityMarker = false
        while (index < input.length) {
            val classification = urlQueryCharacterClass(input[index])
            if (classification == 0) break
            if (input[index].isMentionOrHashMarker()) hasEntityMarker = true
            index++
            if (classification and URL_ENDING_CHARACTER != 0) lastValidEnd = index
        }
        return packSegmentScan(lastValidEnd, hasEntityMarker)
    }

    private fun scanUrlTail(input: String, start: Int): Long {
        var index = start
        var lastValidEnd = start
        var hasEntityMarker = false
        while (index < input.length) {
            val current = input[index]
            when {
                current == '?' -> {
                    val queryScan = scanQuery(input, index)
                    return packSegmentScan(
                        maxOf(lastValidEnd, queryScan.scanEnd()),
                        hasEntityMarker || queryScan.hasEntityMarker(),
                    )
                }
                enableEscapeInUrl && current == '(' -> {
                    val canEndAtParenthesis = index == lastValidEnd
                    val balancedScan = scanBalancedParentheses(input, index, 1)
                    if (balancedScan < 0) break
                    index = balancedScan.scanEnd()
                    hasEntityMarker = hasEntityMarker || balancedScan.hasEntityMarker()
                    if (canEndAtParenthesis) lastValidEnd = index
                }
                else -> {
                    val classification = urlPathCharacterClass(current)
                    if (classification == 0) break
                    if (current.isMentionOrHashMarker()) hasEntityMarker = true
                    index++
                    if (classification and URL_ENDING_CHARACTER != 0) lastValidEnd = index
                }
            }
        }
        return packSegmentScan(lastValidEnd, hasEntityMarker)
    }

    private fun urlPathCharacterClass(character: Char): Int {
        val core = character.isAsciiAlphanumeric() || character.isLatinAccent() || character.code in 0x0400..0x04FF
        val ending = core || character in "=_#/-+"
        val body = ending || character == '\u2013' || character in "!*';:,.\$%[]~|&@"
        if (body) {
            if (!enableEscapeInUrl && character.isUrlEscapeCharacter()) return 0
            return URL_CHARACTER or if (ending) URL_ENDING_CHARACTER else 0
        }
        return if (enableNonAsciiInUrl && character.isExtendedUrlCharacter()) {
            URL_CHARACTER or URL_ENDING_CHARACTER
        } else {
            0
        }
    }

    private fun urlQueryCharacterClass(character: Char): Int {
        val alphanumeric = character.isAsciiAlphanumeric()
        val ending = alphanumeric || character in "-_&=#/"
        val body = ending || character in "!?*'();:+\$%[],.~|@"
        if (body) {
            if (!enableEscapeInUrl && character.isUrlEscapeCharacter()) return 0
            return URL_CHARACTER or if (ending) URL_ENDING_CHARACTER else 0
        }
        return if (enableNonAsciiInUrl && character.isExtendedUrlCharacter()) {
            URL_CHARACTER or URL_ENDING_CHARACTER
        } else {
            0
        }
    }

    private fun scanUserName(input: String, start: Int): Int {
        if (validMarkInUserName.isNotEmpty()) return scanLegacyConfiguredUserName(input, start)
        if (!hasValidConfiguredMentionBoundary(input, start)) return -1

        var end = start + 1
        val limit = if (validMarkInUserName.isEmpty()) minOf(input.length, start + 21) else input.length
        while (end < limit) {
            val current = input[end]
            when {
                current.isAsciiAlphanumericUnderscore() -> {
                    end++
                }
                current in validMarkInUserName && end + 1 < input.length &&
                    input[end + 1].isLetterOrDigit() -> {
                    end++
                }
                else -> break
            }
        }
        if (end == start + 1) return -1

        if (end < input.length && input[end] == '/' && end + 1 < input.length && input[end + 1].isAsciiAlpha()) {
            var listEnd = end + 2
            var listLength = 1
            while (listEnd < input.length && listLength < 25 &&
                (input[listEnd].isAsciiAlphanumericUnderscore() || input[listEnd] == '-')
            ) {
                listEnd++
                listLength++
            }
            end = listEnd
        }

        if (end < input.length && (input[end] == '@' || input[end] == '＠')) {
            var resume = end + 1
            while (resume < input.length && (input[resume] == '@' || input[resume] == '＠')) resume++
            return -resume - 1
        }
        if (end < input.length && input[end].isLatinAccent()) return -end - 2
        if (end + 2 < input.length && input.startsWith("://", end)) return -1
        return end
    }

    private fun scanLegacyConfiguredUserName(input: String, start: Int): Int {
        if (start > 0) {
            val previous = input[start - 1]
            val validBoundary = previous.isLegacyEmptyCharacter() ||
                previous !in validMarkInUserName && !previous.isAsciiAlphanumeric() ||
                previous.isFullWidthCharacter()
            if (!validBoundary) return -1
        }
        if (start + 1 >= input.length) return -1
        val first = input[start + 1]
        if (!first.isAsciiAlphanumericUnderscore() && first !in validMarkInUserName) return -1

        var end = start + 1
        while (end < input.length) {
            val current = input[end]
            when {
                current.isAsciiAlphanumericUnderscore() -> end++
                current in validMarkInUserName -> {
                    if (end + 1 < input.length && input[end + 1].isLetterOrDigit()) end++ else break
                }
                else -> break
            }
        }
        return end
    }

    private fun hasValidConfiguredMentionBoundary(input: String, start: Int): Boolean {
        var atRunStart = start
        while (atRunStart > 0 && (input[atRunStart - 1] == '@' || input[atRunStart - 1] == '＠')) atRunStart--
        if (atRunStart > 0 && input[atRunStart - 1] in validMarkInUserName) return false
        return hasValidMentionBoundary(input, atRunStart)
    }

    private fun scanHashTag(input: String, start: Int): Int {
        if (validMarkInHashTag.isNotEmpty()) return scanLegacyConfiguredHashTag(input, start)
        if (!hasValidConfiguredHashTagBoundary(input, start) || start + 1 >= input.length) return -1
        if (input[start + 1] == '\uFE0F' || input[start + 1] == '\u20E3') return -1

        var end = start + 1
        var hasLetterOrMark = false
        while (end < input.length) {
            val codePoint = input.codePointAt(end)
            val classification = configuredHashTagCharacterClass(codePoint)
            if (classification == 0) break
            if (classification and HASH_TAG_LETTER_OR_MARK != 0) hasLetterOrMark = true
            end += codePoint.charCount()
        }
        if (!hasLetterOrMark) return -1
        if (end < input.length && (input[end] == '#' || input[end] == '＃')) return -end - 2
        if (end + 2 < input.length && input.startsWith("://", end)) return -1
        return end
    }

    private fun scanLegacyConfiguredHashTag(input: String, start: Int): Int {
        if (start > 0) {
            val previous = input[start - 1]
            if (!previous.isLegacyEmptyCharacter() && !previous.isFullWidthCharacter()) return -1
        }
        if (start + 1 >= input.length) return -1
        val first = input[start + 1]
        if (!first.isLetterOrDigit() && first != '_' && first !in validMarkInHashTag &&
            !first.isFullWidthCharacter()
        ) {
            return -1
        }

        var end = start + 1
        while (end < input.length) {
            val current = input[end]
            if (current.isLetterOrDigit() || current == '_' || current in validMarkInHashTag ||
                current.isFullWidthCharacter() && !current.isFullWidthSymbol()
            ) {
                end++
            } else {
                break
            }
        }
        return end
    }

    private fun hasValidConfiguredHashTagBoundary(input: String, start: Int): Boolean {
        if (start > 0 && input[start - 1] in validMarkInHashTag) return false
        return hasValidHashTagBoundary(input, start)
    }

    private fun configuredHashTagCharacterClass(codePoint: Int): Int {
        val classification = codePoint.hashTagCharacterClass()
        if (classification != 0) return classification
        return if (codePoint <= 0xFFFF && codePoint.toChar() in validMarkInHashTag) {
            HASH_TAG_CHARACTER
        } else {
            0
        }
    }

    private fun scanCashTag(input: String, start: Int): Int {
        if (start + 1 >= input.length) return -1

        val first = input[start + 1]
        val legacyExtension = enableCJKInCashTag &&
            (input[start] == '＄' || first.isFullWidthCharacter() && !first.isFullWidthSymbol() ||
                first.isAsciiDigit())
        if (legacyExtension) {
            if (start > 0 && !input[start - 1].isLegacyEmptyCharacter()) return -1
            return when {
                first.isAsciiAlpha() -> scanLegacyAsciiCashTag(input, start)
                first.isFullWidthCharacter() && !first.isFullWidthSymbol() -> scanCjkCashTag(input, start)
                first.isAsciiDigit() -> scanDigitCashTag(input, start)
                else -> -1
            }
        }
        if (input[start] == '＄' || !hasValidCashTagBoundary(input, start)) return -1
        return when {
            first.isAsciiAlpha() -> scanTwitterTextCashTag(input, start)
            else -> -1
        }
    }

    private fun scanTwitterTextCashTag(input: String, start: Int): Int {
        var end = start + 1
        val baseLimit = minOf(input.length, start + 7)
        while (end < baseLimit && input[end].isAsciiAlpha()) end++
        if (end == start + 1) return -1
        val baseEnd = end

        if (end < input.length && (input[end] == '.' || input[end] == '_') &&
            end + 1 < input.length && input[end + 1].isAsciiAlpha()
        ) {
            var suffixEnd = end + 1
            val suffixLimit = minOf(input.length, end + 3)
            while (suffixEnd < suffixLimit && input[suffixEnd].isAsciiAlpha()) suffixEnd++
            end = suffixEnd
        }

        if (end < input.length && !input[end].isAsciiWhitespace() && !input[end].isAsciiPunctuation()) {
            return if (baseEnd != end) baseEnd else -1
        }
        return end
    }

    private fun scanLegacyAsciiCashTag(input: String, start: Int): Int {
        var end = start + 1
        var length = 0
        while (end < input.length && input[end].isAsciiAlphanumeric() && length < 20) {
            end++
            length++
        }
        if (length == 20 || end == input.length) return end
        return if (input[end].isLegacyEmptyCharacter() || input[end].isLegacyUrlMark()) end else -1
    }

    private fun scanCjkCashTag(input: String, start: Int): Int {
        var end = start + 1
        var length = 0
        while (end < input.length && length < 10) {
            val current = input[end]
            if ((current.isFullWidthCharacter() && !current.isFullWidthSymbol()) ||
                current.isAsciiAlphanumericUnderscore()
            ) {
                end++
                length++
            } else {
                break
            }
        }
        return end
    }

    private fun scanDigitCashTag(input: String, start: Int): Int {
        var end = start + 1
        var length = 0
        while (end < input.length && length < 10) {
            val current = input[end]
            if (current == 'k' || current == 'K' || current == 'm' || current == 'M' ||
                current == 'b' || current == 'B' || current == '.' || current == ','
            ) {
                return -1
            }
            if (current.isLegacyEmptyCharacter() || current.isDigitCashDelimiter()) return end
            end++
            length++
        }
        return end
    }

    private fun scanEmoji(input: String, start: Int): Int {
        if (!enableEmoji || start + 1 >= input.length || !input[start + 1].isAsciiAlphanumericUnderscore()) {
            return -1
        }
        var end = start + 2
        while (end < input.length && input[end].isAsciiAlphanumericUnderscore()) end++
        return if (end < input.length && input[end] == ':') end + 1 else -1
    }

    private fun isKnownDomain(input: String, start: Int, end: Int): Boolean {
        return AsciiTldMatcher.contains(input, start, end)
    }

    private fun addPlain(tokens: ArrayList<Token>, input: String, start: Int, end: Int) {
        if (start < end) tokens.add(StringToken(input.slice(start, end)))
    }

    private fun String.slice(start: Int, end: Int): String {
        return if (start == 0 && end == length) this else substring(start, end)
    }
}

private enum class TokenKind {
    Url,
    CashTag,
    UserName,
    HashTag,
    Emoji,
    ;

    fun create(value: String): Token = when (this) {
        Url -> UrlToken(value)
        CashTag -> CashTagToken(value)
        UserName -> UserNameToken(value)
        HashTag -> HashTagToken(value)
        Emoji -> EmojiToken(value)
    }
}

private object AsciiTldMatcher {
    private const val TABLE_SIZE = 4_096
    private const val TABLE_MASK = TABLE_SIZE - 1
    private const val FNV_OFFSET_BASIS = -2_128_831_035
    private const val FNV_PRIME = 16_777_619

    private val slots = arrayOfNulls<String>(TABLE_SIZE).also { table ->
        DomainList.forEach { table.add(it) }
        TwitterTextLegacyDomainList.forEach { table.add(it) }
    }

    fun contains(input: String, start: Int, end: Int): Boolean {
        var slot = input.hashAsciiUppercase(start, end).slot()
        while (true) {
            val candidate = slots[slot] ?: return false
            if (input.regionEqualsAsciiUppercase(start, end, candidate)) return true
            slot = (slot + 1) and TABLE_MASK
        }
    }

    private fun Array<String?>.add(domain: String) {
        if (domain in TwitterTextExcludedDomainList) return
        var slot = domain.hashAsciiUppercase().slot()
        while (true) {
            val existing = this[slot]
            if (existing == null) {
                this[slot] = domain
                return
            }
            if (existing == domain) return
            slot = (slot + 1) and TABLE_MASK
        }
    }

    private fun Int.slot(): Int = (this xor (this ushr 16)) and TABLE_MASK

    private fun String.hashAsciiUppercase(): Int {
        var hash = FNV_OFFSET_BASIS
        for (character in this) hash = (hash xor character.code) * FNV_PRIME
        return hash
    }

    private fun String.hashAsciiUppercase(start: Int, end: Int): Int {
        var hash = FNV_OFFSET_BASIS
        for (index in start until end) hash = (hash xor this[index].asciiUppercase().code) * FNV_PRIME
        return hash
    }

    private fun String.regionEqualsAsciiUppercase(start: Int, end: Int, value: String): Boolean {
        if (end - start != value.length) return false
        for (offset in value.indices) {
            if (this[start + offset].asciiUppercase() != value[offset]) return false
        }
        return true
    }
}

private fun packUrlScan(entityEnd: Int, candidateEnd: Int, hasEntityMarker: Boolean): Long {
    return ((entityEnd + 1).toLong() shl 32) or candidateEnd.toLong() or
        if (hasEntityMarker) ENTITY_MARKER_FLAG else 0
}

private fun Long.entityEnd(): Int = (this ushr 32).toInt() - 1

private fun Long.candidateEnd(): Int = (this and SCAN_END_MASK).toInt()

private fun packSegmentScan(end: Int, hasEntityMarker: Boolean): Long {
    return end.toLong() or if (hasEntityMarker) ENTITY_MARKER_FLAG else 0
}

private fun Long.scanEnd(): Int = (this and SCAN_END_MASK).toInt()

private fun Long.hasEntityMarker(): Boolean = this and ENTITY_MARKER_FLAG != 0L

private fun String.protocolLengthAt(start: Int): Int {
    if (start + 6 >= length || this[start] != 'h' && this[start] != 'H' ||
        this[start + 1] != 't' && this[start + 1] != 'T' ||
        this[start + 2] != 't' && this[start + 2] != 'T' ||
        this[start + 3] != 'p' && this[start + 3] != 'P'
    ) {
        return 0
    }
    if (this[start + 4] == ':' && this[start + 5] == '/' && this[start + 6] == '/') return 7
    if (start + 7 < length &&
        (this[start + 4] == 's' || this[start + 4] == 'S' || this[start + 4] == '\u017F') &&
        this[start + 5] == ':' && this[start + 6] == '/' && this[start + 7] == '/'
    ) {
        return 8
    }
    return 0
}

private fun String.embeddedProtocolStart(start: Int, end: Int): Int {
    var index = start
    while (index < end) {
        if (protocolLengthAt(index) > 0) return index
        index++
    }
    return -1
}

private fun String.nextEntityMarker(start: Int, enableEmoji: Boolean): Int {
    var index = start
    while (index < length) {
        when (this[index]) {
            '@', '＠', '#', '＃', '$', '＄' -> return index
            ':' -> if (enableEmoji) return index
        }
        index++
    }
    return length
}

private fun String.indexOfBefore(character: Char, start: Int, end: Int): Int {
    var index = start
    while (index < end) {
        if (this[index] == character) return index
        index++
    }
    return -1
}

private fun String.nextUrlCandidateStart(start: Int, failedHostEnd: Int): Int {
    if (failedHostEnd >= length || this[failedHostEnd] != ':') return failedHostEnd
    return embeddedProtocolStart(start, failedHostEnd).takeIf { it >= 0 } ?: failedHostEnd
}

private fun hasValidUrlBoundary(input: String, start: Int, withoutProtocol: Boolean): Boolean {
    if (start == 0) return true
    val previous = input[start - 1]
    if (previous.isAsciiAlphanumeric() || previous == '@' || previous == '＠' ||
        previous == '$' || previous == '#' || previous == '＃' || previous == '\uFFFE' ||
        previous == '\uFEFF' || previous == '\uFFFF'
    ) {
        return false
    }
    return !withoutProtocol || previous != '-' && previous != '_' && previous != '.' && previous != '/'
}

private fun hasValidMentionBoundary(input: String, atRunStart: Int): Boolean {
    if (atRunStart == 0) return true
    val previous = input[atRunStart - 1]
    if (!previous.isAsciiAlphanumericUnderscore()) {
        when (previous) {
            '!', '#', '$', '%', '&', '*', '@', '＠' -> Unit
            else -> return true
        }
    }

    val rtEnd = atRunStart
    val rtStart = when {
        rtEnd >= 3 && input[rtEnd - 1] == ':' && input.regionEquals(rtEnd - 3, rtEnd - 1, "RT") -> rtEnd - 3
        rtEnd >= 2 && input.regionEquals(rtEnd - 2, rtEnd, "RT") -> rtEnd - 2
        else -> return false
    }
    return rtStart == 0 || input[rtStart - 1].let {
        !it.isAsciiAlphanumericUnderscore() && it != '+' && it != '~' && it != '.' && it != '-'
    }
}

private fun hasValidHashTagBoundary(input: String, start: Int): Boolean {
    if (start == 0) return true
    if (input[start - 1] == '\uFE0E' || input[start - 1] == '\uFE0F') return true
    val previousStart = input.previousCodePointStart(start)
    val previous = input.codePointAt(previousStart)
    return previous != '&'.code && !previous.isHashTagCharacter()
}

private fun hasValidCashTagBoundary(input: String, start: Int): Boolean {
    return start == 0 || input[start - 1].isTwitterTextSpace() || input[start - 1].isDirectionalCharacter()
}

private fun isValidTldBoundary(input: String, end: Int): Boolean {
    if (end == input.length) return true
    val next = input[end]
    return !next.isAsciiAlphanumeric() && next != '@' && next != '+' && next != '-'
}

private fun String.hasPunycodeTldPrefix(start: Int): Boolean =
    start + 4 <= length && regionEquals(start, start + 4, "xn--")

private fun String.regionEquals(start: Int, end: Int, value: String): Boolean {
    return end - start == value.length && startsWith(value, start, ignoreCase = true)
}

private fun String.codePointAt(index: Int): Int {
    val high = this[index].code
    if (high !in 0xD800..0xDBFF || index + 1 >= length) return high
    val low = this[index + 1].code
    if (low !in 0xDC00..0xDFFF) return high
    return 0x10000 + ((high - 0xD800) shl 10) + (low - 0xDC00)
}

private fun String.previousCodePointStart(index: Int): Int {
    if (index < 2 || this[index - 1].code !in 0xDC00..0xDFFF || this[index - 2].code !in 0xD800..0xDBFF) {
        return index - 1
    }
    return index - 2
}

private fun Int.charCount(): Int = if (this >= 0x10000) 2 else 1

private fun Int.isHashTagCharacter(): Boolean = hashTagCharacterClass() != 0

private fun Int.hashTagCharacterClass(): Int {
    if (this < 0x80) {
        if ((this or 0x20) in 'a'.code..'z'.code) {
            return HASH_TAG_CHARACTER or HASH_TAG_LETTER_OR_MARK
        }
        return if (this - '0'.code in 0..9 || this == '_'.code) HASH_TAG_CHARACTER else 0
    }
    if (isHashTagLetterOrMark()) return HASH_TAG_CHARACTER or HASH_TAG_LETTER_OR_MARK
    return if (isHashTagNumeral() || this == '_'.code || this == 0x200C || this == 0x200D ||
        this == 0xA67E || this == 0x05BE || this == 0x05F3 || this == 0x05F4 || this == 0xFF5E ||
        this == 0x301C || this == 0x309B || this == 0x309C || this == 0x30A0 || this == 0x30FB ||
        this == 0x3003 || this == 0x0F0B || this == 0x0F0C || this == 0x00B7
    ) {
        HASH_TAG_CHARACTER
    } else {
        0
    }
}

private fun Int.isHashTagLetterOrMark(): Boolean {
    if (this <= 0xFFFF) {
        val character = toChar()
        return character.isLetter() || character.category == CharCategory.NON_SPACING_MARK ||
            character.category == CharCategory.COMBINING_SPACING_MARK ||
            character.category == CharCategory.ENCLOSING_MARK
    }
    return this == 0x20021 || this in 0x16F00..0x16F4A || this in 0x16F50..0x16F87
}

private fun Int.isHashTagNumeral(): Boolean {
    return this <= 0xFFFF && toChar().isDigit() || this in 0x104A0..0x104A9
}

private fun Char.canStartDomainWithoutProtocol(): Boolean = isAsciiAlphanumeric() || isLatinAccent()

private fun Char.isPotentialHostChar(allowUnicode: Boolean): Boolean {
    return this == '.' || this == '-' || this == '_' || isAsciiAlphanumeric() || isLatinAccent() ||
        allowUnicode && isUrlUnicodeDomainChar()
}

private fun Char.isDomainBaseChar(allowUnicode: Boolean): Boolean {
    return isAsciiAlphanumeric() || isLatinAccent() || allowUnicode && isUrlUnicodeDomainChar()
}

private fun Char.isUrlUnicodeDomainChar(): Boolean {
    if (isWhitespace() || code in 0x2000..0x206F) return false
    if (this == '＠' || this == '＃' || this == '＄') return false
    return this !in "-_!\"#\$%&'()*+,./:;<=>?@[\\]^`{|}~"
}

private fun Char.isUrlEscapeCharacter(): Boolean = this in "!~*'();:+,%[]"

private fun Char.isMentionOrHashMarker(): Boolean = this == '@' || this == '＠' || this == '#' || this == '＃'

private fun Char.isExtendedUrlCharacter(): Boolean = code > 0x7F && !isTwitterTextSpace()

private fun Char.isLegacyEmptyCharacter(): Boolean {
    return this == '\u0009' || this == '\u000A' || this == '\u000C' || this == '\u0020' || this == '　'
}

private fun Char.isLegacyUrlMark(): Boolean = this in "-._~:/?#[]@!\$&'()*+,;="

private fun Char.isDigitCashDelimiter(): Boolean = this in "-._~:?#[]@!\$&'()*+;="

private fun Char.isTwitterTextSpace(): Boolean {
    return code in 0x0009..0x000D || this == '\u0020' || this == '\u0085' || this == '\u00A0' ||
        this == '\u1680' || this == '\u180E' || code in 0x2000..0x200A || this == '\u2028' ||
        this == '\u2029' || this == '\u202F' || this == '\u205F' || this == '\u3000'
}

private fun Char.isDirectionalCharacter(): Boolean {
    return this == '\u061C' || this == '\u200E' || this == '\u200F' || code in 0x202A..0x202E ||
        code in 0x2066..0x2069
}

private fun Char.isLatinAccent(): Boolean {
    if (code !in 0x00C0..0x1EFF) return false
    return code in 0x00C0..0x00D6 || code in 0x00D8..0x00F6 || code in 0x00F8..0x024F ||
        this == '\u0253' || this == '\u0254' || this == '\u0256' || this == '\u0257' ||
        this == '\u0259' || this == '\u025B' || this == '\u0263' || this == '\u0268' ||
        this == '\u026F' || this == '\u0272' || this == '\u0289' || this == '\u028B' ||
        this == '\u02BB' || code in 0x0300..0x036F || code in 0x1E00..0x1EFF
}

private fun Char.isAsciiMappedByIdn(): Boolean {
    return this == '\u00DF' || code in 0x0132..0x0133 || this == '\u017F' ||
        code in 0x01C7..0x01CC || code in 0x01F1..0x01F3 || this == '\u034F'
}

private fun Char.isFullWidthCharacter(): Boolean {
    return code in 0x1100..0x115F || code == 0x2329 || code == 0x232A ||
        code in 0x2E80..0xA4CF && code != 0x303F || code in 0xAC00..0xD7A3 ||
        code in 0xF900..0xFAFF || code in 0xFE10..0xFE19 || code in 0xFE30..0xFE6F ||
        code in 0xFF01..0xFF60 || code in 0xFFE0..0xFFE6
}

private fun Char.isFullWidthSymbol(): Boolean {
    return code in 0x3001..0x303D || code in 0xFE10..0xFE19 || code in 0xFE30..0xFE4F ||
        code in 0xFE50..0xFE6F || code in 0xFF01..0xFF0F || code in 0xFF1A..0xFF20 ||
        code in 0xFF3B..0xFF40 || code in 0xFF5B..0xFF60
}

private fun Char.isAsciiAlpha(): Boolean = (code or 0x20) in 'a'.code..'z'.code

private fun Char.isAsciiDigit(): Boolean = code - '0'.code in 0..9

private fun Char.isAsciiAlphanumeric(): Boolean = isAsciiAlpha() || isAsciiDigit()

private fun Char.isAsciiAlphanumericUnderscore(): Boolean = isAsciiAlphanumeric() || this == '_'

private fun Char.isAsciiWhitespace(): Boolean = code in 0x0009..0x000D || this == ' '

private fun Char.isAsciiPunctuation(): Boolean = code in 0x0021..0x002F || code in 0x003A..0x0040 ||
    code in 0x005B..0x0060 || code in 0x007B..0x007E

private fun Char.asciiUppercase(): Char = if (this in 'a'..'z') (code - 32).toChar() else this
