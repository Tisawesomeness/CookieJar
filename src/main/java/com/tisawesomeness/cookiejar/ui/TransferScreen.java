package com.tisawesomeness.cookiejar.ui;

import com.tisawesomeness.cookiejar.CookieJar;
import com.tisawesomeness.cookiejar.mixin.ServerAddressAccessor;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.ClientCommonNetworkHandler;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.network.packet.s2c.common.ServerTransferS2CPacket;
import net.minecraft.text.Text;

@Environment(EnvType.CLIENT)
public class TransferScreen extends Screen {

    private static final int SERVER_WIDTH = 250;
    private static final int CONNECT_WIDTH = 80;
    private static final int HEIGHT = 16;
    private static final int PADDING = 4;

    private final Screen parent;
    private ServerAddress serverAddress;
    private ServerTransferS2CPacket outgoingPacket;

    private TextFieldWidget serverField;
    private ButtonWidget connectButton;

    protected TransferScreen(Screen parent) {
        super(Text.translatable("gui.cookiejar.transfer_screen.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        serverField = new TextFieldWidget(
                textRenderer,
                width / 2 - SERVER_WIDTH / 2,
                height / 2 - HEIGHT - PADDING / 2,
                SERVER_WIDTH,
                HEIGHT,
                Text.translatable("gui.cookiejar.transfer_screen.server_address")
        );
        serverField.setMaxLength(Integer.MAX_VALUE);
        serverField.setPlaceholder(Text.translatable("gui.cookiejar.transfer_screen.server_address_placeholder"));
        serverField.setEditableColor(CookieJar.COLOR_SUGGESTION);
        serverField.setChangedListener(this::editServer);

        connectButton = ButtonWidget.builder(Text.translatable("gui.cookiejar.transfer_screen.connect"), button -> connect())
                .dimensions(
                        width / 2 - CONNECT_WIDTH / 2,
                        height / 2 + HEIGHT + PADDING / 2,
                        CONNECT_WIDTH,
                        HEIGHT)
                .build();

        addDrawableChild(serverField);
        addDrawableChild(connectButton);
    }

    private void editServer(String serverStr) {
        if (serverStr.isEmpty()) {
            serverField.setEditableColor(CookieJar.COLOR_SUGGESTION);
            serverAddress = null;
            connectButton.active = false;
            return;
        }
        ServerAddress newAddress = ServerAddress.parse(serverStr);
        if (ServerAddressAccessor.getInvalid().equals(newAddress)) {
            serverField.setEditableColor(CookieJar.COLOR_INVALID);
            serverAddress = null;
            connectButton.active = false;
        } else {
            serverField.setEditableColor(CookieJar.COLOR_VALID);
            serverAddress = newAddress;
            connectButton.active = true;
        }
    }

    private void connect() {
        assert client != null;
        if (serverField.getText().isEmpty()) {
            return;
        }
        ClientCommonNetworkHandler handler = client.getNetworkHandler();
        if (handler == null) {
            return;
        }
        // save packet so the mixin can detect whether a received packet was from this screen
        // and let the packet through even when configured to ignore transfers
        outgoingPacket = new ServerTransferS2CPacket(serverAddress.getAddress(), serverAddress.getPort());
        handler.onServerTransfer(outgoingPacket);
        // check happens in `onServerTransfer` mixin so packet isn't needed anymore
        this.outgoingPacket = null;
    }

    @Override
    public void close() {
        assert client != null;
        client.setScreen(parent);
    }

    public boolean isSamePacket(ServerTransferS2CPacket packet) {
        return outgoingPacket == packet;
    }

}
