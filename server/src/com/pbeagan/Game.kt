package com.pbeagan

import com.pbeagan.actions.Action
import com.pbeagan.actions.AttackMelee
import com.pbeagan.actions.Consume
import com.pbeagan.actions.Debug
import com.pbeagan.actions.Doors
import com.pbeagan.actions.Drop
import com.pbeagan.actions.Examine
import com.pbeagan.actions.FreeAction
import com.pbeagan.actions.Give
import com.pbeagan.actions.Inactive
import com.pbeagan.actions.Inventory
import com.pbeagan.actions.Look
import com.pbeagan.actions.Move
import com.pbeagan.actions.Pass
import com.pbeagan.actions.Repeat
import com.pbeagan.actions.Retry
import com.pbeagan.actions.Settings
import com.pbeagan.actions.Take
import com.pbeagan.mob.Mob
import com.pbeagan.mob.getFirstVisibleMob
import com.pbeagan.mob.getRandomVisibleItem
import com.pbeagan.writer.Reader
import com.pbeagan.writer.Writer
import mobs

class Game {
    fun gameLoop(writer: Writer, reader: Reader) {
        mobs.sortedByDescending { roll20() + it.attr.awareness }.forEach { mob ->

            if (mob.behavior == MobBehavior.PLAYER && reader.isActive(mob)) {
                if (mob.action == Inactive) {
                    writer.sayToAll().join("${mob.name} has joined the game!")
                }
                writer.sayToAll().turnStart(mob.name)
            }

            var action: Action? = null
            while (checkIfTurnContinues(action)) {

                action = mob.getAction(reader)
                if (action != null) {
                    mob.action = action
                }
                if (checkIfTurnContinues(action)) {
                    action?.also { it.writer = writer }?.invoke(mob)
                }
            }

            writer.sayTo(mob).info("Pending Action: ${mob.action::class.java.simpleName}")
        }
        mobs.forEach { it.action.apply { this.writer = writer }(it) }
    }

    private fun checkIfTurnContinues(action: Action?) =
        action == null || action is FreeAction || action is Repeat && action.isRepeatFreeAction

    private fun Mob.getAction(reader: Reader): Action? = when (behavior) {
        MobBehavior.PLAYER -> interpretPlayerAction(reader, this)
        MobBehavior.HELPFUL -> TODO()
        MobBehavior.IMMOBILE -> TODO()
        MobBehavior.LOOTER -> {
            when (roll20()) {
                in 0..2 -> getRandomVisibleItem()?.let { Take(it) }
                in 3..4 -> this.items.takeIf { it.isNotEmpty() }?.random()?.let { Drop(it) }
                else -> Pass
            } ?: Pass
        }
        MobBehavior.AGGRESSIVE -> {
            when (roll20()) {
                in 0..18 -> getFirstVisibleMob()?.let { AttackMelee(it) }
                else -> Pass
            } ?: Pass
        }
        MobBehavior.WANDERER -> when (roll20()) {
            0 -> Move(Direction.NORTH)
            1 -> Move(Direction.EAST)
            2 -> Move(Direction.SOUTH)
            3 -> Move(Direction.WEST)
            else -> Pass
        }
        MobBehavior.FLEE -> TODO()
    }

    private fun interpretPlayerAction(
        reader: Reader,
        mob: Mob
    ): Action? {
        val read = reader.read(mob)
        val input = read?.toLowerCase() ?: return Inactive
        return getCommands(mob).map { it.first.toRegex() to it.second }
            .firstOrNull { it.first.matches(input) }
            ?.let { pair ->
                pair.first.find(input)
                    ?.groupValues
                    ?.also { println("Matches: $it") }
                    ?.let { pair.second.invoke(it) }
            } ?: Retry("Unknown Command")
    }

    private fun getCommands(mob: Mob) = listOf<Pair<String, ((List<String>) -> Action)>>(
        // Util
        "(\\.|\n|again)" to { _ -> Repeat(mob.action) },
        "debug$ARG" to { i ->
            safeLet(i.getOrNull(1)) { target ->
                Debug(target)
            } ?: Retry("Debug what?")
        },
        "(wait|pass)" to { _ -> Pass },

        // INFO
        "l(s|l|ook)?" to { _ -> Look() },
        "do(or(s)?)?" to { _ -> Doors() },
        "exit(s)?" to { _ -> Doors() },
        "ex(amine)?$ARG" to { i ->
            safeLet(i.getOrNull(2)) { target ->
                Examine(target)
            } ?: Retry("What should I examine?")
        },

        // Items
        "i(nventory)?" to { _ -> Inventory() },
        "take$ARG" to { i ->
            safeLet(i.getOrNull(1)) { itemName ->
                if (itemName.isBlank()) {
                    Retry("What would you like to take?")
                } else {
                    Take.getOrRetry(mob, itemName)
                }
            } ?: Retry("What would you like to take?")
        },
        "drop$ARG" to { i ->
            safeLet(i.getOrNull(1)) { itemName ->
                if (itemName.isBlank()) {
                    Retry("What would you like to drop?")
                } else {
                    Drop.getOrRetry(mob, itemName)
                }
            } ?: Retry("What would you like to drop?")
        },
        "(eat|consume|quaff)$ARG" to { i ->
            safeLet(i.getOrNull(2)) { itemName ->
                Consume.getOrRetry(mob, itemName)
            } ?: Retry("What would you like to consume?")
        },
        "(give)$ARG$ARG" to { i ->
            safeLet(i.getOrNull(2), i.getOrNull(3)) { target, itemName ->
                Give.getOrRetry(mob, target, itemName)
            } ?: Retry("What would you like to give?")
        },

        // Movement
        "n(orth)?" to { _ -> Move(Direction.NORTH) },
        "s(outh)?" to { _ -> Move(Direction.SOUTH) },
        "e(ast)?" to { _ -> Move(Direction.EAST) },
        "w(est)?" to { _ -> Move(Direction.WEST) },
        "u(p)?" to { _ -> Move(Direction.UP) },
        "d(own)?" to { _ -> Move(Direction.DOWN) },

        // Combat
        "(atk|attack)$ARG" to { i ->
            safeLet(i.getOrNull(2)) { mobName ->
                Action.attackOrRetry(mob, mobName)
            } ?: Retry("You need to specify a target!")
        },

        // Settings
        "set$ARG$ARG" to { i ->
            val action: Action = safeLet(i.getOrNull(1), i.getOrNull(2)) { settingKey, settingValue ->
                when (settingKey) {
                    "attack" -> Settings.getAttack(settingValue)
                    else -> Retry("I don't know about that setting.")
                }
            } ?: Retry("What would you like to set?")
            action
        }

        // follow
        // steal
        // scan

    )

    companion object {
        private const val ARG = " *([^\\s]*)?"
    }
}