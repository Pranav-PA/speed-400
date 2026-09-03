package dev.pranav.speed400garage.ai

import dev.pranav.speed400garage.data.db.entity.HandbookChunkEntity

/**
 * Splits extracted handbook text into page-cited passages.
 *
 * Deliberately free of Android: chunking is pure text logic, so it lives apart from
 * the importer that needs a Context for PDFBox. That keeps it directly testable
 * instead of testable-only-through-a-fake, which is how the first attempt at this went
 * wrong.
 */
object HandbookChunker {

    const val MAX_CHUNK = 1_200

    /**
     * Splits one page into passages small enough to answer with — but never across a
     * page boundary, because a passage spanning two pages could not carry one honest
     * citation, and the citation is the entire point of the corpus.
     */
    fun chunksFor(page: Int, raw: String): List<HandbookChunkEntity> {
        val text = raw.replace(Regex("[ \\t]+"), " ").trim()
        if (text.isBlank()) return emptyList()

        val section = sectionOf(text)
        if (text.length <= MAX_CHUNK) {
            return listOf(HandbookChunkEntity(page = page, ordinal = 0, text = text, section = section))
        }

        val out = mutableListOf<HandbookChunkEntity>()
        val paragraphs = text.split(Regex("\\n{2,}")).filter { it.isNotBlank() }
        val buffer = StringBuilder()
        var ordinal = 0
        for (paragraph in paragraphs) {
            if (buffer.isNotEmpty() && buffer.length + paragraph.length > MAX_CHUNK) {
                out += HandbookChunkEntity(page = page, ordinal = ordinal++, text = buffer.toString().trim(), section = section)
                buffer.clear()
            }
            buffer.append(paragraph).append("\n\n")
        }
        if (buffer.isNotBlank()) {
            out += HandbookChunkEntity(page = page, ordinal = ordinal, text = buffer.toString().trim(), section = section)
        }
        return out
    }

    /**
     * The running header carries the section name — "MAINTENANCE AND ADJUSTMENT 134" —
     * which is free context for retrieval and worth keeping.
     */
    fun sectionOf(text: String): String? {
        val firstLine = text.lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: return null
        val header = firstLine.replace(Regex("^\\d+\\s+|\\s+\\d+$"), "").trim()
        return header.takeIf { it.length in 3..48 && it == it.uppercase() && it.any { c -> c.isLetter() } }
    }
}
