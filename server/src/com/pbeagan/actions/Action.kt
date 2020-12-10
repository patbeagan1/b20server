package com.pbeagan.actions

import com.pbeagan.SampleData.mobs
import com.pbeagan.actions.actiondelegates.CombatDelegateProvider
import com.pbeagan.actions.actiondelegates.ItemDelegateProvider
import com.pbeagan.actions.actiondelegates.LookDelegateProvider
import com.pbeagan.actions.actiondelegates.MovementDelegateProvider
import com.pbeagan.data.AttackType.MAGIC
import com.pbeagan.data.AttackType.MELEE
import com.pbeagan.data.AttackType.RANGED
import com.pbeagan.data.AttackType.THROWN
import com.pbeagan.data.Direction
import com.pbeagan.data.Effect.Type.SPELL
import com.pbeagan.data.EffectImpl
import com.pbeagan.data.ItemData
import com.pbeagan.data.ItemFlags
import com.pbeagan.data.ItemFlags.TAKEABLE
import com.pbeagan.data.ItemFlags.UNDROPPABLE
import com.pbeagan.data.Mob
import com.pbeagan.data.currentRoom
import com.pbeagan.data.currentRoomOtherMobs
import com.pbeagan.data.getFirstVisibleMob
import com.pbeagan.data.target
import com.pbeagan.earlyMatches
import com.pbeagan.writer.Writer
import rooms

abstract class Action {
    abstract operator fun invoke(self: Mob)
    lateinit var writer: Writer
    val combatDelegate = CombatDelegateProvider()
    val itemDelegateProvider = ItemDelegateProvider()
    val movementDelegateProvider = MovementDelegateProvider()
    val lookDelegateProvider = LookDelegateProvider()

    companion object {
        fun attackOrRetry(mob: Mob, target: String): Action {
            fun onTargetFound(target: Mob): Action = when (mob.preferredAttack) {
                MELEE -> AttackMelee(target)
                RANGED -> AttackRanged(target)
                THROWN -> AttackThrow(target)
                MAGIC -> TODO()
            }
            return when {
                target.isEmpty() -> mob.getFirstVisibleMob()?.let { onTargetFound(it) }
                else -> mob.target(target)?.let { onTargetFound(it) }
            } ?: Retry("Looks like that mob isn't here...")
        }
    }
}

interface FreeAction

class AttackMelee(private val target: Mob) : Action() {
    override operator fun invoke(self: Mob) = combatDelegate.prepare(writer).attackMelee(self, target)
}

class Take(private val item: ItemData) : Action() {
    override fun invoke(self: Mob) = itemDelegateProvider.prepare(writer).take(self, item)

    companion object {
        fun getOrRetry(mob: Mob, itemName: String): Action {
            if (itemName == "all") return TakeAll()
            return mob.currentRoom()?.items
                ?.firstOrNull { itemData ->
                    itemData.itemFlags.contains(TAKEABLE) && itemData.names.any { name ->
                        itemName.let { name.earlyMatches(it) }
                    }
                }
                ?.let { Take(it) }
                ?: return Retry("That item isn't here...")
        }
    }
}

class Drop(private val item: ItemData) : Action() {
    override fun invoke(self: Mob) = itemDelegateProvider.prepare(writer).drop(self, item)

    companion object {
        fun getOrRetry(mob: Mob, itemName: String): Action = mob.items
            .firstOrNull { itemData ->
                itemData.itemFlags.contains(UNDROPPABLE).not() && itemData.names.any { name ->
                    itemName.let { name.earlyMatches(it) }
                }
            }
            ?.let { Drop(it) }
            ?: Retry("You're not holding that item...")
    }
}

class TakeAll : Action() {
    override fun invoke(self: Mob) = itemDelegateProvider.prepare(writer).takeAll(self)
}

class AttackRanged(private val target: Mob) : Action() {
    override fun invoke(self: Mob) = combatDelegate.prepare(writer).attackRanged(self, target)
}

class AttackThrow(private val target: Mob) : Action() {
    override fun invoke(self: Mob) = combatDelegate.prepare(writer).attackThrow(self, target)
}

class Look : Action(), FreeAction {
    override fun invoke(self: Mob) = lookDelegateProvider.prepare(writer).look(self, mobs)
}

class Doors : Action(), FreeAction {
    override fun invoke(self: Mob) = movementDelegateProvider.prepare(writer).doors(self)
}

class Move(private val direction: Direction) : Action() {
    override fun invoke(self: Mob) = movementDelegateProvider.prepare(writer).move(self, direction)
}

object Pass : Action() {
    override operator fun invoke(self: Mob) = Unit
}

class Repeat(private val action: Action) : Action() {
    override fun invoke(self: Mob) {
        if (action !is Repeat) {
            writer.sayTo(self).info("Repeating the last action: ${action::class.java.simpleName}")
        }
        action.invoke(self)
    }

    val isRepeatFreeAction: Boolean
        get() = if (action is Repeat) action.isRepeatFreeAction else action is FreeAction
}

class Debug(private val target: String) : Action(), FreeAction {

    override fun invoke(self: Mob) {
        searchMobs(self) ?: searchMobItems(self) ?: searchRoomItems(self)
    }

    private fun searchRoomItems(self: Mob): Unit? = rooms.map { it.value.items }.flatten().firstOrNull {
        it.nameMatches(target)
    }?.let {
        writer.sayTo(self).info(it.toString())
    }

    private fun searchMobItems(self: Mob): Unit? = mobs.map { it.items }.flatten().firstOrNull {
        it.nameMatches(target)
    }?.let {
        writer.sayTo(self).info(it.toString())
    }

    private fun searchMobs(self: Mob): Unit? = mobs.firstOrNull {
        it.name.toLowerCase() == target.toLowerCase()
    }?.let { currentMob ->
        writer.sayTo(self).run {
            info(currentMob.toString())
            info(currentMob.effects.toString())
            info("")
            info("baseAtkMelee: ${currentMob.baseAtkMelee}")
            info("baseAtkRanged: ${currentMob.baseAtkRanged}")
            info("baseAtkThrow: ${currentMob.baseAtkThrow}")
            info("awareness: ${currentMob.awareness}")
            info("spirit: ${currentMob.spirit}")
            info("speed: ${currentMob.speed}")
            info("presence: ${currentMob.presence}")
            info("cunning: ${currentMob.cunning}")
            info("persuasion: ${currentMob.persuasion}")
            info("tenacity: ${currentMob.tenacity}")
            info("fortitude: ${currentMob.fortitude}")
            info("strength: ${currentMob.strength}")
            info("agility: ${currentMob.agility}")
            info("precision: ${currentMob.precision}")
            info("endurance: ${currentMob.endurance}")
            info("durability: ${currentMob.durability}")
            info("totalHearts: ${currentMob.totalHearts}")
            info("description: ${currentMob.description}")
            info("preferredAttack: ${currentMob.preferredAttack}")
            info("idForIO: ${currentMob.idForIO}")
            info("hearts: ${currentMob.hearts}")
            info("")
        }
    }
}

object Inactive : Action() {
    override operator fun invoke(self: Mob) = writer.sayToRoomOf(self).info("${self.name} is inactive")
}

class Retry(private val errorMsg: String) : Action(), FreeAction {
    override operator fun invoke(self: Mob) = writer.sayTo(self).error(errorMsg)
}

class Settings(private val action: (Mob) -> Unit) : Action(), FreeAction {
    override operator fun invoke(self: Mob) {
        action(self)
        writer.sayTo(self).info("Settings were changed.")
    }

    companion object {
        fun getAttack(settingValue: String): Action = when (settingValue) {
            "melee" -> Settings { it.preferredAttack = MELEE }
            "range", "ranged" -> Settings { it.preferredAttack = RANGED }
            "throw", "thrown" -> Settings { it.preferredAttack = THROWN }
            else -> Retry("Not a valid attack")
        }
    }
}

class Examine(private val item: String) : Action(), FreeAction {
    override fun invoke(self: Mob) = itemDelegateProvider.prepare(writer).examine(self, item)
}

class Consume(private val item: ItemData) : Action() {
    override fun invoke(self: Mob) {
        item.flagHandlers[ItemFlags.CONSUMABLE]?.invoke(self)?.let {
            self.items.remove(item)
        }
    }

    companion object {
        fun getOrRetry(self: Mob, itemName: String): Action {
            val itemData = self.items.firstOrNull {
                it.nameMatches(itemName)
            } ?: return Retry("You're not holding that item")
            return Consume(itemData)
        }
    }
}

class Give(private val target: Mob, private val item: ItemData) : Action() {
    override fun invoke(self: Mob) {
        self.items.remove(item)
        target.items.add(item)
        writer.sayTo(self, target).info("${self.name} gave ${target.name} a ${item.names[0]}")
    }

    companion object {
        fun getOrRetry(self: Mob, target: String, itemName: String): Action {
            val itemData = self
                .items.firstOrNull { it.nameMatches(itemName) }
                ?: return Retry("You aren't holding that item")

            if (itemData.itemFlags.contains(UNDROPPABLE)) return Retry("You can't drop that item")
            val mob = self
                .currentRoomOtherMobs(mobs)
                .also { println(it) }
                .firstOrNull { target.earlyMatches(it.name) }
                ?: return Retry("$target isn't here")
            return Give(mob, itemData)
        }
    }
}

class Inventory : Action(), FreeAction {
    override fun invoke(self: Mob) = itemDelegateProvider.prepare(writer).inventory(self)
}

class Curse(private val target: Mob) : Action() {
    override fun invoke(self: Mob) {
        val effect = EffectImpl(
            roundsLeft = 1,
            totalHearts = -10,
            descriptionActivation = "${target.name} has been cursed by ${self.name}!",
            descriptionDeactivation = "The curse on ${target.name} has worn off.",
            name = "Curse",
            type = SPELL
        )
        target.addEffect(writer, effect)
    }

    companion object {
        fun getOrRetry(mob: Mob, targetName: String): Action {
            val target = mob.target(targetName) ?: return Retry("That mob isn't here")
            return Curse(target)
        }
    }
}

