package com.unciv.logic.music

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.audio.Music
import com.badlogic.gdx.files.FileHandle
import com.unciv.UncivGame
import com.unciv.utils.Log

/**
 * Simple playlist player for user-supplied audio files.
 *
 * Files are read from the game's local `music/` folder, the same place the game's own ambient
 * tracks live, so an imported file is just a file copied there. Playback is independent of the
 * game's ambience: starting a track pauses the ambient music and stopping resumes it.
 */
object MusicPlayer {

    private const val FOLDER = "music"
    /** Platform decoders (Android MediaPlayer, desktop) handle all of these. */
    val extensions = listOf(
        "mp3", "ogg", "oga", "opus", "wav", "flac", "aac", "m4a", "mp4",
        "wma", "amr", "mid", "midi", "aiff", "aif", "mka", "webm"
    )

    private var current: Music? = null
    private var currentFile: FileHandle? = null

    /** Set by the UI so it can refresh the now-playing label. */
    var onTrackChanged: ((String?) -> Unit)? = null

    var isPlaying: Boolean = false
        private set
    var volume: Float = 0.8f
        private set

    fun musicFolder(): FileHandle = UncivGame.Current.files.getLocalFile(FOLDER)

    /** All playable files in the music folder, sorted by name. */
    fun playlist(): List<FileHandle> {
        val folder = musicFolder()
        if (!folder.exists()) folder.mkdirs()
        return folder.list()
            .filter { !it.isDirectory && extensions.contains(it.extension().lowercase()) }
            .sortedBy { it.name() }
    }

    fun play(file: FileHandle) {
        stop()
        // The platform decoder covers far more formats than libGDX's own loader.
        if (UncivGame.Current.playAudioFile(file.file().absolutePath, volume) {
                stop()
            }) {
            currentFile = file
            isPlaying = true
            onTrackChanged?.invoke(file.name())
            return
        }
        try {
            val music = Gdx.audio.newMusic(file)
            music.volume = volume
            music.setOnCompletionListener { stop() }
            music.play()
            current = music
            currentFile = file
            isPlaying = true
            onTrackChanged?.invoke(file.name())
        } catch (ex: Exception) {
            Log.error("Could not play ${file.name()}: ${ex.message}")
            onTrackChanged?.invoke(null)
        }
    }

    fun togglePause() {
        if (currentFile != null && current == null) {
            isPlaying = !isPlaying
            UncivGame.Current.setAudioFilePaused(!isPlaying)
            return
        }
        val music = current ?: return
        if (music.isPlaying) {
            music.pause()
            isPlaying = false
        } else {
            music.play()
            isPlaying = true
        }
    }

    fun stop() {
        UncivGame.Current.stopAudioFile()
        current?.let {
            it.stop()
            it.dispose()
        }
        current = null
        currentFile = null
        isPlaying = false
        onTrackChanged?.invoke(null)
    }

    fun setVolume(value: Float) {
        volume = value.coerceIn(0f, 1f)
        current?.volume = volume
        UncivGame.Current.setAudioFileVolume(volume)
    }

    fun currentName(): String? = currentFile?.name()

    /** Copies an audio file chosen by the user into the music folder and returns it. */
    fun import(source: FileHandle): FileHandle? {
        if (!extensions.contains(source.extension().lowercase())) return null
        return try {
            val target = musicFolder().child(source.name())
            if (target.exists() && target.path() != source.path()) target.delete()
            source.copyTo(target)
            target
        } catch (ex: Exception) {
            Log.error("Could not import ${source.name()}: ${ex.message}")
            null
        }
    }
}
