package de.handeltheke.handeldesk.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CoreTest {
    @Test fun searchSyntax() {
        val q = SearchSyntax.parse("Samsung \"BN59-01232B RMCTPJ1AP2\" -analog model:BN59 OR model:BN60")
        assertTrue(q.errors.isEmpty())
        assertEquals(3, q.positiveGroups.size)
        assertEquals(1, q.excluded.size)
        assertEquals(2, q.positiveGroups.last().size)
    }

    @Test fun seoPolicy() {
        assertEquals("original-karcher-ladegerat-5-5v-2a", HandelthekeSeoPolicy.slug("Original Kärcher Ladegerät 5.5V 2A"))
        assertEquals("originalnyj-pult-upravleniya", HandelthekeSeoPolicy.slug("оригинальный пульт управления"))
    }

    @Test fun mergeConflict() {
        assertEquals(MergeStatus.SAFE, ThreeWayMerge.compare(20, 22, 20).status)
        assertEquals(MergeStatus.CONFLICT, ThreeWayMerge.compare(20, 22, 21).status)
    }
}
