package com.minenash.command_waypoints;

/** Horizontal coordinates; the End and custom dimensions never share waypoints. */
public final class WaypointCoordinates {
    private WaypointCoordinates() {}

    public static boolean shares(String source, String destination) {
        return source.equals(destination) || isPortalDimension(source) && isPortalDimension(destination);
    }

    private static boolean isPortalDimension(String dimension) {
        return dimension.equals("minecraft:overworld") || dimension.equals("minecraft:the_nether");
    }

    public static double scale(String source, String destination) {
        if (!shares(source, destination))
            throw new IllegalArgumentException("Waypoints cannot cross these dimensions");
        if (source.equals(destination)) return 1;
        return source.equals("minecraft:the_nether") ? 8 : 1.0 / 8;
    }

    public static double distanceSquared(double x, double z, double receiverX, double receiverZ) {
        double dx = x - receiverX, dz = z - receiverZ;
        return dx * dx + dz * dz;
    }

    public static float azimuth(double x, double z, double receiverX, double receiverZ) {
        // Vanilla's receiver-minus-source vector rotated clockwise by 90 degrees.
        return (float) Math.atan2(receiverX - x, z - receiverZ);
    }
}
