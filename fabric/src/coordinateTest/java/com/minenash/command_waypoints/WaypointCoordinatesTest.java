package com.minenash.command_waypoints;

public final class WaypointCoordinatesTest {
    public static void main(String[] args) {
        String overworld = "minecraft:overworld", nether = "minecraft:the_nether", end = "minecraft:the_end";
        check(WaypointCoordinates.shares(overworld, nether), "portal dimensions share waypoints");
        check(WaypointCoordinates.shares(end, end), "End shares with itself");
        check(!WaypointCoordinates.shares(end, overworld), "End excluded from Overworld");
        check(!WaypointCoordinates.shares(overworld, end), "Overworld excluded from End");
        check(!WaypointCoordinates.shares(end, nether), "End excluded from Nether");
        check(!WaypointCoordinates.shares(nether, end), "Nether excluded from End");
        check(!WaypointCoordinates.shares(overworld, "example:custom"), "custom dimensions isolated");
        equal(8, WaypointCoordinates.scale(nether, overworld));
        equal(0.125, WaypointCoordinates.scale(overworld, nether));
        equal(1, WaypointCoordinates.scale(end, end));
        equal(-0.125, -1 * WaypointCoordinates.scale(overworld, nether));
        equal(-808, -101 * WaypointCoordinates.scale(nether, overworld));
        equal(101, 101 * WaypointCoordinates.scale(overworld, nether) * WaypointCoordinates.scale(nether, overworld));
        try {
            WaypointCoordinates.scale(end, overworld);
            throw new AssertionError("cross-End conversion must fail");
        } catch (IllegalArgumentException expected) {}
        equal(25, WaypointCoordinates.distanceSquared(3, 4, 0, 0));
        equal(Math.PI / 2, WaypointCoordinates.azimuth(1, 0, 0, 0));
        equal(0, WaypointCoordinates.azimuth(0, -1, 0, 0));
        equal(-Math.PI / 2, WaypointCoordinates.azimuth(-1, 0, 0, 0));
        System.out.println("Waypoint coordinate checks passed");
    }

    private static void equal(double expected, double actual) {
        check(Math.abs(expected - actual) < 0.000001, "expected " + expected + ", got " + actual);
    }
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
