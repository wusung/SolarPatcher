package com.grappenmaker.solarpatcher

import com.grappenmaker.solarpatcher.util.MethodDescription
import org.objectweb.asm.Opcodes

const val API = Opcodes.ASM7
const val packetClassname = "com/lunarclient/bukkitapi/nethandler/LCPacket"
const val defaultCapesServer = "s.optifine.net"
const val defaultLevelHeadText = "Level: "
const val defaultAutoGGText = "/achat gg"
const val defaultNickhiderName = "You"
const val defaultFPSText = "FPS"
const val defaultCPSText = "CPS"
val runMethodDescription = MethodDescription("run", "()V")