package com.unciv.utils

import java.util.Locale

interface PlatformSpecific {

    /** Notifies player that his multiplayer turn started */
    fun notifyTurnStarted() {}

    /** Install system audio hooks */
    fun installAudioHooks() {}

    /**
     * Play a local audio file with the platform decoder, which understands far more formats
     * (flac, aac, m4a, opus, wma, ...) than libGDX's mp3/ogg/wav.
     * @return `true` when the platform took over playback
     */
    fun playAudioFile(path: String, volume: Float, onComplete: () -> Unit): Boolean = false

    /** Stop playback started by [playAudioFile], if any. */
    fun stopAudioFile() {}

    /** Pause/resume playback started by [playAudioFile]. */
    fun setAudioFilePaused(paused: Boolean) {}

    /** Change volume of [playAudioFile] playback, 0..1. */
    fun setAudioFileVolume(volume: Float) {}

    /** If not null, this is the path to the directory in which to store the local files - mods, saves, maps, etc */
    var customDataDirectory: String?

    /** If the OS localizes all error messages, this should provide a lookup */
    fun getSystemErrorMessage(errorCode: Int): String? = null

    fun getGcCount(): Int

    /** Get system locale, on Android 13+ app-specific locale */
    fun getDefaultLocale(): Locale = Locale.getDefault()

    /**
     * If not null, the absolute path of a writable folder for in-game downloaded installer
     * packages (the APK on Android). Platforms returning `null` (desktop) download via the browser.
     */
    fun getInstallerDownloadFolder(): String? = null

    /** Open the system installer for a downloaded APK - no-op on platforms without one */
    fun installDownloadedApk(apkFilePath: String) {}

    /**
     * Copy a downloaded installer package to a user-visible location (the public Downloads folder
     * on Android 10+), so players can install it from their file manager / download list even
     * outside the game - e.g. after they rejected the in-game install dialog.
     * @return `true` when the copy succeeded
     */
    fun saveInstallerToPublicFolder(apkFilePath: String): Boolean = false
}
