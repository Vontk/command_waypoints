package com.minenash.command_waypoints.client;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.network.chat.Component;
import java.util.function.Consumer;

/** Shared vanilla widgets, layout and keyboard navigation for the waypoint screens. */
public abstract class WaypointScreen extends Screen {
    protected final Screen parent;
    protected int left, panelWidth;
    protected String error = "";
    protected WaypointScreen(Screen parent, String title) { super(Component.literal(title)); this.parent = parent; }
    @Override protected void init() { panelWidth = Math.min(480, width - 24); left = (width - panelWidth) / 2; }
    protected Button button(String name, int x, int y, int w, Consumer<Button> callback) {
        return addRenderableWidget(Button.builder(Component.literal(name), callback::accept).bounds(x, y, w, 20).build());
    }
    protected EditBox field(String name, String value, int x, int y, int w) {
        var box = addRenderableWidget(new EditBox(font, x, y, w, 20, Component.literal(name)));
        box.setMaxLength(120); box.setValue(value); return box;
    }
    protected void tooltip(AbstractWidget widget, String text) { widget.setTooltip(Tooltip.create(Component.literal(text))); }
    protected void label(GuiGraphicsExtractor graphics, String text, int x, int y) { graphics.text(font, text, x, y, 0xffc6cbd1); }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(left - 8, 30, left + panelWidth + 8, height - 38, 0xba10151d);
        graphics.centeredText(font, title, width / 2, 15, 0xffffffff);
        graphics.horizontalLine(left - 8, left + panelWidth + 8, 30, 0xff52677b);
        if (!error.isEmpty()) graphics.centeredText(font, Component.literal(error), width / 2, height - 46, 0xffff8888);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
