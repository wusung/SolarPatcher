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

import com.grappenmaker.solarpatcher.*
import com.grappenmaker.solarpatcher.modules.*
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.full.functions
import kotlin.reflect.full.instanceParameter
import kotlin.reflect.full.memberProperties

@Serializable
data class Configuration(
    val patcherVersion: String = Versioning.version,
    val debug: Boolean = false,
    val enableAll: Boolean = false,
    val modName: ModName = ModName(),
    val nickhider: Nickhider = Nickhider(),
    val fps: FPS = FPS(),
    val cps: CPS = CPS(),
    val autoGG: AutoGG = AutoGG(),
    val levelHead: LevelHead = LevelHead(),
    val metadata: Metadata = Metadata(),
    val modpacketRemoval: ModpacketRemoval = ModpacketRemoval(),
    val mantleIntegration: MantleIntegration = MantleIntegration(),
    val windowName: WindowName = WindowName(),
    val noHitDelay: NoHitDelay = NoHitDelay(),
    val fpsSpoof: FPSSpoof = FPSSpoof(),
    val customCommands: CustomCommands = CustomCommands(),
    val rpcUpdate: RPCUpdate = RPCUpdate(),
    val tasklistPrivacy: TasklistPrivacy = TasklistPrivacy(),
    val hostslistPrivacy: HostslistPrivacy = HostslistPrivacy(),
    val uncapReach: UncapReach = UncapReach(),
    val removeFakeLevelhead: RemoveFakeLevelhead = RemoveFakeLevelhead(),
    val removeHashing: RemoveHashing = RemoveHashing(),
    val debugPackets: DebugPackets = DebugPackets(),
    val keystrokesCPS: KeystrokesCPS = KeystrokesCPS(),
    val toggleSprintText: ToggleSprintText = ToggleSprintText(),
    val reachText: ReachText = ReachText(),
    val fixPings: FixPings = FixPings(),
    val lunarOptions: LunarOptions = LunarOptions()
) {
    private val alwaysEnabledModules = listOf(RuntimeData, HandleNotifications, ClassCacher)
    val modules = Configuration::class.memberProperties
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