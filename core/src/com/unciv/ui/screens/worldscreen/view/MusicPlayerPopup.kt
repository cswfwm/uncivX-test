package com.unciv.ui.screens.worldscreen.view

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.logic.music.MusicPlayer
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.widgets.UncivTextField
import com.unciv.ui.popups.Popup
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.worldscreen.WorldScreen

/**
 * Music player: playlist from the local `music/` folder, play/pause/stop, volume and import.
 *
 * Format support comes from the platform decoder, so flac, m4a, opus and friends work alongside
 * mp3/ogg/wav.
 */
class MusicPlayerPopup(private val worldScreen: WorldScreen) : Popup(worldScreen, Scrollability.None) {

    private val nowPlaying = Label("", BaseScreen.skin)
    private val playlistTable = Table(BaseScreen.skin)
    private val importField = UncivTextField("music 文件夹内的文件名，或音频文件绝对路径")
    private val statusLabel = "".toLabel(fontSize = 16)

    init {
        MusicPlayer.onTrackChanged = { name ->
            nowPlaying.setText(if (name != null) "正在播放：$name" else "已停止")
        }

        add("音乐播放器".toLabel(fontSize = 26)).left().pad(6f).row()
        add(nowPlaying).left().pad(4f).row()

        playlistTable.defaults().growX().pad(2f).left()
        val scroll = ScrollPane(playlistTable, BaseScreen.skin)
        scroll.setFadeScrollBars(false)
        add(scroll).size(0.62f * stageToShowOn.width, 0.4f * stageToShowOn.height)
            .expand().fill().row()

        val controls = Table()
        "播放/暂停".toTextButton().apply {
            onClick {
                if (MusicPlayer.currentName() == null) {
                    MusicPlayer.playlist().firstOrNull()?.let { MusicPlayer.play(it) }
                } else {
                    MusicPlayer.togglePause()
                    refresh()
                }
            }
        }.also { controls.add(it).padRight(6f) }

        "停止".toTextButton().apply {
            onClick {
                MusicPlayer.stop()
                refresh()
            }
        }.also { controls.add(it).padRight(6f) }

        "音量 +".toTextButton().apply {
            onClick {
                MusicPlayer.setVolume(MusicPlayer.volume + 0.1f)
                refresh()
            }
        }.also { controls.add(it).padRight(6f) }

        "音量 -".toTextButton().apply {
            onClick {
                MusicPlayer.setVolume(MusicPlayer.volume - 0.1f)
                refresh()
            }
        }.also { controls.add(it) }
        add(controls).left().row()

        val importRow = Table()
        importRow.add(importField).growX().padRight(6f)
        "导入".toTextButton().apply {
            onClick { importMusic() }
        }.also { importRow.add(it) }
        add(importRow).growX().row()

        "刷新歌单".toTextButton().apply {
            onClick { refresh() }
        }.also { add(it).left().row() }

        add(statusLabel).left().row()
        addCloseButton("关闭")

        nowPlaying.setText(
            if (MusicPlayer.currentName() != null) "正在播放：${MusicPlayer.currentName()}" else "已停止")
        refresh()
    }

    private fun importMusic() {
        val source = importField.text.trim()
        if (source.isEmpty()) return
        val file = try {
            if (source.startsWith("/")) Gdx.files.absolute(source) else MusicPlayer.musicFolder().child(source)
        } catch (ex: Exception) {
            null
        }
        if (file == null || !file.exists()) {
            statusLabel.setText("找不到该文件")
            return
        }
        val imported = MusicPlayer.import(file)
        statusLabel.setText(if (imported != null) "已导入：${imported.name()}" else "格式不支持")
        importField.setText("")
        refresh()
    }

    private fun refresh() {
        playlistTable.clear()
        val tracks = MusicPlayer.playlist()
        if (tracks.isEmpty()) {
            playlistTable.add("music 文件夹里还没有音乐".toLabel(fontSize = 18)).row()
        }
        for (track in tracks) {
            val name = track.name()
            val row = Table()
            name.toLabel(fontSize = 18).also { row.add(it).expandX().left() }
            "播放".toTextButton().apply {
                onClick {
                    MusicPlayer.play(track)
                    refresh()
                }
            }.also { row.add(it) }
            playlistTable.add(row).growX().row()
        }
        statusLabel.setText("音量 ${(MusicPlayer.volume * 100).toInt()}%")
    }
}
