package com.grappenmaker.solarpatcher.config

import com.grappenmaker.solarpatcher.asm.MethodDescription
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