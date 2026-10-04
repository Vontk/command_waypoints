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
    private boolean visible, saving, infinite;
    private EditBox nameBox, xBox, zBox, colorBox, rangeBox;
    private Button saveButton, dimensionButton, visibleButton, rangeButton, styleButton;
    private int rowStep, right, half;
    public WaypointEditorScreen(Screen parent, WaypointData point) {
        super(parent, point == null ? "New waypoint" : "Edit waypoint");
        original = point == null ? WaypointClient.defaults() : point; reset();
    }
    private void reset() {
        name = original.name(); x = "" + original.x(); z = "" + original.z(); dimension = original.dimension();
        color = original.color() == null ? "" : String.format("%06X", original.color());
        infinite = original.range() == Integer.MAX_VALUE;
        range = infinite ? "1000" : "" + original.range(); style = original.style(); visible = original.visible();
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
        colorBox = field("Hex color", color, left, 50 + rowStep * 2, half - 88); colorBox.setMaxLength(7);
        colorBox.setHint(Component.literal("Automatic")); colorBox.setResponder(value -> color = value);
        tooltip(colorBox, "Waypoint marker and name color. Six hexadecimal RGB digits; optional #. Use Pick color for sliders and a live preview.");
        button("Pick color…", left + half - 84, 50 + rowStep * 2, 84, b -> minecraft.gui.setScreen(new WaypointColorScreen(this, color, name, automaticColor(), value -> { color=value; colorBox.setValue(value); })));
        button("Automatic", right, 50 + rowStep * 2, half, b -> colorBox.setValue(""));
        rangeBox = field("Display range", infinite ? "" : range, left, 50 + rowStep * 3, half - 94);
        rangeBox.setHint(Component.literal("No distance limit")); rangeBox.setResponder(value -> { if(!infinite) range = value; });rangeBox.active=!infinite;
        tooltip(rangeBox, "Maximum horizontal distance in blocks. 0 hides the marker. Infinite removes this waypoint's distance limit; vanilla receive range still applies.");
        rangeButton = button(infinite ? "Infinite" : "Limited", left + half - 90, 50 + rowStep * 3, 90, b -> {
            infinite=!infinite; b.setMessage(Component.literal(infinite ? "Infinite" : "Limited"));rangeBox.active=!infinite;rangeBox.setValue(infinite ? "" : range);
        });
        tooltip(rangeButton,"Switch between a distance limit and Infinite. A limited value is remembered while editing.");
        styleButton = button(WaypointStyleScreen.label(style), right + 24, 50 + rowStep * 3, half - 24, b -> minecraft.gui.setScreen(new WaypointStyleScreen(this,style,previewColor(),value->{style=value;styleButton.setMessage(Component.literal(WaypointStyleScreen.label(style)));})));
        tooltip(styleButton,"Shape of the waypoint marker on the Locator Bar. Choose a built-in shape with previews, or enter a resource-pack style.");
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
    private int automaticColor() {
        if(original.uuid()==null || original.uuid().isBlank()) return 0x70d6ff;
        return net.minecraft.util.ARGB.setBrightness(net.minecraft.util.ARGB.color(255, UUID.fromString(original.uuid()).hashCode()),0.9f)&0xffffff;
    }
    private int previewColor() { try { return Integer.parseInt(color.replaceFirst("^#",""),16)&0xffffff; } catch(NumberFormatException ignored){return automaticColor();} }
    private void save() {
        try {
            String rgb = color.strip().replaceFirst("^#", "");
            if (!rgb.isEmpty() && !rgb.matches("[0-9a-fA-F]{6}")) throw new IllegalArgumentException("Use six hexadecimal digits for color.");
            var data = new WaypointData(original.uuid(), original.id(), name.strip(), Integer.parseInt(x.strip()), Integer.parseInt(z.strip()),
                dimension, rgb.isEmpty() ? null : Integer.parseInt(rgb, 16), style.strip(), infinite ? Integer.MAX_VALUE : Integer.parseInt(range.strip()), visible, true, original.personal());
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
        label(graphics, "Waypoint marker & name color", left, 40 + rowStep * 2); 
        label(graphics, "Display range (blocks)", left, 40 + rowStep * 3); label(graphics, "Marker shape", right, 40 + rowStep * 3);
        WaypointStyleScreen.preview(graphics,style,right+3,53+rowStep*3,16,previewColor());
    }
}
