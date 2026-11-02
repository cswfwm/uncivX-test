package com.unciv.ui.components.tilegroups.layers

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.utils.Align
import com.unciv.UncivGame
import com.unciv.logic.llm.LlmSettingsHolder
import com.unciv.view.CivView
import com.unciv.view.ForeignMapUnitView
import com.unciv.ui.components.NonTransformGroup
import com.unciv.ui.components.fonts.Fonts
import com.unciv.ui.components.tilegroups.TileGroup
import com.unciv.ui.images.ImageGetter

class UnitSpriteSlot {
    val spriteGroup = NonTransformGroup()
    var currentImageLocation = ""
}

class TileLayerUnitSprite(tileGroup: TileGroup, size: Float) : TileLayer(tileGroup, size) {

    // Slots are only filled if units exist, and images for those units exist
    private var civilianSlot: UnitSpriteSlot? = null
    private var militarySlot: UnitSpriteSlot? = null

    /** Radar marker blip, drawn on Enemy units that the viewer cannot actually see. */
    private var radarBlip: Label? = null
    /** Unit call-sign label, shown when the player enables unit names. */
    private var unitNameLabel: Label? = null


    fun getSpriteSlot(unitView: ForeignMapUnitView) = if (unitView.isCivilian()) civilianSlot else militarySlot

    private fun updateSlot(currentSlot: UnitSpriteSlot?, unitView: ForeignMapUnitView?, isShown: Boolean): UnitSpriteSlot? {

        var location = ""
        var nationName = ""

        if (unitView != null && isShown && UncivGame.Current.settings.showPixelUnits) {
            location = strings.getUnitImageLocation(unitView)
            nationName = "${unitView.civName}-"
        }

        if (currentSlot == null && location == "") return null // No-op - had none, has none
        if (currentSlot?.currentImageLocation == "$nationName$location") return currentSlot // No-op - had, has

        if (location == "" || !ImageGetter.imageExists(location)){
            currentSlot?.spriteGroup?.let { removeOwnedActor(it) }
            return null
        }

        val slot = currentSlot ?: UnitSpriteSlot().apply {
            // Position the container at the tile origin so children use tile-local coords.
            // This keeps spriteGroup visible to viewport culling and preserves the
            // tile-local child positions that WorldMapHolder's animation code relies on.
            spriteGroup.setPosition(tileX, tileY)
            spriteGroup.setSize(size, size)
            addOwnedActor(spriteGroup)
        }
        slot.currentImageLocation = "$nationName$location"
        slot.spriteGroup.clear()

        val civView = unitView!!.civ()
        val pixelUnitImages = ImageGetter.getLayeredImageColored(
            location,
            null,
            civView.getInnerColor(),
            civView.getOuterColor()
        )
        for (pixelUnitImage in pixelUnitImages) {
            slot.spriteGroup.addActor(pixelUnitImage)
            // tileLocal=true: spriteGroup is already at (tileX,tileY) so children only need
            // the hex-local offset (hexagonImagePosition), not the full absolute position.
            pixelUnitImage.setHexagonSize(tileLocal = true)
        }
        return slot
    }

    fun dim() {
        forEachOwnedActor { it.color.a = 0.5f }
    }

    override fun doUpdate(viewingCiv: CivView?) {

        val isPixelUnitsEnabled = UncivGame.Current.settings.showPixelUnits
        val isViewable = isViewable(viewingCiv)

        val isCivilianSlotShown = isPixelUnitsEnabled && isViewable
        val isMilitarySlotShown = isPixelUnitsEnabled && isViewable

        civilianSlot = updateSlot(civilianSlot, tileGroup.tileView.civilianUnit, isShown = isCivilianSlotShown)
        militarySlot = updateSlot(militarySlot, tileGroup.tileView.militaryUnit, isShown = isMilitarySlotShown)

        updateRadarAndName(viewingCiv, isViewable)
    }

    /**
     * Radar: shows a blip on every enemy unit, including ones standing outside the viewer's sight
     * range. Unit names are drawn on top when enabled. Both are pure overlays - they never change
     * what the game state reports, only what the player sees.
     */
    private fun updateRadarAndName(viewingCiv: CivView?, isViewable: Boolean) {
        val unitView = tileGroup.tileView.militaryUnit ?: tileGroup.tileView.civilianUnit
        val viewerCiv = viewingCiv?.getCiv()

        val showBlip = LlmSettingsHolder.isEnemyRadarEnabled() && unitView != null &&
            viewerCiv != null && unitView.civ().getCiv() != viewerCiv && !unitView.civ().isBarbarian()

        if (showBlip) {
            val label = radarBlip ?: Label("\u25C6", Label.LabelStyle(Fonts.font, Color.RED)).apply {
                setAlignment(Align.center)
                addOwnedActor(this)
            }
            label.setPosition(tileX + tileGroup.hexagonImagePosition.first,
                tileY + tileGroup.hexagonImagePosition.second)
            label.setSize(hexagonWidth(), hexagonWidth())
            label.isVisible = true
            radarBlip = label
        } else {
            radarBlip?.let { removeOwnedActor(it); it.remove() }
            radarBlip = null
        }

        val showName = LlmSettingsHolder.isUnitNamesEnabled() && isViewable && unitView != null
        if (showName) {
            val label = unitNameLabel ?: Label("", Label.LabelStyle(Fonts.font, Color.WHITE)).apply {
                addOwnedActor(this)
            }
            label.setText(unitView.name)
            label.setPosition(tileX + tileGroup.hexagonImagePosition.first,
                tileY + tileGroup.hexagonImagePosition.second + tileGroup.hexagonImageWidth * 0.5f)
            label.setSize(hexagonWidth(), tileGroup.hexagonImageWidth * 0.25f)
            label.isVisible = true
            unitNameLabel = label
        } else {
            unitNameLabel?.let { removeOwnedActor(it); it.remove() }
            unitNameLabel = null
        }
    }

    private fun hexagonWidth() = tileGroup.hexagonImageWidth

    override fun determineVisibility() {
        isVisible = civilianSlot != null || militarySlot != null
    }

    fun reset() {
        civilianSlot?.spriteGroup?.let { removeOwnedActor(it) }
        militarySlot?.spriteGroup?.let { removeOwnedActor(it) }
        civilianSlot = null
        militarySlot = null
    }
}
