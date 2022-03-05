/*
 * Solar Patcher, a runtime patcher for Lunar Client
 * Copyright (C) 2022 Solar Tweaks and respective contributors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.grappenmaker.solarpatcher.config

import com.grappenmaker.solarpatcher.Versioning
import com.grappenmaker.solarpatcher.modules.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.KVisibility
import kotlin.reflect.full.functions
import kotlin.reflect.full.instanceParameter
import kotlin.reflect.full.memberProperties

@Serializable
data class Configuration(
    val patcherVersion: String = Versioning.version,
    val debug: Boolean = false,
    val enableAll: Boolean = false,

    // Allows you to change your nickhider name.
    // This is different to Lunar's implementation because it supports colors
    val nickhider: Nickhider = Nickhider(),
    val fps: FPS = FPS(), // Change your FPS text
    val cps: CPS = CPS(), // Change your CPS text
    val autoGG: AutoGG = AutoGG(), // Change your autogg command (eg. /achat gg (default) -> /ac Good Game)
    val levelHead: LevelHead = LevelHead(), // Sets the levelhead prefix
    // Allows you to disable pinned servers, enable freelook on hypixel, remove blog posts etc
    val metadata: Metadata = Metadata(),
    // Removes LCModSettingsPacket so no mods can be disabled by servers
    val modpacketRemoval: ModpacketRemoval = ModpacketRemoval(),
    val mantleIntegration: MantleIntegration = MantleIntegration(), // Integrate with Mantle.gg cape system
    val windowName: WindowName = WindowName(), // Change the name of the window
    // Removes "hit delay" (1.7-like combat). Note that this is not a cheat, just a bugfix for 1.8
    val noHitDelay: NoHitDelay = NoHitDelay(),
    val fpsSpoof: FPSSpoof = FPSSpoof(), // Multiplier for your FPS counter
    // Allows you to remap commands, eg. /qb -> /play duels_bridge_duel
    val customCommands: CustomCommands = CustomCommands(),
    // Change the text that is displayed in your Rich Presence
    // Can also show your activity (eg. Playing Hypixel/AntiAC/Private Server)
    val rpcUpdate: RPCUpdate = RPCUpdate(),
    // Allows the user to have more privacy, because your full task list and hosts list is sent to
    // Lunar Servers every time your client starts, which is questionable
    val tasklistPrivacy: TasklistPrivacy = TasklistPrivacy(),
    val hostslistPrivacy: HostslistPrivacy = HostslistPrivacy(),
    val uncapReach: UncapReach = UncapReach(), // Fixes the reach display to properly display reach in creative mode
    val removeFakeLevelhead: RemoveFakeLevelhead = RemoveFakeLevelhead(), // Removes fake level head levels for nicked players
    val removeHashing: RemoveHashing = RemoveHashing(), // Removes class hash checks. Can speed up launches
    // For developers. Allows to intercept packets being sent from and to Lunar BukkitAPI
    val debugPackets: DebugPackets = DebugPackets(),
    val keystrokesCPS: KeystrokesCPS = KeystrokesCPS(), // Change the Keystrokes CPS text
    val toggleSprintText: ToggleSprintText = ToggleSprintText(), // Change the togglesprint text
    val reachText: ReachText = ReachText(), // Change the reach display etxt
    // Fix chat "pings" to only happen with actual chat packets and not action bars
    val fixPings: FixPings = FixPings(),
    val lunarOptions: LunarOptions = LunarOptions(), // Always show "Lunar Options" in pause menu
    // Reenables overlays (e.g. to change the icon or panorama, builtin lunar assets)
    val supportOverlays: SupportOverlays = SupportOverlays(),
    // Allows you to use toggle sneak in containers on hypixel
    val toggleSneakContainer: ToggleSneakContainer = ToggleSneakContainer()
) {
    // RuntimeData -> Internal module to retrieve information about the current lunar installation
    // HandleNotifications -> forced because it fixes a Lunar Client bug, brings back the LCPacketNotification
    // ModName -> changes the vendor; enforced because this is not normal Lunar Client
    @Transient
    private val alwaysEnabledModules = listOf(RuntimeData, HandleNotifications, ModName)

    // All enabled modules, cached
    // Retrieved with reflection, yes it is slow but i dont want to put
    // the mess of all the modules twice.
    @Transient
    val modules = Configuration::class.memberProperties
        .filter { it.visibility == KVisibility.PUBLIC }
        .map { it(this) }
        .filterIsInstance<Module>() + alwaysEnabledModules

    // TODO: remove when kotlinx.serialization gets fixed
    // See https://github.com/JetBrains/kotlin/pull/4727
    fun modulesClone(): Configuration {
        val cloneFun = this::copy
        return cloneFun.callBy(cloneFun.parameters.filter {
            it.typeClass?.java?.let { c -> Module::class.java.isAssignableFrom(c) } == true
        }.associateWith { param ->
            val clazz = param.typeClass!!
            val func = clazz.functions.find { f -> f.name == "copy" } ?: error("No copy for $clazz?")
            val instance = Configuration::class.memberProperties
                .find { it.name == param.name }!!.get(this)

            func.callBy(mapOf(func.instanceParameter!! to instance))
        })
    }
}

private val KParameter.typeClass get() = type.classifier as? KClass<*>