package com.minenash.command_waypoints.client;

import com.minenash.command_waypoints.WaypointData;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.*;
import java.io.IOException;

public final class WaypointSettings {
    public double growthDistance = 200;
    public double minimumSize = 6;
    public double maximumSize = 14;
    public static WaypointSettings INSTANCE = new WaypointSettings();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("command_waypoints.json");
    public static void load() {
        if (Files.exists(PATH)) try {
            var settings = WaypointData.GSON.fromJson(Files.readString(PATH), WaypointSettings.class);
            settings.validate(); INSTANCE = settings;
        } catch (IOException | RuntimeException exception) {
            org.slf4j.LoggerFactory.getLogger("command_waypoints").warn("Cannot load waypoint display settings; using defaults", exception);
        }
    }
    public void validate() {
        if (!Double.isFinite(growthDistance) || growthDistance <= 0 || growthDistance > 60000000)
            throw new IllegalArgumentException("Growth distance must be greater than zero and at most 60000000.");
        if (!Double.isFinite(minimumSize) || !Double.isFinite(maximumSize) || minimumSize < 2 || maximumSize > 32 || maximumSize < minimumSize)
            throw new IllegalArgumentException("Icon sizes must be 2–32 pixels, with maximum ≥ minimum.");
    }
    public double size(double distance) {
        return minimumSize + (maximumSize - minimumSize) * Math.clamp(1 - distance / growthDistance, 0, 1);
    }
    public void save() throws IOException {
        validate(); Files.createDirectories(PATH.getParent());
        Path temporary = PATH.resolveSibling("command_waypoints.json.tmp");
        Files.writeString(temporary, WaypointData.GSON.toJson(this));
        Files.move(temporary, PATH, StandardCopyOption.REPLACE_EXISTING);
        INSTANCE = this;
    }
}
