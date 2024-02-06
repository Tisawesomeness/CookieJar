package com.tisawesomeness.cookiejar.mixin;

import com.tisawesomeness.cookiejar.CookieJar;
import com.tisawesomeness.cookiejar.CookieJarConfig;
import com.tisawesomeness.cookiejar.ui.CookieScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientCommonNetworkHandler;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.network.packet.s2c.common.CookieRequestS2CPacket;
import net.minecraft.network.packet.s2c.common.ServerTransferS2CPacket;
import net.minecraft.network.packet.s2c.common.StoreCookieS2CPacket;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientCommonNetworkHandler.class)
public class ClientCommonNetworkHandlerMixin {

    @Inject(method = "onStoreCookie", at = @At("TAIL"), cancellable = true)
    private void onStoreCookie(StoreCookieS2CPacket packet, CallbackInfo ci) {
        if (CookieJar.shouldIgnoreCookieRequest()) {
            ci.cancel();
        } else {
            CookieJar.onStoreCookie(packet);
        }
    }

    @Inject(method = "onCookieRequest", at = @At("HEAD"), cancellable = true)
    private void onCookieRequest(CookieRequestS2CPacket packet, CallbackInfo ci) {
        if (CookieJarConfig.ignoreCookieRequests) {
            ci.cancel();
        }
    }

    @Inject(method = "onServerTransfer", at = @At("HEAD"), cancellable = true)
    private void onServerTransfer(ServerTransferS2CPacket packet, CallbackInfo ci) {
        if (CookieJar.shouldIgnoreTransfer(packet)) {
            ci.cancel();
        }
    }

    // Prevent crash when transferring to another server from singleplayer
    // by supplying a ServerInfo for a "singleplayer" server
    @Redirect(method = "onServerTransfer", at = @At(value = "FIELD", target = "Lnet/minecraft/client/network/ClientCommonNetworkHandler;serverInfo:Lnet/minecraft/client/network/ServerInfo;", opcode = Opcodes.GETFIELD))
    private ServerInfo onServerTransfer$serverInfo(ClientCommonNetworkHandler instance) {
        ServerInfo current = ((ClientCommonNetworkHandlerAccessor) instance).getServerInfo();
        if (current == null && CookieJarConfig.enableSingleplayerFix) {
            return CookieJar.SINGLEPLAYER_INFO;
        }
        return current;
    }

}
