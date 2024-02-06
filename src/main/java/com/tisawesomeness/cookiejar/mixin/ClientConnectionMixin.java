package com.tisawesomeness.cookiejar.mixin;

import com.tisawesomeness.cookiejar.CookieJar;
import io.netty.channel.ChannelFuture;
import net.minecraft.network.ClientConnection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

@Mixin(ClientConnection.class)
public class ClientConnectionMixin {
    @Inject(method = "connect(Ljava/net/InetSocketAddress;ZLnet/minecraft/network/ClientConnection;)Lio/netty/channel/ChannelFuture;", at = @At("HEAD"))
    private static void connect(InetSocketAddress address, boolean useEpoll, ClientConnection connection, CallbackInfoReturnable<ChannelFuture> cir) {
        CookieJar.updateConnection(connection);
    }
    @Inject(method = "connectLocal", at = @At("RETURN"))
    private static void connectLocal(SocketAddress address, CallbackInfoReturnable<ClientConnection> cir) {
        CookieJar.updateConnection(cir.getReturnValue());
    }
}
