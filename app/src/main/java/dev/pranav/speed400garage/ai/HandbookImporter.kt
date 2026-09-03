package dev.pranav.speed400garage.ai

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import dev.pranav.speed400garage.data.db.dao.HandbookDao
import dev.pranav.speed400garage.data.db.entity.HandbookChunkEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

sealed interface ImportResult {
    data class Imported(val pages: Int, val chunks: Int) : ImportResult
    data class Failed(val message: String) : ImportResult
}

/**
 * Imports the owner's handbook PDF into a page-cited local corpus (§10.2).
 *
 * The plan rejected the shortcut of pasting the whole manual into the model's context,
 * and it was right to: 229 pages every question is slow, expensive, and — the part
 * that actually matters — produces answers with no page to check them against.
 *
 * Extraction is per page so the page number travels with the text. Everything after
 * that is local: the corpus lives in SQLite and is searched offline, so handbook
 * answers keep working with no signal and no API key.
 */
@Singleton
class HandbookImporter @Inject constructor(
    private val context: Context,
    private val dao: HandbookDao,
) {

    suspend fun import(source: Uri, onProgress: (Int, Int) -> Unit = { _, _ -> }): ImportResult =
        withContext(Dispatchers.IO) {
            PDFBoxResourceLoader.init(context)
            try {
                context.contentResolver.openInputStream(source).use { stream ->
                    if (stream == null) return@withContext ImportResult.Failed("Couldn't open that file.")

                    PDDocument.load(stream).use { document ->
                        val pages = document.numberOfPages
                        if (pages < MIN_PLAUSIBLE_PAGES) {
                            // Phase 0 hit exactly this: a 1-page, 72 KB "handbook" that
                            // was really an auth wall. Better to refuse than to index it.
                            return@withContext ImportResult.Failed(
                                "That PDF has only $pages page${if (pages == 1) "" else "s"}. The real " +
                                    "handbook is 200+ pages — this looks like a preview or a login page."
                            )
                        }

                        val stripper = PDFTextStripper()
                        val chunks = mutableListOf<HandbookChunkEntity>()
                        var textPages = 0

                        for (page in 1..pages) {
                            coroutineContext.ensureActive()
                            stripper.startPage = page
                            stripper.endPage = page
                            val text = runCatching { stripper.getText(document) }.getOrDefault("")
                            if (text.isNotBlank()) textPages++
                            chunks += HandbookChunker.chunksFor(page, text)
                            onProgress(page, pages)
                        }

                        if (textPages < pages / 2) {
                            return@withContext ImportResult.Failed(
                                "Only $textPages of $pages pages had readable text. That PDF is probably " +
                                    "scanned images rather than a text handbook, and searching it would " +
                                    "return nothing."
                            )
                        }

                        dao.clear()
                        chunks.chunked(BATCH).forEach { dao.insertAll(it) }
                        ImportResult.Imported(pages, chunks.size)
                    }
                }
            } catch (e: Exception) {
                ImportResult.Failed(e.message ?: "Couldn't read that PDF.")
            }
        }

    private companion object {
        const val BATCH = 200
        const val MIN_PLAUSIBLE_PAGES = 50
    }
}
