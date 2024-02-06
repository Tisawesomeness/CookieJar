package com.tisawesomeness.cookiejar.mixin;

import net.minecraft.client.network.ServerAddress;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerAddress.class)
public interface ServerAddressAccessor {
    @Accessor("INVALID")
    static ServerAddress getInvalid() {
        throw new AssertionError("mixin died lol");
    }
}
