package com.minenash.command_waypoints;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public final class CommandWaypoints {
    public static final Map<Level, Map<Identifier, CommandWaypoint>> waypoints = new HashMap<>();
    private static final Map<ServerLevel, Set<CommandWaypoint>> tracked = new HashMap<>();
    public static Consumer<Level> saveWaypoints;
    public static Runnable onChange = () -> {};

    public static void init(Consumer<Level> save) { saveWaypoints = save; }

    public static void load(ServerLevel level, Map<Identifier, CommandWaypoint> points) {
        points.values().forEach(point -> point.dimension = level.dimension().identifier().toString());
        waypoints.put(level, new HashMap<>(points));
        refresh();
    }

    public static java.util.List<CommandWaypoint> all(UUIDFilter filter) {
        return waypoints.values().stream().flatMap(points -> points.values().stream())
            .filter(point -> filter.accept(point)).toList();
    }

    @FunctionalInterface public interface UUIDFilter { boolean accept(CommandWaypoint point); }

    public static Map<Identifier, CommandWaypoint> points(ServerLevel level) { return points(level, null); }

    public static Map<Identifier, CommandWaypoint> points(ServerLevel level, java.util.UUID player) {
        Map<Identifier, CommandWaypoint> result = new HashMap<>();
        var accessible = all(p -> (p.owner == null || p.owner.equals(player))
            && WaypointCoordinates.shares(p.dimension, level.dimension().identifier().toString()));
        accessible.stream().filter(p -> p.owner == null).forEach(p -> result.put(p.id, p));
        accessible.stream().filter(p -> p.owner == null && p.dimension.equals(level.dimension().identifier().toString()))
            .forEach(p -> result.put(p.id, p));
        accessible.stream().filter(p -> p.owner != null).forEach(p -> result.put(p.id, p));
        return result;
    }

    public static void put(ServerLevel level, CommandWaypoint point) {
        point.dimension = level.dimension().identifier().toString();
        Identifier storageId = point.owner == null ? point.id
            : Identifier.fromNamespaceAndPath("command_waypoints", point.uuid.toString());
        waypoints.computeIfAbsent(level, ignored -> new HashMap<>()).put(storageId, point);
    }

    public static void refresh() {
        tracked.forEach((level, points) -> points.forEach(level.getWaypointManager()::untrackWaypoint));
        tracked.clear();
        waypoints.forEach((level, points) -> {
            if (level instanceof ServerLevel serverLevel) {
                Set<CommandWaypoint> shared = new HashSet<>();
                waypoints.forEach((owner, owned) -> {
                    if (WaypointCoordinates.shares(owner.dimension().identifier().toString(), level.dimension().identifier().toString()))
                        owned.values().stream().filter(point -> point.visible).forEach(shared::add);
                });
                shared.forEach(serverLevel.getWaypointManager()::trackWaypoint);
                tracked.put(serverLevel, shared);
            }
        });
    }

    public static void save() {
        refresh();
        waypoints.keySet().forEach(saveWaypoints);
        onChange.run();
    }

    public static void remove(CommandWaypoint point) {
        waypoints.values().forEach(points -> points.values().removeIf(value -> value == point));
    }

    public static void clear() { tracked.clear(); waypoints.clear(); }
}
