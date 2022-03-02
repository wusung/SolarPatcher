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

package com.grappenmaker.solarpatcher.modules

import com.grappenmaker.solarpatcher.Versioning
import com.grappenmaker.solarpatcher.asm.*
import com.grappenmaker.solarpatcher.asm.matching.ClassMatcher
import com.grappenmaker.solarpatcher.asm.matching.ClassMatching
import com.grappenmaker.solarpatcher.asm.matching.MethodMatcherData
import com.grappenmaker.solarpatcher.asm.matching.MethodMatching.matchAny
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
import com.grappenmaker.solarpatcher.config.Constants.packetClassname
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.objectweb.asm.*
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.tree.*
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

const val initMethodName = "init"

private val metadataMatcher: ClassMatcher = { node ->
    val metaTypes = listOf(
        "blogPosts",
        "modSettings",
        "clientSettings",
        "pinnedServers",
        "serverIntegration",
        "featureFlag",
        "knownServersHash",
        "store"
    )

    node.methods.find { it.name == initMethodName }
        ?.constants?.containsAll(metaTypes) == true
}

@Serializable
data class Metadata(
    val removeCalls: List<String> = listOf(
        "serverIntegration",
        "pinnedServers",
        "modSettings",
        "clientSettings",
        "blogPosts"
    ),
    override val isEnabled: Boolean = false
) : Module(),
    TransformGenerator by matcherGenerator(
        ClassTransform(
            VisitorTransform(matchName(initMethodName)) { parent ->
                object : MethodVisitor(API, parent) {
                    private var lastMetadata: String? = null

                    override fun visitLdcInsn(value: Any?) {
                        super.visitLdcInsn(value)
                        if (value is String) lastMetadata = value
                    }

                    override fun visitMethodInsn(
                        opcode: Int,
                        owner: String,
                        name: String,
                        descriptor: String,
                        isInterface: Boolean
                    ) {
                        if (
                            descriptor != "(L$internalString;L${getInternalName<Consumer<*>>()};)V"
                            || lastMetadata !in removeCalls
                        ) {
                            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)
                        }
                    }
                }
            }), matcher = metadataMatcher
    )

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

private const val serverRuleClass = "com/lunarclient/bukkitapi/nethandler/client/obj/ServerRule"
private const val logDetection = "No default value for Server Rule [\u0001] found!"

@Serializable
data class NoHitDelay(override val isEnabled: Boolean = false) : Module() {
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

@Serializable
data class FPSSpoof(
    val multiplier: Double = 2.0,
    override val isEnabled: Boolean = false
) : Module(), TransformGenerator by matcherGenerator(ClassTransform(InvokeAdviceTransform(
    matchAny(),
    MethodMatcherData("bridge\$getDebugFPS", "()I").asMatcher(),
    afterAdvice = {
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
)), matcher = { it.constants.contains("[1466 FPS]") })

@Serializable
data class CustomCommands(
    val commands: Map<String, TextCommand> = mapOf(
        "qb" to TextCommand("/play duels_bridge_duel"),
        "qbd" to TextCommand("/play duels_bridge_doubles"),
        "db" to TextCommand("/duel ", " bridge"),
        "bwp" to TextCommand("/play bedwars_practice")
    ),
    override val isEnabled: Boolean = false
) : Module() {
    init {
        // Yep this is a fake singleton
        // Might want to work on a better "storage" system
        instance = this
    }

    @Transient
    private val actualCommands: Map<String, Command> =
        commands + getCodeCommands()

    companion object {
        // Instance of this class, initialized at runtime
        @JvmStatic
        lateinit var instance: CustomCommands
            private set

        // Function that will handle an event (at runtime),
        // so that we don't have to implement it with bytecode
        @Suppress("Unused") // it is used obviously
        @JvmStatic
        fun handler(event: Any?) {
            if (event == null) return // Java sadness

            val accessor = CommandEventAccessor(event)
            val text = accessor.text
            if (!text.startsWith("/")) return

            val components = text.split(" ")
            instance.actualCommands[components.first().substring(1)]?.handle(accessor)
        }
    }

    override fun generate(node: ClassNode): ClassTransform? {
        val handleMethod = node.methods.find { it.constants.contains("EventBus [\u0001]: \u0001") } ?: return null
        val matcher = handleMethod.asDescription(node).asMatcher()

        return ClassTransform(visitors = listOf {
            AdviceClassVisitor(it, matcher, exitAdvice = {
                val end = Label()

                // Load event class name onto the stack
                loadVariable(1) // Parameter 0, variable 1
                invokeMethod(Object::class.java.getMethod("getClass"))
                invokeMethod(Class<*>::getName)

                // Load chat event class onto the stack
                getObject(RuntimeData::class)
                invokeMethod(RuntimeData::outgoingPacketEvent.getter)

                // Compare the strings
                invokeMethod(String::equals)

                // If they don't match, goodbye
                visitJumpInsn(IFEQ, end)

                // Call handler
                loadVariable(1)
                invokeMethod(
                    InvocationType.STATIC,
                    "handler",
                    "(Ljava/lang/Object;)V",
                    getInternalName<CustomCommands>()
                )

                // method end
                visitLabel(end)
            })
        }, shouldExpand = true)
    }
}

const val defaultClientID = 562286213059444737L
const val defaultIcon = "icon_07_11_2020"
const val defaultText = "Lunar Client"
const val defaultVersionText = "Playing Minecraft \u0001"

@Serializable
data class RPCUpdate(
    val clientID: Long = 920998351430901790,
    val icon: String = "logo",
    val iconText: String = "Solar Tweaks",
    val afkText: String = "AFK",
    val menuText: String = "In Menu",
    val singlePlayerText: String = "Playing Singleplayer",
    val versionText: String = "Minecraft \u0001",
    override val isEnabled: Boolean = true
) : Module() {
    override fun generate(node: ClassNode): ClassTransform? {
        val updateMethod = node.methods.find { it.constants.contains(defaultIcon) } ?: return null
        return ClassTransform(listOf(InvokeAdviceTransform(
            updateMethod.asDescription(node).asMatcher(),
            matchName("build"), // RPC builder
            beforeAdvice = {
                // There is now a RichPresence.Builder on the stack
                // Keep in mind that after dropping out of this advice
                // the builder should be on the stack
                val builderClass = "com/jagrosh/discordipc/entities/RichPresence\$Builder"
                val setState = {
                    invokeMethod(
                        InvocationType.VIRTUAL,
                        "setState",
                        "(L$internalString;)L$builderClass;",
                        builderClass
                    )
                }

                // Labels to jump to
                val wasNull = Label()
                val end = Label()

                val afk = Label()
                isWindowFocused()
                visitJumpInsn(IFNE, afk)
                visitLdcInsn(afkText)
                setState()
                visitJumpInsn(GOTO, end)
                visitLabel(afk)

                // Get the server data and check for nullity
                getServerData()
                dup()

                // If it is null, pop to stack and jump to end
                visitJumpInsn(IFNULL, wasNull)

                // If it was not null, get the server ip
                invokeMethod(
                    InvocationType.INTERFACE,
                    "bridge\$serverIP",
                    "()L$internalString;",
                    Type.getReturnType(RuntimeData.getServerDataMethod.descriptor).internalName
                )
                storeVariable(2) // Store the server ip
                remapServerIP { loadVariable(2) }
                concat("(L$internalString;)L$internalString;", "Playing on \u0001")

                // Set the "state"
                setState()

                // Jump back to original code
                visitJumpInsn(GOTO, end)

                // Handle nullity by popping the null off the stack
                visitLabel(wasNull)
                pop()
                visitLdcInsn(menuText)
                setState()
                visitLabel(end)

                // Continue setting the rpc (drops out of this advice)
            }
        )), listOf {
            replaceClassConstants(
                it, mapOf(
                    defaultClientID to clientID,
                    defaultIcon to icon,
                    defaultText to iconText,
                    defaultVersionText to versionText
                )
            )
        })
    }
}

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
    matcher = { it.constants.containsAll(setOf(" blocks", "range")) }
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
                    invokeMethod(Object::class.java.getMethod("getClass"))
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
data class ToggleSprintText(
    val replacements: Map<String, String> = Constants.ToggleSprint.defaultConfig,
    val method: MethodMatcherData = MethodMatcherData.CLINIT,
    override val isEnabled: Boolean = false,
) : Module(), TransformGenerator by matcherGenerator(
    ClassTransform(replacements.map { (from, to) -> TextTransform(method.asMatcher(), from, to) }),
    matcher = { it.constants.containsAll(replacements.keys) }
)

const val notifPacketClassname = "com/lunarclient/bukkitapi/nethandler/client/LCPacketNotification"

object HandleNotifications : Module() {
    override val isEnabled = true
    override fun generate(node: ClassNode): ClassTransform? {
        val method = node.methods.find {
            it.name == "handleNotification" && Type.getArgumentTypes(it.desc)
                .first().internalName == notifPacketClassname
        } ?: return null

        return ClassTransform(
            VisitorTransform(method.asDescription(node).asMatcher()) { parent ->
                object : MethodVisitor(API, parent) {
                    override fun visitCode() {
                        super.visitCode()
                        // Actually implement this garbage
                        // Get assets socket
                        getAssetsSocket()

                        // Create packet
                        val popupMethod = RuntimeData.sendPopupMethod
                        construct(
                            Type.getArgumentTypes(popupMethod.descriptor).first().internalName,
                            "(L$internalString;L$internalString;)V"
                        ) {
                            // Load title onto operand stack
                            loadVariable(1)
                            invokeMethod(
                                InvocationType.VIRTUAL,
                                "getLevel",
                                "()L$internalString;",
                                notifPacketClassname
                            )

                            concat("(L$internalString;)L$internalString;", "Server Notification - \u0001")

                            // Load message onto operand stack
                            loadVariable(1)
                            invokeMethod(
                                InvocationType.VIRTUAL,
                                "getMessage",
                                "()L$internalString;",
                                notifPacketClassname
                            )
                        }

                        // Fake send packet
                        invokeMethod(InvocationType.VIRTUAL, popupMethod)
                    }
                }
            }
        )
    }
}

const val modName: String = "SolarTweaks"

object ModName : Module(),
    TransformGenerator by matcherGenerator(ClassTransform(ImplementTransform(matchName("getClientModName")) {
        val solarVersion = "$modName v${Versioning.version}"

        // Add a try catch block, because class access might fail
        val start = Label()
        val end = Label()
        visitTryCatchBlock(start, end, end, "java/lang/Throwable")

        // try-start
        visitLabel(start)

        getObject(RuntimeData::class)
        invokeMethod(RuntimeData::version.getter)
        concat("(L$internalString;)L$internalString;", "Lunar Client \u0001/$solarVersion")
        returnMethod(ARETURN)

        // try-end
        visitLabel(end)

        // try-handler
        dup()
        invokeMethod(Throwable::class.java.getMethod("printStackTrace"))
        visitLdcInsn(solarVersion)
        returnMethod(ARETURN)
    }), matcher = ClassMatching.matchName("net/minecraft/client/ClientBrandRetriever")) {
    @Transient
    override val isEnabled: Boolean = true
}

@Serializable
data class FixPings(override val isEnabled: Boolean = false) : Module() {
    override fun generate(node: ClassNode): ClassTransform? {
        if (!node.constants.contains("chat_ping_sound")) return null

        val handleSoundMethod = node.methods.find {
            it.calls.any { c -> c.name == "bridge\$playSound" }
        } ?: return null
        val eventType = Type.getArgumentTypes(handleSoundMethod.desc).first()

        return ClassTransform(VisitorTransform(
            handleSoundMethod.asDescription(node).asMatcher()
        ) { parent ->
            object : MethodVisitor(API, parent) {
                override fun visitJumpInsn(opcode: Int, label: Label) {
                    super.visitJumpInsn(opcode, label)

                    if (opcode == IFNE) {
                        val end = Label()

                        loadVariable(1)
                        invokeMethod(InvocationType.VIRTUAL, "getType", "()I", eventType.internalName)
                        visitInsn(ICONST_1)
                        visitJumpInsn(IF_ICMPEQ, end)

                        returnMethod()
                        visitLabel(end)
                    }
                }
            }
        })
    }
}

@Serializable
data class LunarOptions(override val isEnabled: Boolean = false) : Module() {
    override fun generate(node: ClassNode): ClassTransform? {
        val method = node.methods.find { it.constants.contains("Lunar Options...") } ?: return null
        return ClassTransform(VisitorTransform(method.asDescription(node).asMatcher()) { parent ->
            object : MethodVisitor(API, parent) {
                override fun visitJumpInsn(opcode: Int, label: Label) {
                    if (opcode == IFNE) {
                        pop()
                        return
                    }

                    super.visitJumpInsn(opcode, label)
                }
            }
        })
    }
}

@Serializable
data class SupportOverlays(override val isEnabled: Boolean = false) : Module() {
    override fun generate(node: ClassNode): ClassTransform? {
        val method = node.methods.find {
            Type.getArgumentTypes(it.desc).firstOrNull()?.internalName == internalString
                    && it.constants.contains("assets/lunar/")
        } ?: return null

        return ClassTransform(ConstantValueTransform(method.asDescription(node).asMatcher(), true))
    }
}