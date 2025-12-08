package com.tisawesomeness.cookiejar.mixin;

import com.tisawesomeness.cookiejar.CookieJar;
import io.netty.channel.ChannelFuture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import net.minecraft.network.Connection;
import net.minecraft.server.network.EventLoopGroupHolder;

@Mixin(Connection.class)
public class ConnectionMixin {
    @Inject(method = "connect(Ljava/net/InetSocketAddress;Lnet/minecraft/server/network/EventLoopGroupHolder;Lnet/minecraft/network/Connection;)Lio/netty/channel/ChannelFuture;", at = @At("HEAD"))
    private static void connect(InetSocketAddress address, EventLoopGroupHolder backend, Connection connection, CallbackInfoReturnable<ChannelFuture> cir) {
        CookieJar.updateConnection(connection);
    }
    @Inject(method = "connectToLocalServer", at = @At("RETURN"))
    private static void connectLocal(SocketAddress address, CallbackInfoReturnable<Connection> cir) {
        CookieJar.updateConnection(cir.getReturnValue());
    }
}
