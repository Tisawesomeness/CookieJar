package com.tisawesomeness.cookiejar.ui;

import com.tisawesomeness.cookiejar.CookieJar;
import com.tisawesomeness.cookiejar.CookieUtil;
import eu.midnightdust.lib.config.MidnightConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.ButtonTextures;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.*;
import net.minecraft.client.network.ClientCommonNetworkHandler;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.network.packet.c2s.common.CookieResponseC2SPacket;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Language;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

public class CookieScreen extends Screen {

    private static final int SCROLLER_WIDTH = 6;
    private static final int TEXTURE_SIZE = 16;
    private static final int PADDING = 4;
    private static final int ROW_HEIGHT = TEXTURE_SIZE + PADDING;
    private static final int KEY_WIDTH = 180;
    private static final int IMPORT_METHOD_WIDTH = 80;

    private static final Identifier CROSS_BUTTON = Identifier.ofVanilla("widget/cross_button");
    private static final Identifier CROSS_BUTTON_HIGHLIGHTED = Identifier.ofVanilla("widget/cross_button_highlighted");

    private final Screen parent;
    // reference to the same cookie map used in the network handler
    private final Map<Identifier, byte[]> cookies;

    // Row 1
    private ButtonWidget addButton;
    private TextFieldWidget keyWidget;
    private TextFieldWidget payloadWidget;
    private ButtonWidget transferButton;
    private ButtonWidget settingsButton;

    // Row 2
    private TextFieldWidget filterWidget;
    private ButtonWidget dataTypeButton;
    private ButtonWidget importButton;
    private ButtonWidget importMethodButton;
    private ButtonWidget exportButton;
    private TexturedButtonWidget clearButton;

    // Cookie list
    private CookieListWidget cookieWidget;
    // Minecraft's entry lists do not offer filtering, so keep a master list of all entries here
    // and update the widget with a filtered subset when the filter changes
    private final List<CookieListWidget.Entry> cookieEntries = new ArrayList<>();

    // Backing data with validation
    private @Nullable Identifier keyToAdd; // null if invalid or empty
    private byte[] payloadToAdd = new byte[0]; // last known good value
    private @Nullable String filter = null; // null if empty
    private DataType dataType = DataType.STRING;
    private ImportMethod importMethod = ImportMethod.MERGE;

    public CookieScreen(Screen parent, Map<Identifier, byte[]> cookies) {
        super(Text.translatable("gui.cookiejar.cookie_editor.title"));
        this.parent = parent;
        this.cookies = cookies;
    }

    @Override
    protected void init() {
        // init() is not a constructor, it may be called multiple times, so clearing is necessary
        cookieEntries.clear();
        // If client somehow opens screen without an active connection,
        // some buttons won't work and should be disabled
        boolean allCookieActionsSupported = CookieJar.getNetworkHandler() != null;

        // Row 1
        addButton = ButtonWidget.builder(Text.literal("+"), button -> addCookie())
                .dimensions(PADDING, PADDING, TEXTURE_SIZE, TEXTURE_SIZE)
                .tooltip(Tooltip.of(Text.translatable("gui.cookiejar.cookie_editor.add_cookie")))
                .build();
        addButton.active = false;

        keyWidget = new TextFieldWidget(
                textRenderer,
                PADDING + TEXTURE_SIZE + PADDING,
                PADDING,
                KEY_WIDTH,
                TEXTURE_SIZE,
                Text.translatable("gui.cookiejar.cookie_editor.key")
        );
        keyWidget.setPlaceholder(Text.translatable("gui.cookiejar.cookie_editor.key_placeholder"));
        keyWidget.setEditableColor(CookieJar.COLOR_SUGGESTION);
        keyWidget.setChangedListener(this::editKey);

        payloadWidget = new TextFieldWidget(
                textRenderer,
                PADDING + (TEXTURE_SIZE + PADDING + KEY_WIDTH) + PADDING,
                PADDING,
                width - (SCROLLER_WIDTH + PADDING + (TEXTURE_SIZE + PADDING) * 3 + PADDING * 2 + KEY_WIDTH),
                TEXTURE_SIZE,
                Text.translatable("gui.cookiejar.cookie_editor.payload")
        );
        // Payload placeholder set by setDataType()
        payloadWidget.setEditableColor(CookieJar.COLOR_SUGGESTION);
        payloadWidget.setChangedListener(payloadStr -> {
            setPayloadWidget(payloadWidget, payloadStr, payload -> payloadToAdd = payload);
        });

        transferButton = ButtonWidget.builder(Text.literal("➡"), button -> {
                    client.setScreen(new TransferScreen(this));
                })
                .dimensions(
                        width - (SCROLLER_WIDTH + (TEXTURE_SIZE + PADDING) * 2),
                        PADDING,
                        TEXTURE_SIZE,
                        TEXTURE_SIZE
                )
                .tooltip(Tooltip.of(Text.translatable("gui.cookiejar.cookie_editor.transfer")))
                .build();
        transferButton.active = allCookieActionsSupported;

        settingsButton = ButtonWidget.builder(Text.literal("\uD83D\uDD27"), button -> {
                    client.setScreen(MidnightConfig.getScreen(this, "cookiejar"));
                })
                .dimensions(
                        width - (SCROLLER_WIDTH + TEXTURE_SIZE + PADDING),
                        PADDING,
                        TEXTURE_SIZE,
                        TEXTURE_SIZE
                )
                .tooltip(Tooltip.of(Text.translatable("gui.cookiejar.cookie_editor.settings")))
                .build();

        addDrawableChild(addButton);
        addDrawableChild(keyWidget);
        addDrawableChild(payloadWidget);
        addDrawableChild(transferButton);
        addDrawableChild(settingsButton);

        // Row 2
        filterWidget = new TextFieldWidget(
                textRenderer,
                PADDING,
                PADDING + TEXTURE_SIZE + PADDING,
                KEY_WIDTH,
                TEXTURE_SIZE,
                Text.translatable("gui.cookiejar.cookie_editor.filter")
        );
        filterWidget.setPlaceholder(Text.translatable("gui.cookiejar.cookie_editor.filter_placeholder"));
        filterWidget.setEditableColor(CookieJar.COLOR_SUGGESTION);
        filterWidget.setChangedListener(this::editFilter);

        dataTypeButton = ButtonWidget.builder(Text.literal("S"), button -> cycleDataType())
                .dimensions(
                        PADDING + KEY_WIDTH + PADDING,
                        PADDING + TEXTURE_SIZE + PADDING,
                        TEXTURE_SIZE,
                        TEXTURE_SIZE
                )
                .build();
        // Data type set at end, after cookie widget initialized

        importButton = ButtonWidget.builder(Text.literal("\uD83D\uDCE4"), button -> importCookies())
                .dimensions(
                        width - (SCROLLER_WIDTH + (TEXTURE_SIZE + PADDING) * 3 + IMPORT_METHOD_WIDTH + PADDING),
                        PADDING + TEXTURE_SIZE + PADDING,
                        TEXTURE_SIZE,
                        TEXTURE_SIZE
                )
                .tooltip(Tooltip.of(Text.translatable("gui.cookiejar.cookie_editor.import")))
                .build();

        importMethodButton = ButtonWidget.builder(Text.literal(""), button -> cycleImportMethod())
                .dimensions(
                        width - (SCROLLER_WIDTH + (TEXTURE_SIZE + PADDING) * 2 + IMPORT_METHOD_WIDTH + PADDING),
                        PADDING + TEXTURE_SIZE + PADDING,
                        IMPORT_METHOD_WIDTH,
                        TEXTURE_SIZE
                )
                .build();
        setImportMethod(importMethod);

        exportButton = ButtonWidget.builder(Text.literal("\uD83D\uDCE5"), button -> exportCookies())
                .dimensions(
                        width - (SCROLLER_WIDTH + (TEXTURE_SIZE + PADDING) * 2),
                        PADDING + TEXTURE_SIZE + PADDING,
                        TEXTURE_SIZE,
                        TEXTURE_SIZE
                )
                .tooltip(Tooltip.of(Text.translatable("gui.cookiejar.cookie_editor.export")))
                .build();

        clearButton = new TexturedButtonWidget(
                width - (SCROLLER_WIDTH + TEXTURE_SIZE + PADDING),
                PADDING + TEXTURE_SIZE + PADDING,
                TEXTURE_SIZE,
                TEXTURE_SIZE,
                new ButtonTextures(CROSS_BUTTON, CROSS_BUTTON_HIGHLIGHTED),
                button -> clear(),
                Text.translatable("gui.cookiejar.cookie_editor.clear")
        );
        clearButton.setTooltip(Tooltip.of(Text.translatable("gui.cookiejar.cookie_editor.clear")));

        addDrawableChild(filterWidget);
        addDrawableChild(dataTypeButton);
        addDrawableChild(importButton);
        addDrawableChild(importMethodButton);
        addDrawableChild(exportButton);
        addDrawableChild(clearButton);

        // Cookie list
        cookieWidget = new CookieListWidget(client,
                width,
                height - (PADDING + (TEXTURE_SIZE + PADDING) * 2),
                PADDING + (TEXTURE_SIZE + PADDING) * 2,
                ROW_HEIGHT
        );
        populateEntriesFromCookies(allCookieActionsSupported);
        setDataType(dataType);
        cookieWidget.populateFilteredFromMasterEntries();

        addDrawableChild(cookieWidget);
    }

    private void populateEntriesFromCookies(boolean allCookieActionsSupported) {
        cookies.forEach((key, payload) -> cookieEntries.add(cookieWidget.newEntry(key, payload, allCookieActionsSupported)));
    }

    private void addCookie() {
        if (keyToAdd == null) {
            return;
        }
        cookies.put(keyToAdd, payloadToAdd);
        updateListWidgetWithCookie(keyToAdd);
    }

    private void editKey(String keyStr) {
        if (keyStr.isEmpty()) {
            keyWidget.setEditableColor(CookieJar.COLOR_SUGGESTION);
            keyToAdd = null;
            addButton.active = false;
            return;
        }
        Identifier newKey = Identifier.tryParse(keyStr);
        if (newKey == null) {
            keyWidget.setEditableColor(CookieJar.COLOR_INVALID);
            keyToAdd = null;
            addButton.active = false;
        } else {
            keyWidget.setEditableColor(CookieJar.COLOR_VALID);
            keyToAdd = newKey;
            addButton.active = true;
        }
    }

    private void setPayloadWidget(TextFieldWidget widget, String payloadStr, Consumer<byte[]> validPayloadConsumer) {
        Optional<byte[]> payloadOpt = dataType.toPayload(payloadStr);
        if (payloadOpt.isPresent()) {
            validPayloadConsumer.accept(payloadOpt.get());
            if (payloadStr.isEmpty()) {
                widget.setEditableColor(CookieJar.COLOR_SUGGESTION);
            } else {
                widget.setEditableColor(CookieJar.COLOR_VALID);
            }
        } else {
            widget.setEditableColor(CookieJar.COLOR_INVALID);
        }
    }

    private void editFilter(String filterStr) {
        if (filterStr.isEmpty()) {
            filterWidget.setEditableColor(CookieJar.COLOR_SUGGESTION);
            filter = null;
        } else {
            filterWidget.setEditableColor(CookieJar.COLOR_VALID);
            filter = filterStr;
        }
        cookieWidget.populateFilteredFromMasterEntries();
    }

    private void cycleDataType() {
        DataType newType = switch (dataType) {
            case STRING -> DataType.BYTE_ARRAY;
            case BYTE_ARRAY -> DataType.STRING;
        };
        setDataType(newType);
    }
    private void setDataType(DataType type) {
        dataType = type;
        dataTypeButton.setMessage(type.label);
        dataTypeButton.setTooltip(type.tooltip);
        payloadWidget.setText(type.toStringInput(payloadToAdd));
        payloadWidget.setPlaceholder(type.getPayloadPlaceholder());
        cookieWidget.children().forEach(CookieListWidget.Entry::updatePayloadFromDataType);
    }

    private void cycleImportMethod() {
        ImportMethod newMethod = switch (importMethod) {
            case ADD -> ImportMethod.MERGE;
            case MERGE -> ImportMethod.ADD;
        };
        setImportMethod(newMethod);
    }
    private void setImportMethod(ImportMethod method) {
        importMethod = method;
        importMethodButton.setMessage(method.label);
        importMethodButton.setTooltip(method.tooltip);
    }

    private void importCookies() {
        // Reminder: Do NOT pass user input into tinyfd
        String pathStr = TinyFileDialogs.tinyfd_openFileDialog((CharSequence) null, null, null, null, false);
        if (pathStr == null) {
            return;
        }
        NbtCompound nbt;
        try {
            // No cookie file should get anywhere close to 1G, but just in case...
            nbt = NbtIo.readCompressed(Paths.get(pathStr), NbtSizeTracker.of(CookieUtil.ONE_GIGABYTE));
        } catch (IOException e) {
            CookieJar.LOGGER.error("Failed to import cookies", e);
            return;
        }
        Map<Identifier, byte[]> imported = CookieUtil.fromNbt(nbt);
        switch (importMethod) {
            case ADD -> imported.forEach(cookies::putIfAbsent);
            case MERGE -> cookies.putAll(imported);
        }
        // Connection status could have changed since menu opened
        boolean allCookieActionsSupported = CookieJar.getNetworkHandler() != null;
        // Completely re-create list of entries
        cookieEntries.clear();
        populateEntriesFromCookies(allCookieActionsSupported);
        cookieWidget.populateFilteredFromMasterEntries();
    }

    private void exportCookies() {
        // Reminder: Do NOT pass user input into tinyfd
        String pathStr = TinyFileDialogs.tinyfd_saveFileDialog((CharSequence) null, null, null, null);
        if (pathStr == null) {
            return;
        }
        try {
            NbtIo.writeCompressed(CookieUtil.toNbt(cookies), Paths.get(pathStr));
        } catch (IOException e) {
            CookieJar.LOGGER.error("Failed to export cookies", e);
        }
    }

    private void clear() {
        cookies.clear();
        cookieEntries.clear();
        cookieWidget.clear();
    }

    /**
     * Called when cookie is set on client while screen is open.
     * Used to update the screen live.
     * @param key the key of the cookie
     */
    public void onStoreCookie(Identifier key) {
        CookieListWidget.Entry entry = cookieWidget.getEntry(key);
        // If user was editing the cookie that just got set, unselect it to prevent misinput
        if (entry != null && key.equals(entry.key)) {
            cookieWidget.setSelected(null);
        }
        updateListWidgetWithCookie(key);
    }

    // When a cookie is added to the cookie map using put(),
    // it could be a completely new cookie or modifying the payload of an existing cookie.
    private void updateListWidgetWithCookie(Identifier key) {
        byte[] payload = cookies.get(key);
        CookieListWidget.Entry existingEntry = cookieWidget.getEntry(key);
        // If the cookie is new, add it to the list
        if (existingEntry == null) {
            // Update master entry list
            boolean allCookieActionsSupported = CookieJar.getNetworkHandler() != null;
            CookieListWidget.Entry newEntry = cookieWidget.newEntry(key, payload, allCookieActionsSupported);
            cookieEntries.add(newEntry);
            // Only add to the viewable list if the cookie passes the filter
            if (newEntry.passesFilter()) {
                int searchIdx = Collections.binarySearch(cookieWidget.children(), newEntry, Comparator.comparing(c -> c.key));
                int insertionIdx = -searchIdx - 1;
                cookieWidget.children().add(insertionIdx, newEntry);
            }
        } else {
            // Cookie already exists, only need to update payload
            existingEntry.payload = payload;
            existingEntry.updatePayloadFromDataType();
        }
    }

    @Override
    public void tick() {
        super.tick();
        // Connection status could have changed since menu opened
        boolean allCookieActionsSupported = CookieJar.getNetworkHandler() != null;
        cookieWidget.children().forEach(c -> c.setSendButtonActive(allCookieActionsSupported));
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }

    private class CookieListWidget extends ElementListWidget<CookieListWidget.Entry> {

        public CookieListWidget(MinecraftClient client, int width, int height, int y, int itemHeight) {
            super(client, width, height, y, itemHeight);
        }

        @Override
        public int getRowWidth() {
            return width; // Fixes scrollbar appearing in the middle of the screen
        }

        public Entry newEntry(Identifier key, byte[] payload, boolean allCookieActionsSupported) {
            return new Entry(key, payload, allCookieActionsSupported);
        }

        public @Nullable Entry getEntry(Identifier key) {
            return children().stream()
                    .filter(e -> e.key.equals(key))
                    .findFirst()
                    .orElse(null);
        }

        public void populateFilteredFromMasterEntries() {
            clear(); // Prevent duplicate or glitched entries
            cookieEntries.stream()
                    .filter(Entry::passesFilter)
                    .sorted(Comparator.comparing(en -> en.key)) // Map isn't sorted by default
                    .forEach(this::addEntry);
        }

        // Widens access
        public void clear() {
            clearEntries();
        }

        @Override
        protected int getScrollbarX() {
            return width - SCROLLER_WIDTH;
        }

        private class Entry extends ElementListWidget.Entry<Entry> {

            // Data updated when user types, last known good value
            private Identifier key;
            private byte[] payload;

            private final List<ClickableWidget> children;
            private final TexturedButtonWidget deleteButton;
            private final ButtonWidget sendButton;
            private final TextFieldWidget keyWidget;
            private final ButtonWidget copyButton;
            private final TextFieldWidget payloadWidget;

            public Entry(Identifier key, byte[] payload, boolean allCookieActionsSupported) {
                this.key = key;
                this.payload = payload;

                deleteButton = new TexturedButtonWidget(
                        PADDING, 0, TEXTURE_SIZE, TEXTURE_SIZE,
                        new ButtonTextures(CROSS_BUTTON, CROSS_BUTTON_HIGHLIGHTED),
                        button -> deleteCookie(),
                        Text.translatable("gui.cookiejar.cookie_editor.delete")
                );
                deleteButton.setTooltip(Tooltip.of(Text.translatable("gui.cookiejar.cookie_editor.delete")));

                sendButton = ButtonWidget.builder(Text.literal("\uD83D\uDCE8"), button -> sendCookie())
                        .dimensions(PADDING + TEXTURE_SIZE + PADDING, 0, TEXTURE_SIZE, TEXTURE_SIZE)
                        .tooltip(Tooltip.of(Text.translatable("gui.cookiejar.cookie_editor.send")))
                        .build();
                sendButton.active = allCookieActionsSupported;

                keyWidget = new TextFieldWidget(
                        textRenderer,
                        (PADDING + TEXTURE_SIZE) * 2 + PADDING,
                        0,
                        KEY_WIDTH,
                        TEXTURE_SIZE,
                        Text.translatable("gui.cookiejar.cookie_editor.key")
                );
                keyWidget.setText(key.toString());
                keyWidget.setChangedListener(this::editKey);

                copyButton = ButtonWidget.builder(Text.literal("\uD83D\uDCCB"), button -> copyPayload())
                        .dimensions((PADDING + TEXTURE_SIZE) * 2 + PADDING + KEY_WIDTH + PADDING, 0, TEXTURE_SIZE, TEXTURE_SIZE)
                        .tooltip(Tooltip.of(Text.translatable("gui.cookiejar.cookie_editor.copy_payload")))
                        .build();

                payloadWidget = new TextFieldWidget(
                        textRenderer,
                        (PADDING + TEXTURE_SIZE) * 3 + PADDING + KEY_WIDTH + PADDING,
                        0,
                        width - ((SCROLLER_WIDTH + (PADDING + TEXTURE_SIZE) * 3 + PADDING + KEY_WIDTH + PADDING) + PADDING),
                        TEXTURE_SIZE,
                        Text.translatable("gui.cookiejar.cookie_editor.payload")
                );
                updatePayloadFromDataType();
                payloadWidget.setChangedListener(this::editPayload);

                children = List.of(deleteButton, sendButton, keyWidget, copyButton, payloadWidget);
            }

            public void setSendButtonActive(boolean active) {
                sendButton.active = active;
                if (active) {
                    sendButton.setTooltip(Tooltip.of(Text.translatable("gui.cookiejar.cookie_editor.send")));
                } else {
                    sendButton.setTooltip(Tooltip.of(Text.translatable("gui.cookiejar.cookie_editor.send_disabled")));
                }
            }

            public void updatePayloadFromDataType() {
                payloadWidget.setText(dataType.toStringInput(payload));
            }

            private void deleteCookie() {
                cookies.remove(key);
                cookieEntries.remove(this);
                CookieListWidget.this.removeEntry(this);
            }

            private void editKey(String keyStr) {
                Identifier newKey = Identifier.tryParse(keyStr);
                // If key is unchanged, do nothing to prevent it being marked as invalid
                // due to the cookies.containsKey() check
                if (key.equals(newKey)) {
                    return;
                }
                // Show key as invalid if it already exists, two identical keys cannot exist
                if (newKey == null || cookies.containsKey(newKey)) {
                    keyWidget.setEditableColor(CookieJar.COLOR_INVALID);
                } else {
                    keyWidget.setEditableColor(CookieJar.COLOR_VALID);
                    cookies.remove(key);
                    cookies.put(newKey, payload);
                    key = newKey;
                }
            }

            private void sendCookie() {
                ClientCommonNetworkHandler handler = CookieJar.getNetworkHandler();
                if (handler == null) {
                    return;
                }
                CookieResponseC2SPacket packet = new CookieResponseC2SPacket(key, payload);
                handler.sendPacket(packet);
            }

            private void editPayload(String payloadStr) {
                setPayloadWidget(payloadWidget, payloadStr, payload -> {
                    this.payload = payload;
                    cookies.put(key, payload);
                });
            }

            private void copyPayload() {
                client.keyboard.setClipboard(dataType.toStringInput(payload));
            }

            public boolean passesFilter() {
                return filter == null || key.toString().toLowerCase(Locale.ROOT).contains(filter.toLowerCase(Locale.ROOT));
            }

            @Override
            public List<? extends Selectable> selectableChildren() {
                return children;
            }

            @Override
            public List<? extends Element> children() {
                return children;
            }

            @Override
            public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
                children.forEach(child -> {
                    child.setY(y);
                    child.render(context, mouseX, mouseY, tickDelta);
                });
            }
        }

    }

    private enum DataType {
        /** UTF-8 string, always valid */
        STRING(
                Text.literal("S").withColor(0xFFFFFF),
                Tooltip.of(Text.translatable("gui.cookiejar.cookie_editor.string_data")),
                payload -> new String(payload, StandardCharsets.UTF_8),
                input -> Optional.of(input.getBytes(StandardCharsets.UTF_8))
        ),
        /** Raw bytes, edited in hex form */
        BYTE_ARRAY(
                Text.literal("B").withColor(0xBB833A),
                Tooltip.of(Text.translatable("gui.cookiejar.cookie_editor.byte_data")),
                Hex::encodeHexString,
                input -> {
                    try {
                        return Optional.of(Hex.decodeHex(input));
                    } catch (DecoderException e) {
                        return Optional.empty();
                    }
                }
        );

        public final Text label;
        public final Tooltip tooltip;
        // Convert from raw payload to string displayed in text box
        private final Function<byte[], String> toDisplayFunc;
        // Convert from string displayed in text box to raw payload, or empty if invalid
        private final Function<String, Optional<byte[]>> toPayloadFunc;

        DataType(Text label, Tooltip tooltip, Function<byte[], String> toDisplayFunc, Function<String, Optional<byte[]>> toPayloadFunc) {
            this.label = label;
            this.tooltip = tooltip;
            this.toDisplayFunc = toDisplayFunc;
            this.toPayloadFunc = toPayloadFunc;
        }

        /**
         * Converts a cookie payload to a user-editable string.
         * @param payload payload
         * @return string
         */
        public String toStringInput(byte[] payload) {
            return toDisplayFunc.apply(payload);
        }
        /**
         * Tries to convert user input to a cookie payload.
         * @param input string
         * @return the parsed cookie payload, or empty if invalid
         */
        public Optional<byte[]> toPayload(String input) {
            return toPayloadFunc.apply(input);
        }

        /**
         * Converts the payload string from the lang file (defined in STRING data type) to the
         * string in the current data type that produces the same payload.
         * @return placeholder to put in the data text box
         */
        public Text getPayloadPlaceholder() {
            if (this == DataType.STRING) {
                return Text.translatable("gui.cookiejar.cookie_editor.payload_placeholder");
            }
            String localized = Language.getInstance().get("gui.cookiejar.cookie_editor.payload_placeholder");
            byte[] intermediatePayload = DataType.STRING.toPayload(localized).orElseThrow();
            String placeholder = toStringInput(intermediatePayload);
            return Text.literal(placeholder);
        }
    }

    private enum ImportMethod {
        /** Strictly adds cookies, no updating */
        ADD(
                Text.translatable("gui.cookiejar.cookie_editor.import_add"),
                Tooltip.of(Text.translatable("gui.cookiejar.cookie_editor.import_add_description"))
        ),
        /** Merges all cookies, overwriting existing ones */
        MERGE(
                Text.translatable("gui.cookiejar.cookie_editor.import_merge"),
                Tooltip.of(Text.translatable("gui.cookiejar.cookie_editor.import_merge_description"))
        );

        public final Text label;
        public final Tooltip tooltip;

        ImportMethod(Text label, Tooltip tooltip) {
            this.label = label;
            this.tooltip = tooltip;
        }
    }

}
