package com.minenash.command_waypoints.client;

import com.google.gson.*;
import com.minenash.command_waypoints.*;
import com.minenash.command_waypoints.fabric.WaypointPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;
import java.util.*;
import java.util.function.Consumer;

public final class WaypointClient implements ClientModInitializer {
    public static List<WaypointData> points = List.of();
    public static List<String> dimensions = List.of("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end");
    private static final Map<String, Consumer<String>> pending = new HashMap<>();
    public static KeyMapping openKey, addKey;
    public static boolean ready;
    public static int revision;
    @Override public void onInitializeClient() {
        WaypointSettings.load();
        var category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("command_waypoints", "waypoints"));
        openKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.command_waypoints.open", com.mojang.blaze3d.platform.InputConstants.KEY_N, category));
        addKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.command_waypoints.add", com.mojang.blaze3d.platform.InputConstants.KEY_B, category));
        ClientPlayNetworking.registerGlobalReceiver(WaypointPayload.TYPE, (payload, context) -> context.client().execute(() -> {
            JsonObject object = JsonParser.parseString(payload.json()).getAsJsonObject();
            points = Arrays.asList(WaypointData.GSON.fromJson(object.get("points"), WaypointData[].class));
            dimensions = Arrays.asList(WaypointData.GSON.fromJson(object.get("dimensions"), String[].class));
            ready = true; revision++;
            var callback = pending.remove(object.get("request").getAsString());
            if (callback != null) callback.accept(object.get("error").getAsString());
        }));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            points = List.of(); pending.clear(); ready = false; revision++;
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player != null && client.gui.screen() == null) {
                while (openKey.consumeClick()) client.gui.setScreen(new WaypointManagerScreen(null));
                while (addKey.consumeClick()) client.gui.setScreen(new WaypointEditorScreen(new WaypointManagerScreen(null), null));
            }
        });
    }
    public static boolean supported() {
        return Minecraft.getInstance().getConnection() != null && ClientPlayNetworking.canSend(WaypointPayload.TYPE);
    }
    public static void request(JsonObject object, Consumer<String> callback) {
        if (!supported()) { if (callback != null) callback.accept("Join a world with Command Waypoints installed to manage waypoints."); return; }
        String id = UUID.randomUUID().toString(); object.addProperty("request", id);
        if (callback != null) pending.put(id, callback);
        ClientPlayNetworking.send(new WaypointPayload(object.toString()));
    }
    public static JsonObject action(String action) { var object = new JsonObject(); object.addProperty("action", action); return object; }
    public static void refresh() { request(action("list"), null); }
    public static WaypointData defaults() {
        var client = Minecraft.getInstance();
        return new WaypointData("", "", "", client.player == null ? 0 : (int)Math.floor(client.player.getX()),
            client.player == null ? 0 : (int)Math.floor(client.player.getZ()),
            client.level == null ? "minecraft:overworld" : client.level.dimension().identifier().toString(),
            null, "minecraft:default", Integer.MAX_VALUE, true, true, true);
    }
}
