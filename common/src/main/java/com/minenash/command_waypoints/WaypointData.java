package com.minenash.command_waypoints;

import com.google.gson.Gson;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.waypoints.Waypoint;
import net.minecraft.world.waypoints.WaypointStyleAssets;
import java.util.UUID;
import java.util.Optional;

/** Wire/editor values. No world objects or client classes cross the network. */
public record WaypointData(String uuid, String id, String name, int x, int z, String dimension,
                           Integer color, String style, int range, boolean visible, boolean editable, boolean personal) {
    public static final Gson GSON = new Gson();
    public static String dimensionName(String dimension) {
        return switch (dimension) {
            case "minecraft:overworld", "overworld" -> "Overworld";
            case "minecraft:the_nether", "nether" -> "Nether";
            case "minecraft:the_end", "end" -> "End";
            case "all" -> "All dimensions";
            default -> dimension;
        };
    }
    public static String dimensionId(String dimension) {
        return switch (dimension) {
            case "overworld" -> "minecraft:overworld";
            case "nether" -> "minecraft:the_nether";
            case "end" -> "minecraft:the_end";
            default -> dimension;
        };
    }
    public static WaypointData from(CommandWaypoint p, boolean editable) {
        return new WaypointData(p.uuid.toString(), p.id.toString(), p.displayName(), p.pos.getX(), p.pos.getZ(),
            p.dimension, p.icon.color.map(rgb -> rgb & 0xffffff).orElse(null), p.icon.style.identifier().toString(), p.range, p.visible, editable, p.owner != null);
    }
    public static void validate(WaypointData data) {
        if (data.name == null || data.name.length() > 80 || data.name.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Name must contain at most 80 printable characters.");
        if (Math.abs((long)data.x) > 30000000 || Math.abs((long)data.z) > 30000000)
            throw new IllegalArgumentException("Coordinates must be between -30000000 and 30000000.");
        if (data.range < 0) throw new IllegalArgumentException("Range must be nonnegative; Infinite uses the maximum integer.");
        if (data.color != null && (data.color < 0 || data.color > 0xffffff)) throw new IllegalArgumentException("Use a six-digit RGB color.");
        if (data.dimension == null || Identifier.tryParse(data.dimension) == null || data.style == null || Identifier.tryParse(data.style) == null)
            throw new IllegalArgumentException("Dimension and icon style must be valid Minecraft identifiers.");
    }
    public CommandWaypoint newPoint(Identifier identifier, String resolvedName, UUID owner) {
        var icon = new Waypoint.Icon();
        icon.color = Optional.ofNullable(color);
        icon.style = ResourceKey.create(WaypointStyleAssets.ROOT_ID, Identifier.parse(style));
        var point = new CommandWaypoint(UUID.randomUUID(), identifier, new BlockPos(x, 0, z), icon, range, visible);
        point.name = resolvedName;
        point.dimension = dimension;
        point.owner = owner;
        return point;
    }
}
