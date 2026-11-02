package com.openauto.dash

import com.openauto.dash.link.RecentCall
import org.junit.Assert.assertEquals
import org.junit.Test

class PhoneScreenTest {

    @Test
    fun oneNumberPerContact_theDefaultOne_byName() {
        val rows = listOf(
            ContactRow(2, "Léo Bernard", "+33 6 00 00 00 02", primary = false, starred = false),
            ContactRow(2, "Léo Bernard", "+33 1 00 00 00 02", primary = true, starred = false),
            ContactRow(1, "Camille Martin", "+33 6 00 00 00 01", primary = false, starred = true),
            ContactRow(3, "Élodie", "+33 6 00 00 00 03", primary = false, starred = false),
            ContactRow(4, "", "+33 6 00 00 00 04", primary = false, starred = false),
            ContactRow(5, "Nobody", " ", primary = false, starred = false)
        )
        val contacts = pickContacts(rows)
        // Accents sort with their letter; a contact with no name is listed as its number; one with no number is left out.
        assertEquals(listOf("+33 6 00 00 00 04", "Camille Martin", "Élodie", "Léo Bernard"), contacts.map { it.name })
        assertEquals("+33 1 00 00 00 02", contacts.first { it.name == "Léo Bernard" }.number)
        assertEquals(listOf(false, true, false, false), contacts.map { it.starred })
    }

    @Test
    fun theLetterOfAName_isItsFirstWithoutTheAccent() {
        assertEquals("E", indexLetter("Élodie"))
        assertEquals("C", indexLetter("  camille"))
        assertEquals("#", indexLetter("+33 6 00 00 00 04"))
        assertEquals("#", indexLetter(""))
    }

    @Test
    fun theListIsCutByLetter_signsLast() {
        val contacts = listOf(
            UnitContact("+33 6 00 00 00 04", "+33 6 00 00 00 04", false),
            UnitContact("Camille", "1", true),
            UnitContact("Claire", "2", false),
            UnitContact("Élodie", "3", false),
            UnitContact("Léo", "4", false)
        )
        val sections = contactSections(contacts)
        assertEquals(listOf("C", "E", "L", "#"), sections.map { it.letter })
        assertEquals(listOf("Camille", "Claire"), sections[0].contacts.map { it.name })
        // The row of each header: after the favourites block (its header and three rows), each section's header and rows.
        assertEquals(4, sectionRow(sections, 0, before = 4))
        assertEquals(7, sectionRow(sections, 1, before = 4))
        assertEquals(9, sectionRow(sections, 2, before = 4))
        assertEquals(0, sectionRow(sections, 0, before = 0))
    }

    @Test
    fun theKeypadStopsAtAFullNumber() {
        assertEquals("+3", keypadType(keypadType("", "+"), "3"))
        val full = "1".repeat(KEYPAD_MAX)
        assertEquals(full, keypadType(full, "2"))
    }

    @Test
    fun theEmptyRecentsAskToLinkThePhoneOnlyWhenItSentNothing() {
        assertEquals(R.string.phone_recents_empty, recentsEmptyText(null))
        assertEquals(R.string.phone_recents_none, recentsEmptyText(emptyList<RecentCall>()))
    }
}
