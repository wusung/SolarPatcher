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

package com.grappenmaker.solarpatcher

import com.grappenmaker.solarpatcher.asm.FieldDescription
import com.grappenmaker.solarpatcher.asm.asDescription
import com.grappenmaker.solarpatcher.asm.constants
import com.grappenmaker.solarpatcher.asm.matching.ClassMatcher
import com.grappenmaker.solarpatcher.asm.matching.ClassMatching
import com.grappenmaker.solarpatcher.asm.matching.ClassMatching.match
import com.grappenmaker.solarpatcher.asm.matching.ClassMatching.plus
import com.grappenmaker.solarpatcher.asm.matching.MethodMatcherData
import com.grappenmaker.solarpatcher.asm.matching.MethodMatching.match
import com.grappenmaker.solarpatcher.asm.matching.MethodMatching.matchAny
import com.grappenmaker.solarpatcher.asm.matching.MethodMatching.matchClinit
import com.grappenmaker.solarpatcher.asm.matching.MethodMatching.matchDescriptor
import com.grappenmaker.solarpatcher.asm.matching.MethodMatching.matchName
import com.grappenmaker.solarpatcher.asm.matching.MethodMatching.matchOwner
import com.grappenmaker.solarpatcher.asm.matching.MethodMatching.plus
import com.grappenmaker.solarpatcher.asm.matching.asMatcher
import com.grappenmaker.solarpatcher.asm.method.*
import com.grappenmaker.solarpatcher.asm.transform.*
import com.grappenmaker.solarpatcher.asm.util.*
import com.grappenmaker.solarpatcher.config.Constants
import com.grappenmaker.solarpatcher.config.Constants.API
import com.grappenmaker.solarpatcher.config.Constants.defaultAutoGGText
import com.grappenmaker.solarpatcher.config.Constants.defaultCPSText
import com.grappenmaker.solarpatcher.config.Constants.defaultCapesServer
import com.grappenmaker.solarpatcher.config.Constants.defaultFPSText
import com.grappenmaker.solarpatcher.config.Constants.defaultLevelHeadText
import com.grappenmaker.solarpatcher.config.Constants.defaultNickhiderName
import com.grappenmaker.solarpatcher.config.Constants.defaultReachText
import com.grappenmaker.solarpatcher.config.Constants.packetClassname
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.objectweb.asm.*
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.JumpInsnNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import java.util.*
import java.util.concurrent.ThreadLocalRandom
import java.util.function.Consumer
import kotlin.math.floor
import kotlin.reflect.jvm.javaMethod

val internalString = getInternalName<String>()

@Serializable
sealed class Module : TransformGenerator {
    abstract val isEnabled: Boolean
}

@Serializable
sealed class TextTransformModule : Module() {
    @Transient
    open val matcher: ClassMatcher = ClassMatching.matchAny()

    abstract val from: String
    abstract val to: String

    private val generator: TransformGenerator
        get() = matcherGenerator(
            ClassTransform(listOf(TextTransform(matchAny(), from, to))),
            matcher = matcher + {
                it.constants.filterIsInstance<String>().any { s -> s.contains(from) }
            })

    override fun generate(node: ClassNode) = generator.generate(node)
}

//@Serializable
//sealed class RemoveInvokeModule : Module() {
//    abstract val method: MethodMatcherData
//    abstract val toRemove: MethodMatcherData
//    abstract val popCount: Int
//    abstract val matcher: ClassMatcher
//
//    override val generator: TransformGenerator
//        get() {
//            val removeMatcher = toRemove.asMatcher()
//            return matcherGenerator(
//                ClassTransform(RemoveInvokeTransform(method.asMatcher(), removeMatcher, popCount)),
//                matcher + {
//                    it.methods
//                        .flatMap { m -> m.instructions }
//                        .filterIsInstance<MethodInsnNode>()
//                        .any { node -> removeMatcher.match(MethodDescription(node.name, node.desc, node.owner)) }
//                })
//        }
//}

@Serializable
data class Nickhider(
    override val from: String = defaultNickhiderName,
    override val to: String = defaultNickhiderName,
    override val isEnabled: Boolean = false
) : TextTransformModule() {
    @Transient
    override val matcher: ClassMatcher = { it.constants.contains("lastKnownHypixelNick") }
}

@Serializable
data class FPS(
    override val from: String = defaultFPSText,
    override val to: String = defaultFPSText,
    override val isEnabled: Boolean = false
) : TextTransformModule()

@Serializable
data class CPS(
    override val from: String = defaultCPSText,
    override val to: String = defaultCPSText,
    override val isEnabled: Boolean = false,
) : TextTransformModule()

@Serializable
data class AutoGG(
    override val from: String = defaultAutoGGText,
    override val to: String = defaultAutoGGText,
    override val isEnabled: Boolean = false
) : TextTransformModule()

@Serializable
data class LevelHead(
    override val from: String = defaultLevelHeadText,
    override val to: String = defaultLevelHeadText,
    override val isEnabled: Boolean = false
) : TextTransformModule()

val metadataMatcher: ClassMatcher = { node ->
    val matchStrings = listOf("versions", "name")
    node.interfaces.contains(getInternalName<Runnable>())
            && node.constants.containsAll(matchStrings)
}

@Serializable
data class Metadata(
    val removeCalls: List<RemoveConfig> = listOf(
        RemoveConfig("serverIntegration"),
        RemoveConfig("pinnedServers"),
        RemoveConfig("modSettings"),
        RemoveConfig("clientSettings"),
        RemoveConfig("featureFlag")
    ),
    override val isEnabled: Boolean = true
) : Module() {
    @Serializable
    data class RemoveConfig(
        val metadataName: String,
        val isEnabled: Boolean = true
    )

    override fun generate(node: ClassNode): ClassTransform? {
        if (!metadataMatcher.match(node)) return null

        val runMatcher = Runnable::run.javaMethod!!.asMatcher()
        val runMethod =
            node.methods.find { runMatcher.match(it.asDescription(node)) } ?: return null

        val usedCalls = removeCalls.filter { it.isEnabled }

        val unconditionalJumps = runMethod.instructions
            .filterIsInstance<MethodInsnNode>()
            .filter { it.name == "has" }
            .mapNotNull { call ->
                val previous = call.previous
                if (previous !is LdcInsnNode) return@mapNotNull null
                if (!usedCalls.any { previous.cst == it.metadataName }) return@mapNotNull null

                call.next as? JumpInsnNode
            }

        return ClassTransform(
            VisitorTransform(runMatcher) { parent ->
                object : MethodVisitor(API, parent) {
                    override fun visitJumpInsn(opcode: Int, label: Label) {
                        if (unconditionalJumps.any { it.label.label.offset == label.offset }) {
                            super.visitJumpInsn(GOTO, label)
                        } else {
                            super.visitJumpInsn(opcode, label)
                        }
                    }
                }
            }
        )
    }
}

//@Serializable
//data class Freelook(
//    override val method: MethodMatcherData = runMatcherData,
//    override val isEnabled: Boolean = false,
//    override val toRemove: MethodMatcherData = MethodMatcherData(
//        "llIllIIllIlIlIIIIlIlIllll",
//        "(Lcom/google/gson/JsonArray;)V"
//    )
//) : RemoveInvokeModule() {
//    @Transient
//    override val matcher = metadataMatcher
//
//    @Transient
//    override val popCount = 2
//}
//
//@Serializable
//data class PinnedServers(
//    override val method: MethodMatcherData = runMatcherData,
//    override val isEnabled: Boolean = false,
//    override val toRemove: MethodMatcherData = MethodMatcherData(
//        "llIIIllIIllIIIllIIlIllIIl",
//        "(Lcom/google/gson/JsonArray;)V"
//    )
//) : RemoveInvokeModule() {
//    @Transient
//    override val matcher = metadataMatcher
//
//    @Transient
//    override val popCount = 2
//}
//
//@Serializable
//data class BlogPosts(
//    override val method: MethodMatcherData = runMatcherData,
//    override val isEnabled: Boolean = false,
//    override val toRemove: MethodMatcherData = MethodMatcherData("forEach", "(Ljava/util/function/Consumer;)V")
//) : RemoveInvokeModule() {
//    @Transient
//    override val matcher = metadataMatcher
//
//    @Transient
//    override val popCount = 2
//}

@Serializable
data class ModpacketRemoval(
    override val isEnabled: Boolean = false
) : Module(), TransformGenerator by matcherGenerator(ClassTransform(visitors = listOf { parent: ClassVisitor ->
    AdviceClassVisitor(
        parent,
        matchName("addPacket") + matchDescriptor("(ILjava/lang/Class;)V"),
        enterAdvice = {
            loadVariable(0, ILOAD)
            visitIntInsn(BIPUSH, 31)

            val jumpLabel = Label()
            visitJumpInsn(IF_ICMPNE, jumpLabel)
            returnMethod()
            visitLabel(jumpLabel)
        }
    )
}, shouldExpand = true), matcher = ClassMatching.matchName(packetClassname))

@Serializable
data class MantleIntegration(
    override val from: String = defaultCapesServer,
    override val to: String = "capes.mantle.gg",
    override val isEnabled: Boolean = false
) : TextTransformModule()

@Serializable
data class WindowName(
    val from: String = "Lunar Client (\u0001-\u0001/\u0001)",
    val to: String = "Lunar Client (Modded by Solar Tweaks)",
    override val isEnabled: Boolean = false
) : Module() {
    override fun generate(node: ClassNode): ClassTransform? {
        val method = node.methods.find { it.constants.contains(from) } ?: return null
        return ClassTransform(ConstantValueTransform(method.asDescription(node).asMatcher(), to))
    }
}

private const val serverRuleClass = "com/lunarclient/bukkitapi/nethandler/client/obj/ServerRule"
private const val logDetection = "No default value for Server Rule [\u0001] found!"

@Serializable
data class NoHitDelay(
//    val method: MethodMatcherData = MethodMatcherData("lIlllllIIIIlIIlllIlIlIIIl", "()Ljava/util/Map;"),
//    val className: String = "lunar/es/IIIIIIIlIlIllIlIIIIIlIlll",
    override val isEnabled: Boolean = false
) : Module() {

    override fun generate(node: ClassNode): ClassTransform? {
        val method = node.methods.find { it.constants.contains(logDetection) } ?: return null
        return ClassTransform(visitors = listOf {
            AdviceClassVisitor(it, method.asDescription(node).asMatcher(), exitAdvice = { opcode: Int ->
                if (opcode == ARETURN) {
                    // No, this is not a redundant dup-pop
                    dup()
                    visitFieldInsn(GETSTATIC, serverRuleClass, "LEGACY_COMBAT", "L$serverRuleClass;")
                    visitInsn(ICONST_1)
                    invokeMethod(java.lang.Boolean::class.java.getMethod("valueOf", Boolean::class.javaPrimitiveType))
                    invokeMethod(Map::class.java.getMethod("put", Object::class.java, Object::class.java))
                    pop()
                }
            })
        }, shouldExpand = true)
    }
}
//) : Module(), TransformGenerator by matcherGenerator(ClassTransform(visitors = listOf {
//    AdviceClassVisitor(it, method.asMatcher(), exitAdvice = { opcode: Int ->
//        if (opcode == ARETURN) {
//            // No, this is not a redundant dup-pop
//            dup()
//            visitFieldInsn(GETSTATIC, serverRuleClass, "LEGACY_COMBAT", "L$serverRuleClass;")
//            visitInsn(ICONST_1)
//            invokeMethod(java.lang.Boolean::class.java.getMethod("valueOf", Boolean::class.javaPrimitiveType))
//            invokeMethod(Map::class.java.getMethod("put", Object::class.java, Object::class.java))
//            pop()
//        }
//    })
//}, shouldExpand = true), matcher = ClassMatching.matchName(className))

@Serializable
data class FPSSpoof(
    val multiplier: Double = 2.0,
    override val isEnabled: Boolean = false
) : Module(), TransformGenerator by matcherGenerator(ClassTransform(InvokeAdviceTransform(
    matchAny(),
    MethodMatcherData("bridge\$getDebugFPS", "()I").asMatcher(),
    afterAdvice = { spoof(multiplier) }
)), matcher = { it.constants.contains("[1466 FPS]") })

@Serializable
data class CPSSpoof(
    val chance: Double = .1,
    val className: String = "lunar/dp/llIlIIIllIlllllIllIIIIIlI",
    val method: MethodMatcherData = MethodMatcherData(
        "llIlIIIllIlllllIllIIIIIlI",
        "(Llunar/aK/llIllIIllIlIlIIIIlIlIllll;)V"
    ),
    override val isEnabled: Boolean = false
) : Module(), TransformGenerator by matcherGenerator(ClassTransform(InvokeAdviceTransform(
    method.asMatcher(),
    MethodMatcherData("add", "(Ljava/lang/Object;)Z").asMatcher(),
    afterAdvice = {
        // Init label
        val label = Label()

        // Boolean from add
        pop()

        // Load chance, get random double
        visitLdcInsn(chance)
        invokeMethod(ThreadLocalRandom::current)
        invokeMethod(Random::class.java.getMethod("nextDouble"))

        // Check if chance is met, if so, add another
        visitInsn(DCMPG)
        visitJumpInsn(IFLE, label)

        // Call ourselves. Yep, hacky, but idk what else to do
        // because if i were to visit another add method, that would get advice too...
        loadVariable(0)
        loadVariable(1)
        visitMethodInsn(INVOKEVIRTUAL, className, method.name, method.descriptor, false)

        // Done, let's add back a fake boolean, because otherwise
        // the stack height doesn't match
        visitLabel(label)
        visitInsn(ICONST_1)
    }
)), matcher = ClassMatching.matchName(className)) {
    init {
        require(chance > 0 && chance < 1) { "chance must be >0 and <1" }
    }
}

// Util to multiply by arbitrary double value
// Used for spoofing cps/fps values
private fun MethodVisitor.spoof(multiplier: Double) {
    if (floor(multiplier) == multiplier && multiplier <= 0xFF) {
        // Whole number, can load as byte integer
        visitIntInsn(BIPUSH, multiplier.toInt())

        // Can immediately multiply
        visitInsn(IMUL)
    } else {
        // Can't immediately multiply, should first convert fps/cps to double
        visitInsn(I2D)

        // Should use ldc instruction
        visitLdcInsn(multiplier)

        // Multiply now
        visitInsn(DMUL)

        // Convert back to int
        visitInsn(D2I)
    }
}

@Serializable
data class CustomCommands(
    val commands: Map<String, Command> = mapOf(
        "qb" to Command("/play duels_bridge_duel"),
        "qbd" to Command("/play duels_bridge_doubles"),
        "db" to Command("/duel ", " bridge"),
        "bwp" to Command("/play bedwars_practice")
    ),
    val className: String = "lunar/aE/llIllIIllIlIlIIIIlIlIllll",
    val instanceName: String = "llIllIIllIlIlIIIIlIlIllll",
    val registerMethod: MethodDescription = MethodDescription(
        "llIlIIIllIlllllIllIIIIIlI",
        "(Ljava/lang/Class;Ljava/util/function/Consumer;)Z",
        className,
        ACC_PUBLIC
    ),
    val chatEventClass: String = "lunar/aH/llIlIIIllIlllllIllIIIIIlI",
    override val isEnabled: Boolean = false
) : Module() {
    companion object {
        // Instance of this class, initialized at runtime
        @JvmStatic
        lateinit var instance: CustomCommands
            private set

        // Function that will return our listener (at runtime),
        // so that we don't have to implement it with bytecode
        @Suppress("Unused") // it is used obviously
        @JvmStatic
        fun handler() = Consumer<Any?> { event ->
            val textField = event.javaClass.fields.first()
            val text = textField[event] as String
            if (!text.startsWith("/")) return@Consumer

            val components = text.split(" ")
            instance.commands[components.first().substring(1)]?.let {
                // Change text sent
                textField[event] = it.prefix + components.drop(1).joinToString(" ") + it.suffix
            }
        }
    }

    private val generator
        get() = matcherGenerator(ClassTransform(visitors = listOf {
            AdviceClassVisitor(it, matchClinit(), exitAdvice = {
                // Set instance of customcommands
                instance = this@CustomCommands

                // Get event bus instance
                visitFieldInsn(GETSTATIC, className, instanceName, "L$className;")

                // Load chat event class onto the stack
                visitLdcInsn(Type.getObjectType(chatEventClass))

                // Load consumer onto the stack
                invokeMethod(
                    InvocationType.STATIC,
                    "handler",
                    "()Ljava/util/function/Consumer;",
                    getInternalName<CustomCommands>()
                )

                // Register custom listener
                invokeMethod(InvocationType.VIRTUAL, registerMethod)
            })
        }), matcher = ClassMatching.matchName(className))

    override fun generate(node: ClassNode) = generator.generate(node)
}

@Serializable
data class Command(val prefix: String, val suffix: String = "")

const val defaultClientID = 562286213059444737L

@Serializable
data class RPCUpdate(
    val clientID: Long = 920998351430901790,
    val icon: String = "logo",
    val iconText: String = "Solar Tweaks",
    override val isEnabled: Boolean = false
) : Module(), TransformGenerator by matcherGenerator(ClassTransform(visitors = listOf {
    replaceClassConstants(
        it, mapOf(
            defaultClientID to clientID,
            "icon_07_11_2020" to icon,
            "Lunar Client" to iconText
        )
    )
}), matcher = { it.constants.contains(defaultClientID) })

private fun privacyTransformGenerator(path: String): TransformGenerator = object : TransformGenerator {
    override fun generate(node: ClassNode): ClassTransform? {
        val method = node.methods.find { it.constants.contains(path) }
        return method?.let { ClassTransform(StubMethodTransform(method.asDescription(node))) }
    }
}

@Serializable
data class TasklistPrivacy(override val isEnabled: Boolean = false) : Module(),
    TransformGenerator by privacyTransformGenerator("\u0001\\system32\\tasklist.exe")

@Serializable
data class HostslistPrivacy(override val isEnabled: Boolean = false) : Module(),
    TransformGenerator by privacyTransformGenerator("\u0001\\system32\\drivers\\etc\\hosts")

@Serializable
data class UncapReach(override val isEnabled: Boolean = false) : Module(), TransformGenerator by matcherGenerator(
    ClassTransform(
        listOf(
            RemoveInvokeTransform(
                matchAny(),
                MethodMatcherData("min", "(DD)D").asMatcher()
            )
        ),
        listOf { parent: ClassVisitor -> replaceClassConstants(parent, mapOf(3.0 to null)) }),
    matcher = { it.constants.containsAll(listOf(" blocks", "range")) }
)

@Serializable
data class RemoveFakeLevelhead(override val isEnabled: Boolean = false) : Module(),
    TransformGenerator by matcherGenerator(ClassTransform(
        listOf(
            RemoveInvokeTransform(
                matchAny(),
                ThreadLocalRandom::current.javaMethod!!.asMatcher()
            ),
            ReplaceCodeTransform(
                matchAny(),
                ThreadLocalRandom::class.java.getMethod("nextInt", Int::class.javaPrimitiveType).asMatcher()
            ) { _, _ ->
                pop()
                visitIntInsn(BIPUSH, -26) // Hacky bro
            }
        )), matcher = { it.constants.contains("Level: ") }
    )

@Serializable
data class RemoveHashing(
    val className: String = "WMMWMWMMWMWWMWMWWWWWWWMMM",
    val method: MethodMatcherData = MethodMatcherData(
        "WWWWWNNNNWMWWWMWWMMMWMMWM",
        "(L$internalString;[BZ)Z"
    ),
    override val isEnabled: Boolean = false
) : Module(), TransformGenerator by matcherGenerator(
    ClassTransform(
        ImplementTransform(method.asMatcher()) {
            visitInsn(ICONST_1)
            returnMethod(IRETURN)
        }
    ), matcher = ClassMatching.matchName(className)
)

@Serializable
data class DebugPackets(override val isEnabled: Boolean = false) : Module(),
    TransformGenerator by matcherGenerator(ClassTransform(
        InvokeAdviceTransform(
            matchAny(),
            matchName("process") + matchOwner(packetClassname),
            afterAdvice = {
                visitPrintln {
                    loadVariable(2)
                    invokeMethod(java.lang.Object::class.java.getMethod("getClass"))
                    invokeMethod(Class<*>::getName)

                    val stringName = internalString
                    concat(
                        "(L$stringName;)L$stringName;",
                        "Received/processed packet type \u0001"
                    )
                }
            }
        )), matcher = { it.interfaces.contains("com/lunarclient/bukkitapi/nethandler/client/LCNetHandlerClient") }
    )

@Serializable
data class KeystrokesCPS(
    override val from: String = defaultCPSText,
    override val to: String = defaultCPSText,
    override val isEnabled: Boolean = false
) : TextTransformModule()

@Serializable
data class ToggleSprintText(
    val replacements: Map<String, String> = Constants.ToggleSprint.defaultConfig,
    val method: MethodMatcherData = MethodMatcherData.CLINIT,
    override val isEnabled: Boolean = false,
) : Module(), TransformGenerator by matcherGenerator(
    ClassTransform(replacements.map { (from, to) -> TextTransform(method.asMatcher(), from, to) }),
    matcher = { it.constants.containsAll(replacements.keys) }
)

@Serializable
data class ReachText(
    override val from: String = defaultReachText,
    override val to: String = defaultReachText,
    override val isEnabled: Boolean = false
) : TextTransformModule() {
    @Transient
    override val matcher: ClassMatcher = { it.constants.contains("[1.3 blocks]") }
}

const val lunarMainClassname = "lunar/as/llIllIIllIlIlIIIIlIlIllll"
const val popupManagerClassname = "lunar/dg/IllIIlIIlIlIllllIIllllIll"
const val notifPacketClassname = "com/lunarclient/bukkitapi/nethandler/client/LCPacketNotification"

@Serializable
data class HandleNotifs(
    val getLunarmainMethod: MethodDescription = MethodDescription(
        "IIIIIlIIlIllIlIIlllIlIIIl",
        "()L$lunarMainClassname;",
        lunarMainClassname
    ),
    val getPopupManagerMethod: MethodDescription = MethodDescription(
        "lIIlllIlIllIIIIlIIlIlIIll",
        "()L$popupManagerClassname;",
        lunarMainClassname
    ),
    val levelEnum: String = "lunar/dg/IIlllllIllllIlIIllllIlIlI",
    val sendPopupMethod: MethodDescription = MethodDescription(
        "llIlIIIllIlllllIllIIIIIlI",
        "(L$levelEnum;L$internalString;L$internalString;)Llunar/dg/IIIlIlIlIIIllIlIIllllllIl;",
        popupManagerClassname
    ),
    val levelmap: Map<String, String> = mapOf(
        "info" to "llIlIIIllIlllllIllIIIIIlI",
        "success" to "llIllIIllIlIlIIIIlIlIllll",
        "warning" to "llIIIllIIllIIIllIIlIllIIl",
        "error" to "lllIlIIllllIllIIIlIlIIIll"
    ),
    val className: String = "lunar/dG/llIlIIIllIlllllIllIIIIIlI"
) : Module() {
    @Transient
    override val isEnabled = true
    fun getLevel(clazz: Class<*>, name: String): Any? = clazz.getField(levelmap[name] ?: "info")[null]

    companion object {
        @JvmStatic
        lateinit var instance: HandleNotifs
    }

    override fun generate(node: ClassNode): ClassTransform? {
        if (node.name != className) return null

        return ClassTransform(
            VisitorTransform(matchName("handleNotification")) { parent ->
                object : MethodVisitor(API, parent) {
                    init {
                        instance = this@HandleNotifs
                    }

                    override fun visitCode() {
                        super.visitCode()

                        // Actually implement this garbage
                        // Get "popup manager"
                        invokeMethod(InvocationType.STATIC, getLunarmainMethod)
                        invokeMethod(InvocationType.VIRTUAL, getPopupManagerMethod)

                        // Load level map onto the stack
                        invokeMethod(HandleNotifs::class.java.getMethod("getInstance"))

                        // Load class object onto stack
                        visitLdcInsn(Type.getObjectType(levelEnum))

                        // Load packet level onto the stack
                        loadVariable(1)
                        invokeMethod(
                            InvocationType.VIRTUAL,
                            "getLevel",
                            "()L$internalString;",
                            notifPacketClassname
                        )
                        invokeMethod(java.lang.String::class.java.getMethod("toLowerCase"))

                        // Get level enum constant
                        invokeMethod(HandleNotifs::getLevel)
                        visitTypeInsn(CHECKCAST, levelEnum)

                        // Load title onto stack
                        visitLdcInsn("§lServer Notification")

                        // Load message onto operand stack
                        loadVariable(1)
                        invokeMethod(
                            InvocationType.VIRTUAL,
                            "getMessage",
                            "()L$internalString;",
                            notifPacketClassname
                        )

                        // Send message
                        invokeMethod(InvocationType.VIRTUAL, sendPopupMethod)
                        pop()
                    }
                }
            }
        )
    }
}

@Serializable
data class ModName(
    val modName: String = "SolarTweaks",
    val shortHashField: FieldDescription = FieldDescription(
        "lIlllllIIIIlIIlllIlIlIIIl",
        "L$internalString;",
        "lunar/ax/llIllIlllIlIlIIlIllllIIIl",
        ACC_STATIC
    ),
    val className: String = "net/minecraft/client/ClientBrandRetriever"
) : Module(), TransformGenerator by matcherGenerator(ClassTransform(ImplementTransform(matchName("getClientModName")) {
    val solarVersion = "$modName v${Versioning.version}"

    val start = Label()
    val end = Label()
    visitTryCatchBlock(start, end, end, "java/lang/Throwable")

    visitLabel(start)
    getField(shortHashField, static = true)
    concat("(L$internalString;)L$internalString;", "Lunar Client \u0001/$solarVersion")
    returnMethod(ARETURN)

    visitLabel(end)
    dup()
    invokeMethod(Throwable::class.java.getMethod("printStackTrace"))
    visitLdcInsn(solarVersion)
    returnMethod(ARETURN)
}), matcher = ClassMatching.matchName(className)) {
    @Transient
    override val isEnabled: Boolean = true
}