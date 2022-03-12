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

import kotlinx.serialization.json.Json
import org.objectweb.asm.Opcodes

// Default json object
val json = Json {
    encodeDefaults = true
    prettyPrint = true
    ignoreUnknownKeys = true
}

// Several constant values. Some constant values exist in the Modules.kt files, or alike
object Constants {
    const val API = Opcodes.ASM7
    const val packetClassname = "com/lunarclient/bukkitapi/nethandler/LCPacket"
    const val defaultCapesServer = "s.optifine.net"
    const val defaultLevelHeadText = "Level: "
    const val defaultAutoGGText = "/achat gg"
    const val defaultNickhiderName = "You"
    const val defaultFPSText = "\u0001 FPS"
    const val defaultCPSText = "\u0001 CPS"
    const val defaultReachText = "\u0001 blocks"
    const val defaultPingText = "\u0001 ms"
    const val defaultWindowName = "Lunar Client (\u0001-\u0001/\u0001)"

    // The difference is one letter, beware
    private const val optifinePrefix = "net/optifine/player"
    const val playerConfigurationsName = "$optifinePrefix/PlayerConfigurations"
    const val playerConfigurationName = "$optifinePrefix/PlayerConfiguration"
    const val playerConfigReceivername = "$optifinePrefix/PlayerConfigurationReceiver"
    const val fileDownloadThreadName = "net/optifine/http/FileDownloadThread"
    const val httpUtilName = "net/optifine/http/HttpUtils"

    object ToggleSprint {
        private const val defaultFlyingText = "flying"
        private const val defaultRidingText = "riding"
        private const val defaultDescendingText = "descending"
        private const val defaultDismountingText = "dismounting"
        private const val defaultSneakingText = "sneaking"
        private const val defaultSprintingText = "sprinting"
        val defaultConfig = listOf(
            defaultFlyingText,
            defaultRidingText,
            defaultDescendingText,
            defaultDismountingText,
            defaultSneakingText,
            defaultSprintingText
        ).associateWith { it }
    }
}