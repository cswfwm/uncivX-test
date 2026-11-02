package com.unciv.logic.llm

import com.unciv.logic.civilization.Civilization

/**
 * Pending "do you want to declare war on X?" questions raised by the AI turn.
 *
 * The AI runs on a background thread while the player may be looking at the map, so it cannot
 * block on a popup. Instead a declaration is queued here and the WorldScreen asks the player on
 * the GL thread; only an accepted answer calls into the diplomacy manager.
 */
object WarDeclarations {
    private val pending = ArrayList<Civilization>()

    fun request(target: Civilization) {
        synchronized(pending) {
            if (pending.none { it == target }) pending.add(target)
        }
    }

    fun poll(): Civilization? = synchronized(pending) {
        if (pending.isEmpty()) null else pending.removeAt(0)
    }

    fun clear() = synchronized(pending) { pending.clear() }
}
