package com.minenash.command_waypoints.fabric;

import com.minenash.command_waypoints.CommandWaypoint;
import com.minenash.command_waypoints.CommandWaypoints;
import com.mojang.serialization.Codec;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("UnstableApiUsage")
public final class CommandWaypointsFabric implements ModInitializer {

    public static final AttachmentType<Map<Identifier, CommandWaypoint>> WAYPOINT_ATTACHMENT_TYPE = AttachmentRegistry.createPersistent(
        Identifier.tryBuild("command_waypoints", "points"),
        Codec.unboundedMap(Identifier.CODEC, CommandWaypoint.CODEC));

    @Override
    public void onInitialize() {

        ServerLevelEvents.LOAD.register(Identifier.tryBuild("command_waypoints","read_attachments"), (server, level) -> {
            var points = level.getAttachedOrCreate(WAYPOINT_ATTACHMENT_TYPE, HashMap::new);
            for (var point : points.values())
                level.getWaypointManager().trackWaypoint(point);

            CommandWaypoints.waypoints.put(level, new HashMap<>(points));
        });

        CommandWaypoints.init(CommandWaypointsFabric::save);
    }

    public static void save(Level level) {
        level.setAttached(WAYPOINT_ATTACHMENT_TYPE, CommandWaypoints.waypoints.get(level));
    }
}
