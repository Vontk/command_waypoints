package com.minenash.command_waypoints.fabric;

import com.google.gson.*;
import com.minenash.command_waypoints.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.resources.*;
import net.minecraft.network.chat.*;
import net.minecraft.ChatFormatting;
import java.util.*;

public final class WaypointNetworking {
    private static final Map<UUID, Shared> shares = new LinkedHashMap<>();
    private static final Map<UUID, Long> lastShare = new HashMap<>();
    private record Shared(WaypointData point, Set<UUID> recipients, long expires) {}
    public static void init() {
        PayloadTypeRegistry.serverboundPlay().register(WaypointPayload.TYPE, WaypointPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(WaypointPayload.TYPE, WaypointPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(WaypointPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> handle(context.player(), payload.json()));
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> send(handler.player, "", ""));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 40 == 0) server.getPlayerList().getPlayers().forEach(p -> send(p, "", ""));
        });
        CommandWaypoints.onChange = () -> CommandWaypoints.waypoints.keySet().stream().findFirst().ifPresent(level -> {
            if (level instanceof net.minecraft.server.level.ServerLevel serverLevel)
                serverLevel.getServer().getPlayerList().getPlayers().forEach(p -> send(p, "", ""));
        });
    }
    public static void clear() { shares.clear(); lastShare.clear(); }
    public static boolean editable(ServerPlayer player, CommandWaypoint point) {
        return point.owner != null ? point.owner.equals(player.getUUID()) : player.level().getServer().isSingleplayer()
            || player.createCommandSourceStack().permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER);
    }
    public static List<CommandWaypoint> accessible(ServerPlayer player) {
        return CommandWaypoints.all(p -> p.owner == null || p.owner.equals(player.getUUID()));
    }
    public static String uniqueName(Collection<CommandWaypoint> existing, String name, String dimension) {
        var names = existing.stream().filter(p -> WaypointCoordinates.shares(p.dimension, dimension)).map(CommandWaypoint::displayName).collect(java.util.stream.Collectors.toSet());
        if (name.isBlank()) {
            for (int n = 1; ; n++) if (!names.contains("waypoint" + n)) return "waypoint" + n;
        }
        if (names.contains(name)) throw new IllegalArgumentException("A waypoint with that name already exists in this dimension set.");
        return name;
    }
    public static CommandWaypoint save(ServerPlayer player, WaypointData data) {
        WaypointData.validate(data);
        var level = player.level().getServer().getLevel(ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, Identifier.parse(data.dimension())));
        if (level == null) throw new IllegalArgumentException("That dimension is unavailable.");
        CommandWaypoint previous = data.uuid() == null || data.uuid().isEmpty() ? null : find(player, data.uuid());
        if (previous != null && !editable(player, previous)) throw new IllegalArgumentException("Only an operator can edit a legacy shared waypoint.");
        var existing = accessible(player).stream().filter(p -> p != previous).toList();
        String name = uniqueName(existing, data.name().trim(), data.dimension());
        Identifier id = previous == null ? Identifier.tryParse(name) : previous.id;
        if (id == null || previous != null && !previous.displayName().equals(name)) {
            // Readable names may contain spaces; an internal identifier keeps command compatibility.
            String slug = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_./-]", "_");
            if (slug.isBlank()) slug = "waypoint";
            id = Identifier.withDefaultNamespace(slug);
            int suffix = 2;
            while (idExists(existing, id, data.dimension())) id = Identifier.withDefaultNamespace(slug + "_" + suffix++);
        } else if (idExists(existing, id, data.dimension())) {
            String path = id.getPath(); String namespace = id.getNamespace(); int suffix = 2;
            while (idExists(existing, id, data.dimension())) id = Identifier.fromNamespaceAndPath(namespace, path + "_" + suffix++);
        }
        if (previous == null && accessible(player).stream().filter(p -> p.owner != null).count() >= 512)
            throw new IllegalArgumentException("You can save up to 512 personal waypoints.");
        var replacement = data.newPoint(id, name, previous == null ? player.getUUID() : previous.owner);
        if (previous != null) {
            var updated = new CommandWaypoint(previous.uuid, replacement.id, replacement.pos, replacement.icon, replacement.range, replacement.visible);
            updated.name = replacement.name; updated.owner = replacement.owner; replacement = updated;
            CommandWaypoints.remove(previous);
        }
        CommandWaypoints.put(level, replacement);
        CommandWaypoints.save();
        return replacement;
    }
    private static boolean idExists(List<CommandWaypoint> points, Identifier id, String dimension) {
        return points.stream().anyMatch(p -> p.id.equals(id) && WaypointCoordinates.shares(p.dimension, dimension));
    }
    public static CommandWaypoint find(ServerPlayer player, String uuid) {
        return accessible(player).stream().filter(p -> p.uuid.toString().equals(uuid)).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Waypoint no longer exists."));
    }
    private static void handle(ServerPlayer player, String json) {
        String request = "";
        try {
            if (json.length() > 8192) throw new IllegalArgumentException("Request too large.");
            JsonObject object = JsonParser.parseString(json).getAsJsonObject();
            request = object.has("request") ? object.get("request").getAsString() : "";
            switch (object.get("action").getAsString()) {
                case "list" -> { }
                case "save" -> save(player, WaypointData.GSON.fromJson(object.get("point"), WaypointData.class));
                case "delete" -> { var point = find(player, object.get("uuid").getAsString());
                    if (!editable(player, point)) throw new IllegalArgumentException("You cannot edit this waypoint.");
                    CommandWaypoints.remove(point); CommandWaypoints.save(); }
                case "visible" -> { var point = find(player, object.get("uuid").getAsString());
                    if (!editable(player, point)) throw new IllegalArgumentException("You cannot edit this waypoint.");
                    point.visible = object.get("visible").getAsBoolean(); CommandWaypoints.save(); }
                case "share" -> share(player, find(player, object.get("uuid").getAsString()), object.get("recipient").getAsString());
                default -> throw new IllegalArgumentException("Unknown waypoint action.");
            }
            send(player, request, "");
        } catch (RuntimeException error) { send(player, request, error instanceof IllegalArgumentException ? error.getMessage() : "Invalid waypoint request."); }
    }
    public static Component shareMessage(String sender, WaypointData data, UUID token) {
        int color = data.color() == null ? 0x70d6ff : data.color();
        return Component.literal(sender + " shared ").withStyle(ChatFormatting.GRAY)
            .append(Component.literal(data.name()).withStyle(s -> s.withColor(color)))
            .append(Component.literal(" [" + data.x() + " " + data.z() + " | " + WaypointData.dimensionName(data.dimension()) + "] ").withStyle(ChatFormatting.GRAY))
            .append(Component.literal("[Add]").withStyle(s -> s.withColor(ChatFormatting.GREEN).withBold(true)
                .withClickEvent(new ClickEvent.RunCommand("/waypoint accept " + token))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal("Add a personal copy of this waypoint")))));
    }
    public static void share(ServerPlayer sender, CommandWaypoint point, String recipient) {
        long now = System.currentTimeMillis();
        if (now - lastShare.getOrDefault(sender.getUUID(), 0L) < 2000) throw new IllegalArgumentException("Please wait two seconds before sharing again.");
        var players = sender.level().getServer().getPlayerList();
        List<ServerPlayer> targets;
        if (recipient.equals("all")) targets = List.copyOf(players.getPlayers());
        else {
            var target = players.getPlayerByName(recipient);
            if (target == null) throw new IllegalArgumentException("That player is no longer online.");
            targets = List.of(target);
        }
        shares.entrySet().removeIf(e -> e.getValue().expires < now);
        while (shares.size() >= 1024) shares.remove(shares.keySet().iterator().next());
        UUID token = UUID.randomUUID();
        WaypointData data = WaypointData.from(point, false);
        shares.put(token, new Shared(data, targets.stream().map(ServerPlayer::getUUID).collect(java.util.stream.Collectors.toSet()), now + 86400000));
        lastShare.put(sender.getUUID(), now);
        var message = shareMessage(sender.getGameProfile().name(), data, token);
        targets.forEach(p -> p.sendSystemMessage(message));
        if (!targets.contains(sender)) sender.sendSystemMessage(Component.literal("Shared privately with " + recipient + ": ").append(message));
    }
    public static CommandWaypoint accept(ServerPlayer player, UUID token) {
        var shared = shares.get(token);
        if (shared == null || shared.expires < System.currentTimeMillis() || !shared.recipients.contains(player.getUUID()))
            throw new IllegalArgumentException("This share has expired or was sent to another player.");
        var data = shared.point;
        String name = data.name();
        var existing = accessible(player);
        for (int n = 2; existingName(existing, name, data.dimension()); n++) name = data.name().substring(0, Math.min(data.name().length(), 70)) + " " + n;
        var imported = new WaypointData("", "", name, data.x(), data.z(), data.dimension(), data.color(), data.style(), data.range(), data.visible(), true, true);
        var point = save(player, imported);
        shared.recipients.remove(player.getUUID());
        return point;
    }
    private static boolean existingName(List<CommandWaypoint> points, String name, String dimension) {
        return points.stream().anyMatch(p -> p.displayName().equals(name) && WaypointCoordinates.shares(p.dimension, dimension));
    }
    private static void send(ServerPlayer player, String request, String error) {
        if (!ServerPlayNetworking.canSend(player, WaypointPayload.TYPE)) return;
        var object = new JsonObject();
        object.addProperty("request", request); object.addProperty("error", error == null ? "Invalid input." : error);
        object.add("points", WaypointData.GSON.toJsonTree(accessible(player).stream().map(p -> WaypointData.from(p, editable(player, p))).toList()));
        object.add("dimensions", WaypointData.GSON.toJsonTree(player.level().getServer().levelKeys().stream().map(key -> key.identifier().toString()).sorted().toList()));
        ServerPlayNetworking.send(player, new WaypointPayload(object.toString()));
    }
}
