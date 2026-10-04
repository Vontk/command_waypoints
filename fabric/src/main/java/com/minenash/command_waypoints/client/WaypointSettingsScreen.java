package com.minenash.command_waypoints.client;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.client.gui.components.EditBox;

public final class WaypointSettingsScreen extends WaypointScreen {
    private String distance = "" + WaypointSettings.INSTANCE.growthDistance;
    private String minimum = "" + WaypointSettings.INSTANCE.minimumSize;
    private String maximum = "" + WaypointSettings.INSTANCE.maximumSize;
    private int step;
    public WaypointSettingsScreen(Screen parent) { super(parent, "Waypoint settings"); }
    @Override protected void init() {
        super.init(); step = Math.min(42, Math.max(30, (height - 110) / 4));
        var growth = field("Growth distance", distance, left + panelWidth / 2, 48, panelWidth / 2);
        growth.setResponder(value -> distance = value); tooltip(growth, "Minimum size at this distance or farther. Grows linearly to maximum size at zero distance. Default: 200 blocks.");
        var min = field("Minimum icon size", minimum, left + panelWidth / 2, 48 + step, panelWidth / 2);
        min.setResponder(value -> minimum = value); tooltip(min, "Icon size in GUI pixels at or beyond the growth distance. Default: 6.");
        var max = field("Maximum icon size", maximum, left + panelWidth / 2, 48 + step * 2, panelWidth / 2);
        max.setResponder(value -> maximum = value); tooltip(max, "Icon size in GUI pixels at zero distance. Default: 14.");
        button("Key bindings…", left, 48 + step * 3, panelWidth, b -> minecraft.gui.setScreen(new KeyBindsScreen(this, minecraft.options)));
        int w = (panelWidth - 8) / 3;
        button("Defaults", left, height - 28, w, b -> { distance = "200"; minimum = "6"; maximum = "14"; rebuildWidgets(); });
        button("Cancel", left + w + 4, height - 28, w, b -> onClose());
        button("Save", left + (w + 4) * 2, height - 28, w, b -> {
            try {
                var settings = new WaypointSettings(); settings.growthDistance = Double.parseDouble(distance);
                settings.minimumSize = Double.parseDouble(minimum); settings.maximumSize = Double.parseDouble(maximum); settings.save(); onClose();
            } catch (NumberFormatException exception) { error = "Enter valid numbers."; }
            catch (Exception exception) { error = exception.getMessage(); }
        });
    }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        label(graphics, "Growth distance (blocks)", left, 54);
        label(graphics, "Minimum size (pixels)", left, 54 + step);
        label(graphics, "Maximum size (pixels)", left, 54 + step * 2);
        if (height >= 280) graphics.centeredText(font, "Open: " + WaypointClient.openKey.getTranslatedKeyMessage().getString() + "   •   Quick add: " + WaypointClient.addKey.getTranslatedKeyMessage().getString(), width / 2, 80 + step * 3, 0xffc6cbd1);
    }
}
