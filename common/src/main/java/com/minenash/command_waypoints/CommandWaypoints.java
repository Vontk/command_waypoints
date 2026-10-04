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

    public static void init(Consumer<Level> save) { saveWaypoints = save; }

    public static void load(ServerLevel level, Map<Identifier, CommandWaypoint> points) {
        points.values().forEach(point -> point.dimension = level.dimension().identifier().toString());
        waypoints.put(level, new HashMap<>(points));
        refresh();
    }

    public static Map<Identifier, CommandWaypoint> points(ServerLevel level) {
        Map<Identifier, CommandWaypoint> result = new HashMap<>();
        waypoints.forEach((owner, points) -> {
            if (WaypointCoordinates.shares(owner.dimension().identifier().toString(), level.dimension().identifier().toString()))
                result.putAll(points);
        });
        // Preserve access to legacy same-name waypoints in their owning dimension.
        result.putAll(waypoints.getOrDefault(level, Map.of()));
        return result;
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
    }

    public static void remove(CommandWaypoint point) {
        waypoints.values().forEach(points -> points.values().removeIf(value -> value == point));
    }

    public static void clear() { tracked.clear(); waypoints.clear(); }
}
