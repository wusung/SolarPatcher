package com.grappenmaker.solarpatcher.config

import com.grappenmaker.solarpatcher.*
import kotlinx.serialization.Serializable

@Serializable
data class Configuration(
    val enableAll: Boolean = false,
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
    val uncapReach: UncapReach = UncapReach()
) {
    fun getModules() = arrayOf(
        nickhider,
        fps,
        cps,
        autoGG,
        levelHead,
        freelook,
        pinnedServers,
        blogPosts,
        modpacketRemoval,
        mantleIntegration,
        windowName,
        noHitDelay,
        fpsSpoof,
        cpsSpoof,
        customCommands,
        rpcUpdate,
        websocketPrivacy,
        uncapReach
    )
}