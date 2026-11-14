package com.pixelbead.client

import com.pixelbead.config.ClientConfig
import com.pixelbead.config.HudPosition
import com.pixelbead.config.HudSwatchSize

/** Client-local favorites / recents / default hotbar / HUD layout, persisted via client config. */
object ClientPrefs {
    fun favorites(): List<String> = ClientConfig.favorites.get()
    fun recents(): List<String> = ClientConfig.recents.get()
    fun defaultHotbar(): List<String> = ClientConfig.defaultHotbar.get()
    fun hudPosition(): HudPosition = ClientConfig.hudPosition.get()
    fun hudEnabled(): Boolean = ClientConfig.hudEnabled.get()
    fun swatchSize(): HudSwatchSize = ClientConfig.hudSwatchSize.get()

    fun setHudPosition(position: HudPosition) {
        ClientConfig.hudPosition.set(position)
        ClientConfig.SPEC.save()
    }

    fun setHudEnabled(enabled: Boolean) {
        ClientConfig.hudEnabled.set(enabled)
        ClientConfig.SPEC.save()
    }

    fun setSwatchSize(size: HudSwatchSize) {
        ClientConfig.hudSwatchSize.set(size)
        ClientConfig.SPEC.save()
    }

    fun toggleFavorite(id: String) {
        val list = favorites().toMutableList()
        if (!list.remove(id)) list.add(id)
        ClientConfig.favorites.set(list)
        ClientConfig.SPEC.save()
    }

    fun addRecent(id: String) {
        val list = recents().toMutableList()
        list.remove(id)
        list.add(0, id)
        while (list.size > 16) list.removeAt(list.size - 1)
        ClientConfig.recents.set(list)
        ClientConfig.SPEC.save()
    }
}
