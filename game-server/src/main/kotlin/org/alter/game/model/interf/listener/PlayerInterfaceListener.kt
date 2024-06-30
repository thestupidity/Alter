package org.alter.game.model.interf.listener

import org.alter.game.model.entity.Player
import org.alter.game.plugin.PluginRepository

/**
 * @author Tom <rspsmods@gmail.com>
 */
class PlayerInterfaceListener(val player: Player, val plugins: PluginRepository) : InterfaceListener {
    override fun onInterfaceOpen(interfaceId: Int) {
        plugins.executeInterfaceOpen(player, interfaceId)
    }

    override fun onInterfaceClose(interfaceId: Int) {
        player.world.plugins.executeInterfaceClose(player, interfaceId)
    }
}
