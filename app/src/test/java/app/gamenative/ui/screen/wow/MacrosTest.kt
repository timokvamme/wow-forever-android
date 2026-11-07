package app.gamenative.ui.screen.wow

import org.junit.Assert.assertEquals
import org.junit.Test

class MacrosTest {
    private val sample = """
        VER 3 0000000000000001 "Earth Shock" "INV_MISC_QUESTIONMARK"
        #showtooltip Earth Shock
        /startattack
        /cast Earth Shock
        END
        VER 3 0000000000000002 "Mount" "134400"
        /cast Swift Brown Steed
        END
    """.trimIndent() + "\n"

    @Test
    fun `parses and writes macros-cache back unchanged`() {
        val macros = Macros.parse(sample)
        assertEquals(2, macros.size)
        assertEquals("Earth Shock", macros[0].name)
        assertEquals("#showtooltip Earth Shock\n/startattack\n/cast Earth Shock", macros[0].body)
        assertEquals("134400", macros[1].icon)
        assertEquals(sample, Macros.serialize(macros))
    }

    @Test
    fun `new macros get the next free id`() {
        val macros = Macros.parse(sample)
        assertEquals("0000000000000003", Macros.newMacro(macros, "x", "/sit").id)
        assertEquals("0000000000000001", Macros.newMacro(emptyList(), "x", "/sit").id)
    }
}
