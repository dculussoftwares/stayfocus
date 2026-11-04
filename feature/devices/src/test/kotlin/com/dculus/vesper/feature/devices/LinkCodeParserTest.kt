package com.dculus.vesper.feature.devices

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LinkCodeParserTest {
    private val linkId = "abcdefghijklmnopqrstuv"

    @Test
    fun parsesAValidLink() = assertEquals(linkId, LinkCodeParser.parseToken("vesper://link?t=$linkId"))

    @Test
    fun trimsSurroundingWhitespace() = assertEquals(linkId, LinkCodeParser.parseToken("  vesper://link?t=$linkId\n"))

    @Test
    fun acceptsUrlSafeCharacters() {
        val t = "a-b_c-d_e-f_g-h_i-j_k-l"
        assertEquals(t, LinkCodeParser.parseToken("vesper://link?t=$t"))
    }

    @Test
    fun ignoresOtherQueryParameters() = assertEquals(linkId, LinkCodeParser.parseToken("vesper://link?x=1&t=$linkId"))

    @Test
    fun rejectsOtherSchemesAndHosts() {
        assertNull(LinkCodeParser.parseToken("https://link?t=$linkId"))
        assertNull(LinkCodeParser.parseToken("vesper://other?t=$linkId"))
        assertNull(LinkCodeParser.parseToken("https://example.com/?t=$linkId"))
    }

    @Test
    fun rejectsMissingEmptyOrMalformedTokens() {
        assertNull(LinkCodeParser.parseToken("vesper://link"))
        assertNull(LinkCodeParser.parseToken("vesper://link?t="))
        assertNull(LinkCodeParser.parseToken("vesper://link?t=short"))
        assertNull(LinkCodeParser.parseToken("vesper://link?t=${"a".repeat(200)}"))
        assertNull(LinkCodeParser.parseToken("vesper://link?t=abc/def+ghi==jklmnopqrstu"))
    }

    @Test
    fun rejectsGarbage() {
        assertNull(LinkCodeParser.parseToken(""))
        assertNull(LinkCodeParser.parseToken("not a url at all"))
        assertNull(LinkCodeParser.parseToken("vesper://link?t=$linkId#frag with space"))
    }
}
