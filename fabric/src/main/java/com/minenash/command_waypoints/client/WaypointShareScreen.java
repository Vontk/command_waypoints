package com.minenash.command_waypoints.client;

import com.minenash.command_waypoints.WaypointData;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.network.chat.Component;
import java.util.*;

public final class WaypointShareScreen extends WaypointScreen {
    private final WaypointData point;
    private String query = "", recipient = "";
    private int page;
    private boolean privateShare, sending;
    private List<String> players = List.of();
    private final List<Button> rows = new ArrayList<>();
    private EditBox search;
    private Button send, previous, next;
    private int perPage;
    public WaypointShareScreen(Screen parent, WaypointData point) { super(parent, "Share waypoint"); this.point = point; }
    @Override protected void init() {
        super.init(); rows.clear();
        button("Public chat", left, 61, panelWidth / 2 - 3, b -> { privateShare = false; update(); });
        button("Specific player", left + panelWidth / 2 + 3, 61, panelWidth / 2 - 3, b -> { privateShare = true; update(); });
        search = field("Search online players", query, left, 88, panelWidth);
        search.setHint(Component.literal("Search online players…"));
        search.setResponder(value -> { query = value; page = 0; update(); });
        perPage = Math.max(1, (height - 190) / 24);
        for (int n = 0; n < perPage; n++) {
            int index = n;
            rows.add(button("", left, 116 + n * 24, panelWidth, b -> { recipient = players.get(page * perPage + index); update(); }));
        }
        previous = button("<", left, height - 65, 28, b -> { page--; update(); });
        next = button(">", left + panelWidth - 28, height - 65, 28, b -> { page++; update(); });
        button("Cancel", left, height - 28, panelWidth / 2 - 3, b -> onClose());
        send = button("Share publicly", left + panelWidth / 2 + 3, height - 28, panelWidth / 2 - 3, b -> {
            var object = WaypointClient.action("share"); object.addProperty("uuid", point.uuid()); object.addProperty("recipient", privateShare ? recipient : "all");
            sending = true; update();
            WaypointClient.request(object, message -> { sending = false; error = message; update(); if (message.isEmpty() && minecraft.gui.screen() == this) onClose(); });
        });
        update();
    }
    private void update() {
        players = minecraft.getConnection() == null ? List.of() : minecraft.getConnection().getOnlinePlayers().stream()
            .map(info -> info.getProfile().name()).filter(name -> name.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT)))
            .sorted(String.CASE_INSENSITIVE_ORDER).toList();
        page = Math.clamp(page, 0, Math.max(0, (players.size() - 1) / perPage));
        search.visible = privateShare;
        for (int n = 0; n < rows.size(); n++) {
            int index = page * perPage + n; var row = rows.get(n); row.visible = privateShare && index < players.size();
            if (row.visible) row.setMessage(Component.literal((players.get(index).equals(recipient) ? "> " : "") + players.get(index)));
        }
        previous.visible = next.visible = privateShare;
        previous.active = page > 0; next.active = (page + 1) * perPage < players.size();
        send.active = !sending && WaypointClient.supported() && (!privateShare || minecraft.getConnection().getPlayerInfo(recipient) != null);
        send.setMessage(Component.literal(privateShare ? "Send privately" : "Share publicly"));
    }
    @Override public void tick() { update(); }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(font, Component.literal(point.name() + " | " + point.x() + " " + point.z() + " | " + WaypointData.dimensionName(point.dimension())), width / 2, 42, 0xffffffff);
        if (!privateShare) {
            graphics.centeredText(font, "Everyone online will see your waypoint in chat.", width / 2, 114, 0xffc6cbd1);
            graphics.centeredText(font, "They can click [Add] to save their own copy.", width / 2, 135, 0xff76df91);
        } else {
            graphics.centeredText(font, recipient.isEmpty() ? "Select a recipient" : "To: " + recipient, width / 2, height - 59, 0xffc6cbd1);
            if (players.isEmpty()) graphics.centeredText(font, "No matching online players.", width / 2, 122, 0xffc6cbd1);
        }
    }
}
