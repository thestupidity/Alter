package org.alter.game.action

import dev.openrune.cache.CacheManager.getItem
import net.rsprot.protocol.game.outgoing.misc.player.SetMapFlag
import org.alter.game.model.MovementQueue
import org.alter.game.model.attr.GROUNDITEM_PICKUP_TRANSACTION
import org.alter.game.model.attr.INTERACTING_GROUNDITEM_ATTR
import org.alter.game.model.attr.INTERACTING_ITEM
import org.alter.game.model.attr.INTERACTING_OPT_ATTR
import org.alter.game.model.entity.Entity
import org.alter.game.model.entity.GroundItem
import org.alter.game.model.entity.Player
import org.alter.game.model.item.Item
import org.alter.game.model.queue.QueueTask
import org.alter.game.model.queue.TaskPriority
import org.alter.game.plugin.Plugin
import org.alter.game.service.log.LoggerService
import java.lang.ref.WeakReference

/**
 * This class is responsible for moving towards a [GroundItem].
 *
 * @author Tom <rspsmods@gmail.com>
 */
object GroundItemPathAction {
    /**
     * The option used to specify that a walk action should execute item on
     * ground item plugins when destination is reached.
     */
    internal const val ITEM_ON_GROUND_ITEM_OPTION = -1

    val walkPlugin: Plugin.() -> Unit = {
        val p = ctx as Player
        val item = p.attr[INTERACTING_GROUNDITEM_ATTR]!!.get()!!
        val opt = p.attr[INTERACTING_OPT_ATTR]!!

        if (p.tile.sameAs(item.tile)) {
            handleAction(p, item, opt)
        } else {
            p.walkTo(item.tile, MovementQueue.StepType.NORMAL)
            p.queue(TaskPriority.STANDARD) {
                terminateAction = {
                    p.stopMovement()
                    p.write(SetMapFlag(255, 255))
                }
                awaitArrival(item, opt)
            }
        }
    }

    private suspend fun QueueTask.awaitArrival(
        item: GroundItem,
        opt: Int,
    ) {
        val p = ctx as Player
        val destination = p.movementQueue.peekLast()
        if (destination == null) {
            p.writeMessage(Entity.YOU_CANT_REACH_THAT)
            return
        }
        while (true) {
            if (!p.tile.sameAs(destination)) {
                wait(1)
                continue
            }
            if (p.tile.sameAs(item.tile)) {
                handleAction(p, item, opt)
            } else {
                p.writeMessage(Entity.YOU_CANT_REACH_THAT)
            }
            break
        }
    }

    private fun handleAction(
        p: Player,
        groundItem: GroundItem,
        opt: Int,
    ) {
        if (!p.world.isSpawned(groundItem)) {
            return
        }
        if (opt == 3) {
            if (!p.world.plugins.canPickupGroundItem(p, groundItem.item)) {
                return
            }

            /**
             * added partial pickup requires tracking player inventory amount before and after
             * as the leftover amount must be derived after transaction in order to properly
             * place the remainder [Item.amount] of [GroundItem] back as leftover
             */
            val before = p.inventory.getItemCount(groundItem.item)
            val add = p.inventory.add(item = groundItem.item, amount = groundItem.amount, assureFullInsertion = false)
            val after = p.inventory.getItemCount(groundItem.item)

            if (add.getLeftOver() != 0) {
                p.writeMessage("You don't have enough inventory space to hold any more.")
            } else {
                p.playSound(2582)
            }

            add.items.firstOrNull()?.let { item ->
                item.item.attr.putAll(groundItem.attr)
            }

            val remainder = groundItem.amount - (after - before)
            p.world.remove(groundItem)

            if (remainder != 0) {
                if (groundItem.ownerUID == null) {
                    p.world.spawn(GroundItem(groundItem.item, remainder, groundItem.tile))
                } else {
                    p.world.spawn(
                        GroundItem(
                            groundItem.item,
                            remainder,
                            groundItem.tile,
                            p.world.getPlayerForUid(groundItem.ownerUID!!),
                        ),
                    )
                }
            }

            p.attr[GROUNDITEM_PICKUP_TRANSACTION] = WeakReference(add)
            p.world.plugins.executeGlobalGroundItemPickUp(p)
            p.world.getService(LoggerService::class.java, searchSubclasses = true)?.logItemPickUp(p, Item(groundItem.item, add.completed))
        } else if (opt == ITEM_ON_GROUND_ITEM_OPTION) {
            val item = p.attr[INTERACTING_ITEM]?.get() ?: return
            val handled = p.world.plugins.executeItemOnGroundItem(p, item.id, groundItem.item)

            if (!handled && p.world.devContext.debugItemActions) {
                p.writeMessage("Unhandled item on ground item action: [item=${item.id}, ground=${groundItem.item}]")
            }
        } else {
            val handled = p.world.plugins.executeGroundItem(p, groundItem.item, opt)
            if (!handled && p.world.devContext.debugItemActions) {
                val definition = getItem(groundItem.item)
                p.writeMessage("Unhandled ground item action: [item=${groundItem.item}, option=[$opt, ${definition.options[opt - 1]}]]")
            }
        }
    }
}
