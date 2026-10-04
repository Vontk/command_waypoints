package com.minenash.command_waypoints;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.resources.Identifier;
import java.util.Collection;
import java.util.Locale;

/** Human-readable command names with legacy identifier compatibility. */
public final class WaypointNames {
    private WaypointNames() {}
    public static String commandName(String name) { return StringArgumentType.escapeIfRequired(name); }
    public static CommandWaypoint resolve(Collection<CommandWaypoint> points, String name) {
        var exact = points.stream().filter(p -> p.displayName().equals(name)).toList();
        if (exact.size() == 1) return exact.getFirst();
        if (exact.size() > 1) throw new IllegalArgumentException("Waypoint name is ambiguous; use its identifier from the manager.");
        Identifier id = Identifier.tryParse(name.toLowerCase(Locale.ROOT));
        if (id == null) return null;
        var aliases = points.stream().filter(p -> p.id.equals(id)).toList();
        if (aliases.size() > 1) throw new IllegalArgumentException("Waypoint identifier is ambiguous; use its display name.");
        return aliases.isEmpty() ? null : aliases.getFirst();
    }
    public static Identifier identifier(Collection<Identifier> existing, String name) {
        var parsed = Identifier.tryParse(name);
        if (parsed != null && !existing.contains(parsed)) return parsed;
        String slug = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_./-]", "_");
        if (slug.isBlank()) slug = "waypoint";
        var id = Identifier.withDefaultNamespace(slug);
        for (int n = 2; existing.contains(id); n++) id = Identifier.withDefaultNamespace(slug + "_" + n);
        return id;
    }
}
