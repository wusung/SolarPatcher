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

import com.grappenmaker.solarpatcher.asm.method.MethodDescription
import kotlinx.serialization.json.Json
import org.objectweb.asm.Opcodes

const val API = Opcodes.ASM7
const val packetClassname = "com/lunarclient/bukkitapi/nethandler/LCPacket"
const val defaultCapesServer = "s.optifine.net"
const val defaultLevelHeadText = "Level: "
const val defaultAutoGGText = "/achat gg"
const val defaultNickhiderName = "You"
const val defaultFPSText = "FPS"
val runMethodDescription = MethodDescription("run", "()V")

// Default json object
val json = Json {
    encodeDefaults = true
    prettyPrint = true
    ignoreUnknownKeys = true
}