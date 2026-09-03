package dev.pranav.speed400garage.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HandbookChunkingTest {

    @Test
    fun `a short page becomes one passage`() {
        val chunks = HandbookChunker.chunksFor(
            134, "MAINTENANCE AND ADJUSTMENT 134\n\nThe vertical movement must be 20-25 mm."
        )
        assertEquals(1, chunks.size)
        assertEquals(134, chunks[0].page)
        assertEquals(0, chunks[0].ordinal)
    }

    @Test
    fun `a long page splits but never across a page boundary`() {
        // A passage spanning two pages could not carry one honest citation, which is
        // the entire reason the corpus exists.
        val paragraph = "word ".repeat(120)
        val page = (1..8).joinToString("\n\n") { paragraph }
        val chunks = HandbookChunker.chunksFor(50, page)
        assertTrue("should split", chunks.size > 1)
        assertTrue("all from page 50", chunks.all { it.page == 50 })
        assertEquals(chunks.indices.toList(), chunks.map { it.ordinal })
    }

    @Test
    fun `no passage exceeds the chunk ceiling by much`() {
        val paragraph = "word ".repeat(100)
        val chunks = HandbookChunker.chunksFor(60, (1..10).joinToString("\n\n") { paragraph })
        // Paragraphs are kept whole, so a single oversized paragraph can exceed the
        // ceiling — but nothing should be wildly over it.
        assertTrue(chunks.all { it.text.length <= HandbookChunker.MAX_CHUNK * 2 })
    }

    @Test
    fun `a blank page produces nothing`() {
        assertTrue(HandbookChunker.chunksFor(8, "   \n \n ").isEmpty())
    }

    @Test
    fun `the running header is captured as the section`() {
        assertEquals(
            "MAINTENANCE AND ADJUSTMENT",
            HandbookChunker.sectionOf("MAINTENANCE AND ADJUSTMENT 134\nDrive chain"),
        )
        assertEquals("SPECIFICATIONS", HandbookChunker.sectionOf("198 SPECIFICATIONS\nSpeed 400"))
    }

    @Test
    fun `ordinary prose is not mistaken for a section header`() {
        assertNull(HandbookChunker.sectionOf("The vertical movement of the drive chain must be 20-25 mm."))
    }

    @Test
    fun `every passage keeps its page so a citation is always possible`() {
        val chunks = (100..105).flatMap {
            HandbookChunker.chunksFor(it, "Page $it content here, long enough to matter.")
        }
        assertEquals(6, chunks.size)
        assertTrue(chunks.all { it.page in 100..105 })
        assertTrue("a chunk without a page is unciteable", chunks.none { it.page == 0 })
    }
}
