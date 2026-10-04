package com.minenash.command_waypoints;

import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public final class CommandWaypoints {

    public static Map<Level,Map<Identifier, CommandWaypoint>> waypoints = new HashMap<>();
    public static Consumer<Level> saveWaypoints;

    public static void init(Consumer<Level> saveWaypoints) {
        CommandWaypoints.saveWaypoints = saveWaypoints;
    }
}
