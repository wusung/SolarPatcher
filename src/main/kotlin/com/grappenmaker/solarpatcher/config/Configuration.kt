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
import kotlinx.serialization.Serializable
import kotlin.reflect.full.memberProperties

@Serializable
data class Configuration(
    val enableAll: Boolean = true,
    val nickhider: Nickhider = Nickhider(),
    val fps: FPS = FPS(),
    val cps: CPS = CPS(),
    val autoGG: AutoGG = AutoGG(),
    val levelHead: LevelHead = LevelHead(),
    val freelook: Freelook = Freelook(),
    val pinnedServers: PinnedServers = PinnedServers(),
    val blogPosts: BlogPosts = BlogPosts(),
    val modpacketRemoval: ModpacketRemoval = ModpacketRemoval(),
    val mantleIntegration: MantleIntegration = MantleIntegration(),
    val windowName: WindowName = WindowName(),
    val noHitDelay: NoHitDelay = NoHitDelay(),
    val fpsSpoof: FPSSpoof = FPSSpoof(),
    val cpsSpoof: CPSSpoof = CPSSpoof(),
    val customCommands: CustomCommands = CustomCommands(),
    val rpcUpdate: RPCUpdate = RPCUpdate(),
    val websocketPrivacy: WebsocketPrivacy = WebsocketPrivacy(),
    val uncapReach: UncapReach = UncapReach(),
    val removeFakeLevelhead: RemoveFakeLevelhead = RemoveFakeLevelhead(),
    val removeHashing: RemoveHashing = RemoveHashing()
) {
    fun getModules() = Configuration::class.memberProperties
        .map { it.get(this) }
        .filterIsInstance<Module>()
}