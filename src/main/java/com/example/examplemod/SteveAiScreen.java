package com.example.examplemod;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

public class SteveAiScreen extends MerchantScreen {

    private static final Logger LOGGER = LogUtils.getLogger();

    private enum Tab {
        TRADE,
        CHAT,
        MAP,
        INVENTORY
    }

    private Tab activeTab = Tab.TRADE;

    // tab layout
    private int tabY;
    private int tradeTabX;
    private int chatTabX;
    private int mapTabX;
    private int inventoryTabX;

    public SteveAiScreen(SteveAiMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        LOGGER.info("### SteveAiScreen CONSTRUCTOR ###");
    }

    @Override
    protected void init() {
        LOGGER.info("### SteveAiScreen override tabs ###");
        super.init();

        tabY = this.topPos - 24;
        tradeTabX = this.leftPos + 8;
        chatTabX = tradeTabX + 54;
        mapTabX = chatTabX + 54;
        inventoryTabX = mapTabX + 54;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        LOGGER.info("### steveAiscreen GuiGraphics ###");
        if (activeTab == Tab.TRADE) {
            super.render(guiGraphics, mouseX, mouseY, partialTick);
        } else {
            this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);

            // draw normal merchant background so the window still looks familiar
            super.renderBg(guiGraphics, partialTick, mouseX, mouseY);
            super.renderLabels(guiGraphics, mouseX, mouseY);

            drawPlaceholderContent(guiGraphics);
        }

        drawTabs(guiGraphics, mouseX, mouseY);
        renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        LOGGER.info("### steveAiscreen renderLabels ###");
        if (activeTab == Tab.TRADE) {
            super.renderLabels(guiGraphics, mouseX, mouseY);
        } else {
            guiGraphics.drawString(this.font, this.title, 49, 6, 4210752, false);
            guiGraphics.drawString(this.font, this.playerInventoryTitle, 8, this.imageHeight - 94, 4210752, false);
        }
    }

    private void drawTabs(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        LOGGER.info("### steveAiscreen drawTabs ###");
        drawTab(guiGraphics, tradeTabX, tabY, "Trade", activeTab == Tab.TRADE);
        drawTab(guiGraphics, chatTabX, tabY, "Chat", activeTab == Tab.CHAT);
        drawTab(guiGraphics, mapTabX, tabY, "Map", activeTab == Tab.MAP);
        drawTab(guiGraphics, inventoryTabX, tabY, "Inventory", activeTab == Tab.INVENTORY);
    }

    private void drawTab(GuiGraphics guiGraphics, int x, int y, String label, boolean selected) {
        LOGGER.info("### steveAiscreen drawTab ###");
        int fillColor = selected ? 0xFFB0B0B0 : 0xFF808080;
        int borderColor = 0xFF202020;
        int textColor = 0xFFFFFFFF;

        guiGraphics.fill(x, y, x + 50, y + 20, fillColor);

        // border
        guiGraphics.fill(x, y, x + 50, y + 1, borderColor);
        guiGraphics.fill(x, y + 19, x + 50, y + 20, borderColor);
        guiGraphics.fill(x, y, x + 1, y + 20, borderColor);
        guiGraphics.fill(x + 49, y, x + 50, y + 20, borderColor);

        int textWidth = this.font.width(label);
        guiGraphics.drawString(
            this.font,
            label,
            x + (50 - textWidth) / 2,
            y + 6,
            textColor,
            false
        );
    }

    private void drawPlaceholderContent(GuiGraphics guiGraphics) {
        LOGGER.info("### steveAiscreen drawPlaceholderContent ###");
        String line1;
        String line2 = "Future release";

        switch (activeTab) {
            case CHAT -> line1 = "Chat tab";
            case MAP -> line1 = "Map tab";
            case INVENTORY -> line1 = "Inventory tab";
            default -> line1 = "";
        }

        int centerX = this.leftPos + this.imageWidth / 2;
        int centerY = this.topPos + this.imageHeight / 2 - 10;

        int line1Width = this.font.width(line1);
        int line2Width = this.font.width(line2);

        guiGraphics.drawString(this.font, line1, centerX - line1Width / 2, centerY, 0xFFFFFF, false);
        guiGraphics.drawString(this.font, line2, centerX - line2Width / 2, centerY + 14, 0xAAAAAA, false);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean consumed) {
        double mouseX = event.x();
        double mouseY = event.y();
        LOGGER.info("### steveAiscreen mouseClicked at x: {}, y: {} ###", mouseX, mouseY);
        if (isTabClicked(mouseX, mouseY, tradeTabX, tabY)) {
            activeTab = Tab.TRADE;
            return true;
        }
        if (isTabClicked(mouseX, mouseY, chatTabX, tabY)) {
            activeTab = Tab.CHAT;
            return true;
        }
        if (isTabClicked(mouseX, mouseY, mapTabX, tabY)) {
            activeTab = Tab.MAP;
            return true;
        }
        if (isTabClicked(mouseX, mouseY, inventoryTabX, tabY)) {
            activeTab = Tab.INVENTORY;
            return true;
        }

        if (activeTab != Tab.TRADE) {
            return true;
        }

        return super.mouseClicked(event, consumed);
    }

    private boolean isTabClicked(double mouseX, double mouseY, int x, int y) {
        LOGGER.info("### steveAiscreen isTabClicked at x: {}, y: {} ###", mouseX, mouseY);
        return mouseX >= x && mouseX < x + 50 && mouseY >= y && mouseY < y + 20;
    }
}