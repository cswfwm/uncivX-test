package com.unciv.logic.llm

import com.unciv.UncivGame
import com.unciv.utils.Log

/**
 * Persistent transcript for the AI console.
 *
 * The conversation is stored as plain text under the game's data folder so reopening the console
 * continues where it left off. Only the last [MAX_LINES] entries are kept to bound file size.
 */
object ChatHistory {

    private const val FILE = "ai-guide-history.txt"
    private const val MAX_LINES = 200
    private const val SEPARATOR = "\u0001"

    data class Entry(val who: String, val text: String)

    private val entries = ArrayList<Entry>()

    private fun file() = UncivGame.Current.files.getLocalFile(FILE)

    fun load() {
        entries.clear()
        try {
            val handle = file()
            if (!handle.exists()) return
            handle.readString("UTF-8").lineSequence().forEach { line ->
                val parts = line.split(SEPARATOR, limit = 2)
                if (parts.size == 2) entries.add(Entry(parts[0], parts[1]))
            }
        } catch (ex: Exception) {
            Log.debug("Could not read AI history: ${ex.message}")
        }
    }

    fun all(): List<Entry> = entries.toList()

    fun add(who: String, text: String) {
        entries.add(Entry(who, text.replace('\n', ' ')))
        while (entries.size > MAX_LINES) entries.removeAt(0)
        save()
    }

    fun clear() {
        entries.clear()
        save()
    }

    private fun save() {
        try {
            file().writeString(
                entries.joinToString("\n") { "${it.who}$SEPARATOR${it.text}" }, false, "UTF-8")
        } catch (ex: Exception) {
            Log.debug("Could not save AI history: ${ex.message}")
        }
    }
}
