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

package com.grappenmaker.solarpatcher.util.generation

import com.grappenmaker.solarpatcher.asm.FieldDescription
import com.grappenmaker.solarpatcher.asm.asDescription
import com.grappenmaker.solarpatcher.asm.isInterface
import com.grappenmaker.solarpatcher.asm.method.InvocationType
import com.grappenmaker.solarpatcher.asm.method.asDescription
import com.grappenmaker.solarpatcher.asm.util.*
import com.grappenmaker.solarpatcher.util.LunarClassLoader
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import java.io.File
import java.util.*
import java.util.function.LongConsumer

private const val bridgePrefix = "bridge\$"

// This file is used to generate classes on runtime for bridge types
// Simply pass in a ClassNode to this helper function, and a class will be generated
// based on the specified interface
internal inline fun <reified T : Any> bridgeGeneration(bridgeNode: ClassNode): Class<*> {
    val interfaceType = T::class.java
    if (!interfaceType.isInterface) error("Type specified for bridge generation is not an interface!")
    if (!bridgeNode.isInterface) error("ClassNode specified for bridge generation is not an interface!")

    return generateBridgeBinding(interfaceType, bridgeNode)
}

// This function basically generates a binding with a receiver between a bridge type
// and an interface type that the user specified
private fun generateBridgeBinding(interfaceType: Class<*>, bridgeNode: ClassNode): Class<*> {
    // Map all the interface methods to methods of the classnode, and start generating
    val bridgeMethods = interfaceType.declaredMethods.associateWith { m ->
        bridgeNode.methods.find { it.name == "$bridgePrefix${m.name}" }
            ?: error("Bridge method ${m.name} was not found in type ${bridgeNode.name}!")
    }

    val owner = "${GeneratedCode.prefix}/binding/${interfaceType.simpleName}"
    return generateClass(
        owner,
        interfaces = arrayOf(interfaceType.internalName),
        createConstructor = false
    ) {
        // Make a field containing the receiver
        val receiverField = FieldDescription("\$RECEIVER", "L${bridgeNode.name};", owner, ACC_PRIVATE or ACC_FINAL)
        visitField(receiverField.access, receiverField.name, receiverField.descriptor, null, null)

        // Make a constructor
        with(visitMethod(ACC_PUBLIC, "<init>", "(L${bridgeNode.name};)V", null, null)) {
            visitCode()
            loadVariable(0)
            visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)

            loadVariable(0)
            loadVariable(1)
            setField(receiverField)

            returnMethod()
            visitMaxs(-1, -1)
            visitEnd()
        }

        // Make the getReceiver method
        with(visitMethod(ACC_PUBLIC, "getReceiver", "()Ljava/lang/Object;", null, null)) {
            visitCode()
            loadVariable(0)
            getField(receiverField)
            returnMethod(ARETURN)
            visitMaxs(-1, -1)
            visitEnd()
        }

        // Make all bridge methods
        for ((method, node) in bridgeMethods) {
            val (name, descriptor) = method.asDescription()
            val parameters = Type.getArgumentTypes(descriptor)
            val originalMethodType = Type.getMethodType(node.desc)
            val originalParameters = originalMethodType.argumentTypes

            with(visitMethod(ACC_PUBLIC, name, descriptor, null, null)) {
                visitCode()
                loadVariable(0)
                getField(receiverField)
                parameters.forEachIndexed { idx, param ->
                    val paramIndex = idx + 1
                    when (param.sort) {
                        Type.VOID -> error("Illegal void parameter")
                        Type.BOOLEAN, Type.BYTE, Type.SHORT, Type.CHAR, Type.INT -> loadVariable(paramIndex, ILOAD)
                        Type.FLOAT -> loadVariable(paramIndex, FLOAD)
                        Type.DOUBLE -> loadVariable(paramIndex, DLOAD)
                        Type.LONG -> loadVariable(paramIndex, LLOAD)
                        else -> {
                            loadVariable(paramIndex)
                            val type = originalParameters[idx].internalName
                            visitTypeInsn(CHECKCAST, type)
                        }
                    }
                }

                invokeMethod(InvocationType.INTERFACE, node.asDescription(bridgeNode))
                returnMethod(
                    when (originalMethodType.returnType.sort) {
                        Type.BOOLEAN, Type.BYTE, Type.SHORT, Type.CHAR, Type.INT -> IRETURN
                        Type.FLOAT -> FRETURN
                        Type.DOUBLE -> DRETURN
                        Type.VOID -> RETURN
                        Type.LONG -> LRETURN
                        else -> ARETURN
                    }
                )

                visitMaxs(-1, -1)
                visitEnd()
            }
        }
    }
}

// Wrapped inside a container, so we can have "statefulness" and laziness at the same time
class BindingContainer(val type: Class<*>, provider: () -> Class<*>) {
    val bindingClass by lazy(provider)
}

object Bindings {
    private val bridges = mutableListOf<ClassNode>()
    val bindings = buildList {
        binding<ClientBridge>()
        binding<SessionBridge>()
        binding<LocalPlayerBridge>()
        binding<SkinRenderableBridge>()
        binding<ServerDataBridge>()
        binding<ServerPlayerBridge>()
        binding<PlayerCapabilitiesBridge>()
        binding<GameSettingsBridge>()
        binding<KeyBindBridge>()
    }

    private inline fun <reified T : Any> MutableList<BindingContainer>.binding() {
        val interfaceType = T::class.java
        add(BindingContainer(interfaceType) {
            bridgeGeneration<T>(bridges.find { node ->
                node.methods.map { it.name }.containsAll(
                    interfaceType.declaredMethods.map { "$bridgePrefix${it.name}" }
                )
            } ?: error("Bridge for type $interfaceType has not been loaded yet!"))
        })
    }

    inline fun <reified T : Any> getBinding() = bindings.find { it.type == T::class.java }?.bindingClass

    fun onBridgeLoad(node: ClassNode) {
        println("Loaded ${node.name} (${node.methods.first().name})")
        bridges.add(node)
    }
}

// Utility to convert an object to a bridge binding implementation
inline fun <reified T : Any> bindBridge(instance: Any) =
    (Bindings.getBinding<T>() ?: error("No binding found for the given type!"))
        .constructors.first().newInstance(instance) as T

// Interface to trace back to the original receiver value
// Make a Bridge interface extend this interface to be able to get the receiver
interface BridgeBinding {
    fun getReceiver(): Any
}

inline fun <reified T : Any> BridgeBinding.rebind() = bindBridge<T>(getReceiver())

// All (implemented) lunar client bridge bindings
interface ClientBridge {
    fun displayWidth(): Int
    fun displayHeight(): Int
    fun getCurrentScreen(): Any?
    fun getCurrentServerData(): Any?
    fun getDebugFPS(): Int
    fun getEffectRenderer(): Any?
    fun getEntityRenderer(): Any?
    fun getFontRenderer(): Any?
    fun getFramebuffer(): Any?
    fun getGameSettings(): Any
    fun getGuiIngame(): Any?
    fun getGuiScale(): Int
    fun getItemRenderer(): Any?
    fun getMcDataDir(): File
    fun getMcDefaultResourcePack(): Any?
    fun getNetHandler(): Any
    fun getObjectMouseOver(): Any?
    fun getPlayer(): Any?
    fun getPlayerController(): Any?
    fun getPointedEntity(): Optional<Any>
    fun getProfileProperties(): Any?
    fun getRenderGlobal(): Any
    fun getRenderItem(): Any
    fun getRenderManager(): Any
    fun getRenderViewEntity(): Any?
    fun getResourceManager(): Any
    fun getSelectedResourcePack(): Any?
    fun getSession(): Any?
    fun getSessionService(): Any
    fun getSkinManager(): Any
    fun getSoundHandler(): Any
    fun getSystemTime(): Long // equivalent to System.currentTimeMillis()
    fun getTextureManager(): Any
    fun getTextureMap(): Any
    fun getTimer(): Any
    fun getWorld(): Any?
    fun hasInGameFocus(): Boolean
    fun isConnectedToRealms(): Boolean
    fun isDisplayActive(): Boolean
    fun isDisplayCreated(): Boolean
    fun isFullScreen(): Boolean
    fun isGamePaused(): Boolean
    fun isWindowFocused(): Boolean
    fun lastServerData(): Any?
    fun loadWorld(world: Any)
    fun refreshResources()
    fun repeatEventsEnabled(): Boolean
    fun setDisplayTitle(title: String)
    fun setPlayer(player: Any)
    fun setRepeatEventsEnabled(enabled: Boolean)
    fun setSession(session: Any)
    fun shutdownMinecraftApplet()
    fun submit(runnable: Runnable)
    fun toggleFullscreen()
    fun unicode(): Boolean
    fun updateDisplay()
    fun xrayBlocks(): Set<Any>
}

fun ClientBridge.getBoundServerData() = getCurrentServerData()?.let { bindBridge<ServerDataBridge>(it) }
fun ClientBridge.getBoundSession() = getSession()?.let { bindBridge<SessionBridge>(it) }
fun ClientBridge.getBoundPlayer() = getPlayer()?.let { bindBridge<LocalPlayerBridge>(it) }
fun ClientBridge.getBoundSettings() = bindBridge<GameSettingsBridge>(getGameSettings())

interface SessionBridge {
    fun getPlayerID(): String
    fun getUsername(): String
    fun getToken(): String
    fun getProfile(): Any // GameProfile
}

interface ServerDataBridge {
    fun serverIP(): String
    fun getPingToServer(): Long
    fun getServerName(): String
    fun getBase64Icon(): String
    fun getPingCallback(): LongConsumer
    fun setPingCallback(callback: LongConsumer)
}

interface LocalPlayerBridge : BridgeBinding {
    fun getClientBrand(): Optional<String>
    fun sendChatMessage(message: String)
    fun isSprinting(): Boolean
    fun setSprinting(sprinting: Boolean)
    fun isRidingHorse(): Boolean
    fun playPortalSound()
    fun sendRidingJumpPacket()
    fun getSprintingTicksLeft(): Int
    fun setSprintingTicksLeft(ticks: Int)
    fun getSprintToggleTimer(): Int
    fun setSprintToggleTimer(timer: Int)
    fun getPrevTimeInPortal(): Float
    fun setPrevTimeInPortal(time: Float)
    fun getTimeInPortal(): Float
    fun setTimeInPortal(time: Float)
    fun setInPortal(inPortal: Boolean)
    fun getTimeUntilPortal(): Int
    fun setTimeUntilPortal(time: Int)
    fun sendPlayerAbilities()
    fun getHorseJumpPowerCounter(): Int
    fun setHorseJumpPowerCounter(powerCounter: Int)
    fun getHorseJumpPower(): Float
    fun setHorseJumpPower(power: Float)
    fun getOpenContainer(): Any?
    fun getSendQueue(): Any?
}

fun LocalPlayerBridge.asSkinRenderable() = rebind<SkinRenderableBridge>()
fun LocalPlayerBridge.asServerPlayer() = rebind<ServerPlayerBridge>()

interface SkinRenderableBridge {
    fun setLocationOfCape(location: Any?)
    fun setLocationOfCapeOverride(locationOverride: Any?)
    fun getSkinType(): String
    fun loadAndGetRealSkinType(): Optional<String>
    fun getLocationSkin(): Any?
    fun getSwingProgress(unknown: Float): Float
    fun setSkinLocationOverride(location: Any?, type: String)
    fun getLocationSkinDefault(): Any
    fun isSkinTextureUploaded(): Boolean
    fun isModelPartShown(part: Any): Boolean
}

fun SkinRenderableBridge.asServerPlayer() = bindBridge<ServerPlayerBridge>(this)

interface ServerPlayerBridge {
    fun getGameProfile(): Any? // From authlib
    fun getPlayerCapabilities(): Any
    fun isSpectator(): Boolean
    fun addChatMessage(message: Any?) // Can be retrieved using chatutility if you want to
    fun isBlocking(): Boolean
    fun getInventory(): Any
    fun getCurrentEquippedItem(): Any?
    fun isSprinting(): Boolean
    fun getName(): String
    fun getFoodStats(): Any
    fun getHeldItem(): Any?
    fun onEnchantmentCritical(event: Any)
    fun preparePlayerToSpawn()
    fun getArmor(slot: Int): Any?
    fun getItemInUseCount(): Int
    fun getBedOrientationInDegrees(): Float
    fun isUsingItem(): Boolean
    fun setFlyToggleTimer(timer: Int)
    fun getMovementSpeedAttribute(): Double
    fun getItemInUse(): Optional<Any>
    fun getItemInUseDuration(): Int
}

fun ServerPlayerBridge.getBoundCapabilities() = bindBridge<PlayerCapabilitiesBridge>(getPlayerCapabilities())

interface PlayerCapabilitiesBridge {
    fun isFlying(): Boolean
    fun setFlying(flying: Boolean)
    fun isCreativeMode(): Boolean
    fun getFlySpeed(): Float
    fun setFlySpeed(speed: Float)
    fun getWalkSpeed(): Float
    fun isAllowFlying(): Boolean
}

interface GameSettingsBridge {
    fun getThirdPersonView(): Int
    fun setThirdPersonView(view: Int)
    fun getScreenshotKey(): Any?
    fun keyBindForward(): Any?
    fun keyBindLeft(): Any?
    fun keyBindBack(): Any?
    fun keyBindRight(): Any?
    fun keyBindJump(): Any?
    fun keyBindAttack(): Any?
    fun keyBindUseItem(): Any?
    fun keyBindSprint(): Any?
    fun keyBindSneak(): Any?
    fun keyBindTogglePerspective(): Any?
    fun getKeyBindings(): Array<Any>
    fun isFancyGraphics(): Boolean
    fun getRenderDistance(): Int
    fun setGamma(gamma: Float)
    fun setGammaOverride(override: Float)
    fun removeGammaOverride()
    fun showDebugInfo(): Boolean
    fun getModelParts(): Set<Any>
    fun isHideGui(): Boolean
    fun getChatScale(): Float
    fun setOptionFloatValue(option: Int, value: Float)
    fun setFancyGraphics(fancy: Boolean)
    fun setKeyBindState(binding: Any, pressed: Boolean)
    fun unpressAllKeys()
    fun setSmoothCamera(smooth: Boolean)
    fun updateVSync()
    fun getZoomKey(): Optional<Any>
    fun getFrameRateLimit(): Int
    fun setFrameRateLimit(limit: Int)
    fun setVBO(useVBO: Boolean)
}

interface KeyBindBridge {
    fun getKey(): Any
    fun setKey(key: Any)
    fun isKeyDown(): Boolean
    fun getKeyName(): String
    fun getKeyDescription(): String
    fun setKeyBindState(pressed: Boolean)
    fun getClashesWith(): List<Any>
    fun getCategory(): String
}