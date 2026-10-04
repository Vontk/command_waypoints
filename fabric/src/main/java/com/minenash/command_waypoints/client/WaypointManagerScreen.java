package com.minenash.command_waypoints.client;

import com.minenash.command_waypoints.WaypointData;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.network.chat.Component;
import java.util.*;

public final class WaypointManagerScreen extends WaypointScreen {
    private String query = "", dimension = "all";
    private int page, seenRevision = -1;
    private WaypointData selected;
    private final List<Button> rows = new ArrayList<>();
    private Button edit, delete, share, visible, previous, next, filter;
    private EditBox search;
    private List<WaypointData> filtered = List.of();
    private int perPage;
    public WaypointManagerScreen(Screen parent) { super(parent, "Waypoints"); }
    @Override protected void init() {
        super.init(); rows.clear();
        search = field("Search waypoints", query, left, 40, panelWidth - 164);
        search.setHint(Component.literal("Search waypoints…"));
        search.setResponder(value -> { query = value; page = 0; update(); });
        filter = button(WaypointData.dimensionName(dimension), left + panelWidth - 158, 40, 158, b -> {
            var choices = new ArrayList<String>(); choices.add("all"); choices.addAll(WaypointClient.dimensions);
            dimension = choices.get((choices.indexOf(dimension) + 1) % choices.size());
            b.setMessage(Component.literal(WaypointData.dimensionName(dimension))); page = 0; selected = null; update();
        });
        tooltip(filter, "Filter by saved source dimension. Click to cycle through dimensions.");
        perPage = Math.max(1, (height - 167) / 28);
        for (int n = 0; n < perPage; n++) {
            final int row = n;
            rows.add(button("", left, 69 + n * 28, panelWidth, b -> {
                int index = page * perPage + row;
                if (index < filtered.size()) { selected = filtered.get(index); update(); }
            }));
        }
        int toolbar = height - 89, gap = 4, w = (panelWidth - gap * 4) / 5;
        var add = button("New", left, toolbar, w, b -> minecraft.gui.setScreen(new WaypointEditorScreen(this, null)));
        edit = button("Edit", left + (w + gap), toolbar, w, b -> minecraft.gui.setScreen(new WaypointEditorScreen(this, selected)));
        visible = button("Hide", left + (w + gap) * 2, toolbar, w, b -> {
            var object = WaypointClient.action("visible"); object.addProperty("uuid", selected.uuid()); object.addProperty("visible", !selected.visible());
            WaypointClient.request(object, value -> error = value);
        });
        share = button("Share", left + (w + gap) * 3, toolbar, w, b -> minecraft.gui.setScreen(new WaypointShareScreen(this, selected)));
        delete = button("Delete", left + (w + gap) * 4, toolbar, w, b -> {
            String uuid = selected.uuid();
            minecraft.gui.setScreen(new ConfirmScreen(confirmed -> {
                minecraft.gui.setScreen(this);
                if (confirmed) { var object = WaypointClient.action("delete"); object.addProperty("uuid", uuid); WaypointClient.request(object, value -> error = value); }
            }, Component.literal("Delete waypoint?"), Component.literal(selected.name() + " — " + selected.x() + " " + selected.z()), Component.literal("Delete"), Component.literal("Cancel")));
        });
        previous = button("<", left, height - 62, 28, b -> { page--; update(); });
        next = button(">", left + panelWidth - 28, height - 62, 28, b -> { page++; update(); });
        button("Settings", left, height - 28, panelWidth / 2 - 3, b -> minecraft.gui.setScreen(new WaypointSettingsScreen(this)));
        button("Done", left + panelWidth / 2 + 3, height - 28, panelWidth / 2 - 3, b -> onClose());
        add.active = WaypointClient.supported(); update(); WaypointClient.refresh();
    }
    private void update() {
        seenRevision = WaypointClient.revision;
        filtered = WaypointClient.points.stream().filter(p -> dimension.equals("all") || p.dimension().equals(dimension))
            .filter(p -> p.name().toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))
                || p.id().toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT)))
            .sorted(Comparator.comparing(WaypointData::name, String.CASE_INSENSITIVE_ORDER).thenComparing(WaypointData::dimension)).toList();
        page = Math.clamp(page, 0, Math.max(0, (filtered.size() - 1) / perPage));
        if (selected != null) selected = filtered.stream().filter(p -> p.uuid().equals(selected.uuid())).findFirst().orElse(null);
        for (int n = 0; n < rows.size(); n++) {
            int index = page * perPage + n; var row = rows.get(n); row.visible = index < filtered.size();
            if (row.visible) {
                var point = filtered.get(index);
                String details = point.x() + " " + point.z() + (dimension.equals("all") ? " | " + WaypointData.dimensionName(point.dimension()) : "") + (point.visible() ? "" : " (hidden)");
                var name = Component.literal((selected != null && selected.uuid().equals(point.uuid()) ? "> " : "") + point.name()).withStyle(s -> s.withColor(point.color() == null ? defaultColor(point) : point.color()));
                row.setMessage(Component.empty().append(name).append(Component.literal(" | " + details)));
                tooltip(row, point.name() + "\n" + details + "\nCommand ID: " + point.id() + (point.personal() ? "\nPersonal waypoint" : "\nLegacy shared waypoint"));
            }
        }
        boolean editable = selected != null && selected.editable();
        edit.active = delete.active = visible.active = editable;
        share.active = selected != null;
        visible.setMessage(Component.literal(selected != null && !selected.visible() ? "Show" : "Hide"));
        previous.active = page > 0; next.active = (page + 1) * perPage < filtered.size();
    }
    private int defaultColor(WaypointData point) { return net.minecraft.util.ARGB.setBrightness(net.minecraft.util.ARGB.color(255, UUID.fromString(point.uuid()).hashCode()), 0.9f) & 0xffffff; }
    @Override public void tick() { if (seenRevision != WaypointClient.revision) update(); }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        page += vertical < 0 ? 1 : -1; update(); return true;
    }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(font, filtered.size() + " waypoints  •  " + (page + 1) + "/" + Math.max(1, (filtered.size() + perPage - 1) / perPage), width / 2, height - 56, 0xffa8b4c2);
        if (filtered.isEmpty()) graphics.centeredText(font, !WaypointClient.supported() ? "Join a world to manage its waypoints." : !WaypointClient.ready ? "Loading waypoints…" : "No waypoints found. Use New to create one.", width / 2, 80, 0xffa8b4c2);
    }
}
