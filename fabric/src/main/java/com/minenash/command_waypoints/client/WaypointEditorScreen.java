package com.minenash.command_waypoints.client;

import com.minenash.command_waypoints.WaypointData;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.network.chat.Component;
import java.util.*;

public final class WaypointEditorScreen extends WaypointScreen {
    private final WaypointData original;
    private String name, x, z, dimension, color, range, style;
    private boolean visible, saving;
    private EditBox nameBox, xBox, zBox, colorBox, rangeBox, styleBox;
    private Button saveButton, dimensionButton, visibleButton;
    private int rowStep, right, half;
    private static final int[] COLORS = {0xff5555, 0xffaa00, 0xffff55, 0x55ff55, 0x55ffff, 0x5555ff, 0xff55ff, 0xffffff};
    public WaypointEditorScreen(Screen parent, WaypointData point) {
        super(parent, point == null ? "New waypoint" : "Edit waypoint");
        original = point == null ? WaypointClient.defaults() : point; reset();
    }
    private void reset() {
        name = original.name(); x = "" + original.x(); z = "" + original.z(); dimension = original.dimension();
        color = original.color() == null ? "" : String.format("%06X", original.color());
        range = "" + original.range(); style = original.style(); visible = original.visible();
    }
    @Override protected void init() {
        super.init(); half = (panelWidth - 8) / 2; right = left + half + 8;
        rowStep = Math.min(42, Math.max(30, (height - 95) / 5));
        nameBox = field("Name", name, left, 50, panelWidth); nameBox.setMaxLength(80);
        nameBox.setHint(Component.literal("Leave empty for waypoint<N>")); nameBox.setResponder(value -> name = value);
        int small = (half - 6) / 2;
        xBox = field("X coordinate", x, left, 50 + rowStep, small); xBox.setResponder(value -> x = value);
        zBox = field("Z coordinate", z, left + small + 6, 50 + rowStep, small); zBox.setResponder(value -> z = value);
        dimensionButton = button(WaypointData.dimensionName(dimension), right, 50 + rowStep, half, b -> {
            var dimensions = WaypointClient.dimensions;
            dimension = dimensions.get((dimensions.indexOf(dimension) + 1) % dimensions.size());
            b.setMessage(Component.literal(WaypointData.dimensionName(dimension)));
        });
        tooltip(dimensionButton, "Source dimension. Coordinates are stored in this dimension; changing it does not convert the numbers.");
        colorBox = field("Hex color", color, left, 50 + rowStep * 2, half - 52); colorBox.setMaxLength(7);
        colorBox.setHint(Component.literal("Automatic")); colorBox.setResponder(value -> color = value);
        tooltip(colorBox, "Six hexadecimal digits, e.g. 55FF55. Empty uses the automatic UUID color.");
        button("Auto", left + half - 48, 50 + rowStep * 2, 48, b -> colorBox.setValue(""));
        int swatchWidth = Math.max(12, (half - 14) / COLORS.length);
        for (int n = 0; n < COLORS.length; n++) {
            int rgb = COLORS[n];
            var swatch = button("■", right + n * (swatchWidth + 2), 50 + rowStep * 2, swatchWidth, b -> colorBox.setValue(String.format("%06X", rgb)));
            swatch.setMessage(Component.literal("■").withStyle(s -> s.withColor(rgb)));
            tooltip(swatch, "#" + String.format("%06X", rgb));
        }
        rangeBox = field("Display range", range, left, 50 + rowStep * 3, half); rangeBox.setResponder(value -> range = value);
        tooltip(rangeBox, "Maximum horizontal display distance in the viewer's dimension. 0 hides the waypoint; default 60000000.");
        styleBox = field("Icon style", style, right, 50 + rowStep * 3, half); styleBox.setResponder(value -> style = value);
        tooltip(styleBox, "Minecraft icon style: minecraft:default, minecraft:bowtie, or a style supplied by a resource pack.");
        visibleButton = button(visible ? "Visible: Yes" : "Visible: No", left, 50 + rowStep * 4, half, b -> {
            visible = !visible; b.setMessage(Component.literal(visible ? "Visible: Yes" : "Visible: No"));
        });
        button("Current location", right, 50 + rowStep * 4, half, b -> {
            var current = WaypointClient.defaults(); xBox.setValue("" + current.x()); zBox.setValue("" + current.z());
            dimension = current.dimension(); dimensionButton.setMessage(Component.literal(WaypointData.dimensionName(dimension)));
        });
        int w = (panelWidth - 8) / 3;
        button("Reset", left, height - 28, w, b -> { reset(); rebuildWidgets(); });
        button("Cancel", left + w + 4, height - 28, w, b -> onClose());
        saveButton = button("Save", left + (w + 4) * 2, height - 28, w, b -> save());
        saveButton.active = WaypointClient.supported() && !saving;
        setInitialFocus(nameBox);
    }
    private void save() {
        try {
            String rgb = color.strip().replaceFirst("^#", "");
            if (!rgb.isEmpty() && !rgb.matches("[0-9a-fA-F]{6}")) throw new IllegalArgumentException("Use six hexadecimal digits for color.");
            var data = new WaypointData(original.uuid(), original.id(), name.strip(), Integer.parseInt(x.strip()), Integer.parseInt(z.strip()),
                dimension, rgb.isEmpty() ? null : Integer.parseInt(rgb, 16), style.strip(), Integer.parseInt(range.strip()), visible, true, original.personal());
            WaypointData.validate(data);
            var object = WaypointClient.action("save"); object.add("point", WaypointData.GSON.toJsonTree(data));
            saving = true; saveButton.active = false; error = "";
            WaypointClient.request(object, message -> {
                saving = false; saveButton.active = true; error = message;
                if (message.isEmpty() && minecraft.gui.screen() == this) onClose();
            });
        } catch (NumberFormatException exception) { error = "Coordinates and range must be whole numbers."; }
        catch (IllegalArgumentException exception) { error = exception.getMessage(); }
    }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        label(graphics, "Name", left, 40);
        label(graphics, "Coordinates (X Z)", left, 40 + rowStep); label(graphics, "Dimension", right, 40 + rowStep);
        label(graphics, "Color (hex RGB)", left, 40 + rowStep * 2); label(graphics, "Color palette", right, 40 + rowStep * 2);
        label(graphics, "Display range (blocks)", left, 40 + rowStep * 3); label(graphics, "Icon style", right, 40 + rowStep * 3);
    }
}
