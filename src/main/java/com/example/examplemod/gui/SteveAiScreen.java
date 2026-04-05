/**
 * File: SteveAiScreen.java
 *
 * Main intent:
 * Defines SteveAiScreen functionality for the NPCoo mod codebase.
 *
 * Methods (what each does, with input/output):
 * 1) {@code label(...)}:
 *    Purpose: Returns the display label text for one screen tab.
 *    Input: none.
 *    Output: String.
 * 2) {@code scrollChatToBottom(...)}:
 *    Purpose: Jumps the chat view to the newest available lines.
 *    Input: none.
 *    Output: void.
 * 3) {@code SteveAiScreen(...)}:
 *    Purpose: Constructs the custom SteveAI merchant and chat screen.
 *    Input: SteveAiMenu menu, Inventory playerInventory, Component title.
 *    Output: none (constructor).
 * 4) {@code init(...)}:
 *    Purpose: Initializes tabs, chat input, and the server-side chat session state.
 *    Input: none.
 *    Output: void.
 * 5) {@code render(...)}:
 *    Purpose: Renders either the trade UI or the custom panel, then overlays tabs and tooltips.
 *    Input: GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick.
 *    Output: void.
 * 6) {@code renderLabels(...)}:
 *    Purpose: Delegates label rendering to the base merchant screen when the trade tab is active.
 *    Input: GuiGraphics guiGraphics, int mouseX, int mouseY.
 *    Output: void.
 * 7) {@code drawMainPanel(...)}:
 *    Purpose: Draws the framed custom panel used by the non-trade tabs.
 *    Input: GuiGraphics guiGraphics.
 *    Output: void.
 * 8) {@code drawCustomLabels(...)}:
 *    Purpose: Draws the title text for the custom SteveAI panel.
 *    Input: GuiGraphics guiGraphics.
 *    Output: void.
 * 9) {@code drawTabs(...)}:
 *    Purpose: Draws all top-level tabs for the SteveAI screen.
 *    Input: GuiGraphics guiGraphics.
 *    Output: void.
 * 10) {@code drawTab(...)}:
 *    Purpose: Draws one tab with the correct selected or unselected styling.
 *    Input: GuiGraphics guiGraphics, int x, int y, int width, Tab tab.
 *    Output: void.
 * 11) {@code drawPlaceholderContent(...)}:
 *    Purpose: Draws either the chat tab contents or placeholder text for unfinished tabs.
 *    Input: GuiGraphics guiGraphics.
 *    Output: void.
 * 12) {@code drawChatTab(...)}:
 *    Purpose: Renders the chat transcript area with scrolling support.
 *    Input: GuiGraphics guiGraphics.
 *    Output: void.
 * 13) {@code drawWrappedText(...)}:
 *    Purpose: Draws wrapped text lines inside a bounded width.
 *    Input: GuiGraphics guiGraphics, String text, int x, int y, int maxWidth, int color.
 *    Output: void.
 * 14) {@code updateTabVisibility(...)}:
 *    Purpose: Shows or hides the chat input box when the active tab changes.
 *    Input: none.
 *    Output: void.
 * 15) {@code mouseClicked(...)}:
 *    Purpose: Handles tab switching and chat-input mouse interaction before falling back to trade UI clicks.
 *    Input: MouseButtonEvent event, boolean consumed.
 *    Output: boolean.
 * 16) {@code keyPressed(...)}:
 *    Purpose: Handles chat submission, chat scrolling keys, and tab-specific keyboard behavior.
 *    Input: KeyEvent event.
 *    Output: boolean.
 * 17) {@code mouseScrolled(...)}:
 *    Purpose: Scrolls the chat transcript when the mouse wheel is used over the chat area.
 *    Input: double mouseX, double mouseY, double deltaX, double deltaY.
 *    Output: boolean.
 * 18) {@code onClose(...)}:
 *    Purpose: Logs that the screen is closing before normal cleanup continues.
 *    Input: none.
 *    Output: void.
 * 19) {@code receiveServerReply(...)}:
 *    Purpose: Updates the chat panel with the latest prompt and AI reply from the server.
 *    Input: String prompt, String reply.
 *    Output: void.
 * 20) {@code removed(...)}:
 *    Purpose: Sends the chat-session close packet when the screen is removed.
 *    Input: none.
 *    Output: void.
 * 21) {@code charTyped(...)}:
 *    Purpose: Forwards typed characters into the chat input when the chat tab is active.
 *    Input: CharacterEvent event.
 *    Output: boolean.
 * 22) {@code isTabClicked(...)}:
 *    Purpose: Checks whether the mouse position falls inside a tab's clickable area.
 *    Input: double mouseX, double mouseY, int x, int y, int width.
 *    Output: boolean.
 */
package com.example.examplemod.gui;

import com.example.examplemod.ModNetworking;
import com.example.examplemod.chat.SteveAiChatRequestPacket;
import com.example.examplemod.chat.SteveAiChatSessionPacket;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

public class SteveAiScreen extends MerchantScreen {

    private static final Logger LOGGER = org.slf4j.LoggerFactory.getLogger("NPCoo");

    private enum Tab {
        CHAT("Chat"),
        TRADE("Trade"),
        MAP("Map"),
        INVENTORY("Inv"),
        HIST("Hist");

        private final String label;

        Tab(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private Tab activeTab = Tab.CHAT;

    private int tabY;
    private int tradeTabX;
    private int chatTabX;
    private int mapTabX;
    private int histTabX;
    private int inventoryTabX;

    private static final int CHAT_TAB_WIDTH = 52;
    private static final int TRADE_TAB_WIDTH = 60;
    private static final int MAP_TAB_WIDTH = 44;
    private static final int HIST_TAB_WIDTH = 48;
    private static final int INVENTORY_TAB_WIDTH = 42;
    private static final int TAB_GAP = 4;
    private static final int TAB_HEIGHT = 18;

    private EditBox chatInput;
    private String lastPrompt = "";
    private String lastResponse = "";
    private String lastTiming = "";
    private long pendingPromptStartMs = -1L;
    private String pendingPrompt = "";
    private int chatScrollLines = 0;
    private boolean chatSessionStarted = false;

    private static final int CHAT_CONTENT_X_OFFSET = 24;
    private static final int CHAT_CONTENT_Y_OFFSET = 42;
    private static final int CHAT_CONTENT_BOTTOM_PADDING = 8;
    private static final int CHAT_LINE_HEIGHT = 10;
    private static final int CHAT_TEXT_COLOR = 0xFF000000;

    private void scrollChatToBottom() {
        this.chatScrollLines = Integer.MAX_VALUE;
    }

    public SteveAiScreen(SteveAiMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        LOGGER.info(com.sai.NpcooLog.tag("### SteveAiScreen CONSTRUCTOR ###"));
    }

    @Override
    protected void init() {
        super.init();

        if (!chatSessionStarted && minecraft != null && minecraft.player != null && minecraft.player.connection != null) {
            ModNetworking.CHANNEL.send(
                new SteveAiChatSessionPacket(true),
                net.minecraftforge.network.PacketDistributor.SERVER.noArg()
            );
            chatSessionStarted = true;
        }

        tabY = this.topPos - 18;

        chatTabX = this.leftPos + 8;
        tradeTabX = chatTabX + CHAT_TAB_WIDTH + TAB_GAP;
        mapTabX = tradeTabX + TRADE_TAB_WIDTH + TAB_GAP;
        histTabX = mapTabX + MAP_TAB_WIDTH + TAB_GAP;
        inventoryTabX = histTabX + HIST_TAB_WIDTH + TAB_GAP;

        chatInput = new EditBox(
            this.font,
            this.leftPos + 24,
            this.topPos + 18,
            this.imageWidth - 48,
            18,
            Component.literal("Ask SteveAI")
        );
        chatInput.setMaxLength(200);
        chatInput.setVisible(activeTab == Tab.CHAT);
        chatInput.setFocused(activeTab == Tab.CHAT);
        chatInput.setTextColor(0xFFFFFFFF);
        chatInput.setTextColorUneditable(0xFFFFFFFF);
        chatInput.setBordered(true);
        this.addRenderableWidget(chatInput);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
    if (activeTab == Tab.TRADE) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    } else {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        drawMainPanel(guiGraphics);
        drawCustomLabels(guiGraphics);
        drawPlaceholderContent(guiGraphics);

        if (activeTab == Tab.CHAT && chatInput != null && chatInput.isVisible()) {
            chatInput.render(guiGraphics, mouseX, mouseY, partialTick);
        }
    }

    drawTabs(guiGraphics);
    renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (activeTab == Tab.TRADE) {
            super.renderLabels(guiGraphics, mouseX, mouseY);
        }
    }

    private void drawMainPanel(GuiGraphics guiGraphics) {
        int x1 = this.leftPos;
        int y1 = this.topPos;
        int x2 = this.leftPos + this.imageWidth;
        int y2 = this.topPos + this.imageHeight;

        int outer = 0xFF3F3F3F;
        int light = 0xFFFFFFFF;
        int panel = 0xFFC6C6C6;
        int shadow = 0xFF8B8B8B;

        guiGraphics.fill(x1, y1, x2, y2, outer);
        guiGraphics.fill(x1 + 1, y1 + 1, x2 - 1, y1 + 3, light);
        guiGraphics.fill(x1 + 1, y1 + 1, x1 + 3, y2 - 1, light);
        guiGraphics.fill(x1 + 1, y2 - 3, x2 - 1, y2 - 1, shadow);
        guiGraphics.fill(x2 - 3, y1 + 1, x2 - 1, y2 - 1, shadow);
        guiGraphics.fill(x1 + 3, y1 + 3, x2 - 3, y2 - 3, panel);
    }

    private void drawCustomLabels(GuiGraphics guiGraphics) {
        guiGraphics.drawString(this.font, this.title, this.leftPos + 106, this.topPos + 6, 0xFF000000, false);
    }

    private void drawTabs(GuiGraphics guiGraphics) {
        drawTab(guiGraphics, chatTabX, tabY, CHAT_TAB_WIDTH, Tab.CHAT);
        drawTab(guiGraphics, tradeTabX, tabY, TRADE_TAB_WIDTH, Tab.TRADE);
        drawTab(guiGraphics, mapTabX, tabY, MAP_TAB_WIDTH, Tab.MAP);
        drawTab(guiGraphics, histTabX, tabY, HIST_TAB_WIDTH, Tab.HIST);
        drawTab(guiGraphics, inventoryTabX, tabY, INVENTORY_TAB_WIDTH, Tab.INVENTORY);
    }

    private void drawTab(GuiGraphics guiGraphics, int x, int y, int width, Tab tab) {
        boolean selected = activeTab == tab;

        int outer = 0xFF3F3F3F;
        int light = 0xFFFFFFFF;
        int fill = selected ? 0xFFC6C6C6 : 0xFFB5B5B5;
        int shadow = 0xFF8B8B8B;
        int textColor = 0xFF000000;

        int x2 = x + width;
        int y2 = y + TAB_HEIGHT;

        guiGraphics.fill(x, y, x2, y2, outer);
        guiGraphics.fill(x + 1, y + 1, x2 - 1, y + 3, light);
        guiGraphics.fill(x + 1, y + 1, x + 3, y2 - 1, light);
        guiGraphics.fill(x + 1, y2 - 3, x2 - 1, y2 - 1, shadow);
        guiGraphics.fill(x2 - 3, y + 1, x2 - 1, y2 - 1, shadow);
        guiGraphics.fill(x + 3, y + 3, x2 - 3, y2 - 3, fill);

        if (selected) {
            guiGraphics.fill(x + 2, y2 - 2, x2 - 2, y2 + 2, 0xFFC6C6C6);
        }

        int textWidth = this.font.width(tab.label());
        guiGraphics.drawString(
            this.font,
            tab.label(),
            x + (width - textWidth) / 2,
            y + 6,
            textColor,
            false
        );
    }

    private void drawPlaceholderContent(GuiGraphics guiGraphics) {
        if (activeTab == Tab.CHAT) {
            drawChatTab(guiGraphics);
            return;
        }

        String line1 = switch (activeTab) {
            case MAP -> "Future map functionality";
            case INVENTORY -> "Future inventory functionality";
            default -> "";
        };

        String line2 = "Placeholder";

        int centerX = this.leftPos + this.imageWidth / 2;
        int centerY = this.topPos + this.imageHeight / 2 - 10;

        int line1Width = this.font.width(line1);
        int line2Width = this.font.width(line2);

        guiGraphics.drawString(this.font, line1, centerX - line1Width / 2, centerY, 0x000000, false);
        guiGraphics.drawString(this.font, line2, centerX - line2Width / 2, centerY + 14, 0xFF000000, false);
    }

    private void drawChatTab(GuiGraphics guiGraphics) {
        int x = this.leftPos + CHAT_CONTENT_X_OFFSET;
        int y = (chatInput != null)
            ? (chatInput.getY() + chatInput.getHeight() + CHAT_CONTENT_BOTTOM_PADDING)
            : (this.topPos + CHAT_CONTENT_Y_OFFSET);
        int maxWidth = this.imageWidth - (CHAT_CONTENT_X_OFFSET * 2);
        int contentBottom = this.topPos + this.imageHeight - CHAT_CONTENT_BOTTOM_PADDING;
        int contentHeight = Math.max(CHAT_LINE_HEIGHT, contentBottom - y);

        java.util.List<net.minecraft.util.FormattedCharSequence> lines = new java.util.ArrayList<>();
        lines.addAll(this.font.split(Component.literal("YOU: " + (lastPrompt == null ? "" : lastPrompt)), maxWidth));
        if (lastTiming != null && !lastTiming.isBlank()) {
            lines.addAll(this.font.split(Component.literal(lastTiming), maxWidth));
        }
        lines.add(Component.literal("").getVisualOrderText());
        lines.addAll(this.font.split(Component.literal("STEVEAI: " + (lastResponse == null ? "" : lastResponse)), maxWidth));

        int visibleLines = Math.max(1, contentHeight / CHAT_LINE_HEIGHT);
        int maxScroll = Math.max(0, lines.size() - visibleLines);
        chatScrollLines = Math.max(0, Math.min(chatScrollLines, maxScroll));

        int start = chatScrollLines;
        int end = Math.min(lines.size(), start + visibleLines);

        int drawY = y;
        for (int i = start; i < end; i++) {
            guiGraphics.drawString(this.font, lines.get(i), x, drawY, CHAT_TEXT_COLOR, false);
            drawY += CHAT_LINE_HEIGHT;
        }
    }

    private void drawWrappedText(GuiGraphics guiGraphics, String text, int x, int y, int maxWidth, int color) {
        var lines = this.font.split(Component.literal(text), maxWidth);
        int currentY = y;

        for (var line : lines) {
            guiGraphics.drawString(this.font, line, x, currentY, color, false);
            currentY += 10;
        }
    }

    private void updateTabVisibility() {
        if (chatInput != null) {
            boolean showChatInput = activeTab == Tab.CHAT;
            chatInput.setVisible(showChatInput);
            chatInput.setFocused(showChatInput);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean consumed) {
        double mouseX = event.x();
        double mouseY = event.y();

        if (isTabClicked(mouseX, mouseY, tradeTabX, tabY, TRADE_TAB_WIDTH)) {
            activeTab = Tab.TRADE;
            updateTabVisibility();
            return true;
        }
        if (isTabClicked(mouseX, mouseY, chatTabX, tabY, CHAT_TAB_WIDTH)) {
            activeTab = Tab.CHAT;
            updateTabVisibility();
            return true;
        }
        if (isTabClicked(mouseX, mouseY, mapTabX, tabY, MAP_TAB_WIDTH)) {
            activeTab = Tab.MAP;
            updateTabVisibility();
            return true;
        }
        if (isTabClicked(mouseX, mouseY, histTabX, tabY, HIST_TAB_WIDTH)) {
            LOGGER.info(com.sai.NpcooLog.tag("HIST tab clicked"));
            activeTab = Tab.HIST;
            return true;
        }
        if (isTabClicked(mouseX, mouseY, inventoryTabX, tabY, INVENTORY_TAB_WIDTH)) {
            activeTab = Tab.INVENTORY;
            updateTabVisibility();
            return true;
        }

        if (activeTab == Tab.CHAT && chatInput != null && chatInput.mouseClicked(event, consumed)) {
            return true;
        }

        if (activeTab != Tab.TRADE) {
            return true;
        }

        return super.mouseClicked(event, consumed);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (activeTab == Tab.CHAT && chatInput != null && chatInput.isVisible()) {
            LOGGER.info(com.sai.NpcooLog.tag("SteveAiScreen keypressed STARTED "));
            
            int keyCode = event.key();

            // ESC should still close the screen
            if (keyCode == 256) {
                return super.keyPressed(event);
            }

            // swallow inventory key E everywhere in this UI
            if (keyCode == 69) { // E
                return true;
            }

            // Scroll chat history in the chat tab.
            if (keyCode == 266) { // Page Up
                chatScrollLines = Math.max(0, chatScrollLines - 6);
                return true;
            }
            if (keyCode == 267) { // Page Down
                chatScrollLines += 6;
                return true;
            }

            // Handle Enter FIRST
            if (keyCode == 257 || keyCode == 335) {
                LOGGER.info(com.sai.NpcooLog.tag("SteveAiScreen keypressed started to wait for input"));
                String message = chatInput.getValue().trim();
                LOGGER.info(com.sai.NpcooLog.tag("SteveAiScreen keypressed input is done and enter pressed {}"), message);

                if (!message.isEmpty()) {
                    lastPrompt = message;
                    lastResponse = "Thinking...";
                    lastTiming = "";
                    pendingPromptStartMs = System.currentTimeMillis();
                    pendingPrompt = message;
                    scrollChatToBottom();
                    chatInput.setValue("");
                    LOGGER.info(com.sai.NpcooLog.tag("SteveAiScreen calling openai to get reply")); 

                    if (minecraft != null && minecraft.player != null && minecraft.player.connection != null) {
                        ModNetworking.CHANNEL.send(
                            new SteveAiChatRequestPacket(message),
                            net.minecraftforge.network.PacketDistributor.SERVER.noArg()
                        );
                    } else {
                        lastResponse = "Not connected.";
                        pendingPromptStartMs = -1L;
                        pendingPrompt = "";
                    }
                    //if (minecraft.player != null && minecraft.player.connection != null) {
                    //    minecraft.player.connection.sendCommand("testmod " + message);
                    //}
                    LOGGER.info(com.sai.NpcooLog.tag("SteveAiScreen finished writing to chat file ")); 
                }

                return true;
            }

            // Let the text box handle normal editing keys after that
            if (chatInput.keyPressed(event)) {
                LOGGER.info(com.sai.NpcooLog.tag("SteveAiScreen keypressed leaving 1 ???  "));
                return true;
            }

            // IMPORTANT: swallow all other keys so Minecraft keybinds
            // like inventory ('e') do not close the screen
            return true;
        }
        LOGGER.info(com.sai.NpcooLog.tag("SteveAiScreen keypressed leaving 2 ???  "));
        return super.keyPressed(event);

    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (activeTab == Tab.CHAT) {
            int x = this.leftPos + CHAT_CONTENT_X_OFFSET;
            int y = (chatInput != null)
                ? (chatInput.getY() + chatInput.getHeight() + CHAT_CONTENT_BOTTOM_PADDING)
                : (this.topPos + CHAT_CONTENT_Y_OFFSET);
            int width = this.imageWidth - (CHAT_CONTENT_X_OFFSET * 2);
            int bottom = this.topPos + this.imageHeight - CHAT_CONTENT_BOTTOM_PADDING;

            if (mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= bottom) {
                if (deltaY > 0) {
                    chatScrollLines = Math.max(0, chatScrollLines - 2);
                } else if (deltaY < 0) {
                    chatScrollLines += 2;
                }
                return true;
            }
        }

        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }

    @Override
    public void onClose() {
        LOGGER.info(com.sai.NpcooLog.tag("### SteveAiScreen onClose ###"));
        super.onClose();
    }

    public void receiveServerReply(String prompt, String reply, long serverProcessingMs, String modeLabel) {
        this.lastPrompt = prompt;
        this.lastResponse = reply;
        long roundtripMs = -1L;
        if (pendingPromptStartMs > 0L && prompt != null && prompt.equals(pendingPrompt)) {
            roundtripMs = Math.max(0L, System.currentTimeMillis() - pendingPromptStartMs);
        }

        String label = modeLabel != null && !modeLabel.isBlank() ? " [" + modeLabel + "]" : "";
        if (roundtripMs >= 0L) {
            this.lastTiming = "TIME: " + roundtripMs + "ms total (server " + Math.max(0L, serverProcessingMs) + "ms)" + label;
        } else {
            this.lastTiming = "TIME: server " + Math.max(0L, serverProcessingMs) + "ms" + label;
        }

        pendingPromptStartMs = -1L;
        pendingPrompt = "";
        scrollChatToBottom();
    }

    @Override
    public void removed() {
        LOGGER.info(com.sai.NpcooLog.tag("### SteveAiScreen removed ###"));
        if (chatSessionStarted && minecraft != null && minecraft.player != null && minecraft.player.connection != null) {
            ModNetworking.CHANNEL.send(
                new SteveAiChatSessionPacket(false),
                net.minecraftforge.network.PacketDistributor.SERVER.noArg()
            );
            chatSessionStarted = false;
        }
        super.removed();
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (activeTab == Tab.CHAT && chatInput != null && chatInput.isVisible()) {
            if (chatInput.charTyped(event)) {
                return true;
            }
        }

        return super.charTyped(event);
    }

    private boolean isTabClicked(double mouseX, double mouseY, int x, int y, int width) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + TAB_HEIGHT;
    }
}
