package com.minenash.command_waypoints;

import com.mojang.brigadier.CommandDispatcher;
import io.netty.buffer.Unpooled;
import net.minecraft.SharedConstants;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundCommandsPacket;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.permissions.PermissionSet;

import static net.minecraft.commands.Commands.literal;

/** Reproduces Minecraft's serverless login command restriction probe. */
public final class WaypointCommandPermissionsTest {
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        var dispatcher = new CommandDispatcher<CommandSourceStack>();
        var root = dispatcher.register(SubCommands.addSubCommands(literal("waypoint")
            .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
            .then(literal("modify").executes(ctx -> 1))));
        var compilation = Commands.createCompilationContext(PermissionSet.NO_PERMISSIONS);
        check(compilation.getServer() == null, "login probe must have no server");
        check(root.canUse(compilation), "navigation root must serialize as unrestricted");
        check(!root.getChild("modify").canUse(compilation), "vanilla entity editing must remain restricted");
        var operator = Commands.createCompilationContext(PermissionSet.ALL_PERMISSIONS);
        check(root.getChild("modify").canUse(operator), "operators retain vanilla commands");

        // Use Minecraft's actual packet inspector rather than imitating its probe.
        var constructor = Class.forName("net.minecraft.commands.Commands$1").getDeclaredConstructor();
        constructor.setAccessible(true);
        @SuppressWarnings("unchecked")
        var inspector = (ClientboundCommandsPacket.NodeInspector<CommandSourceStack>) constructor.newInstance();
        var packet = new ClientboundCommandsPacket(dispatcher.getRoot(), inspector);
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            ClientboundCommandsPacket.STREAM_CODEC.encode(buffer, packet);
            ClientboundCommandsPacket.STREAM_CODEC.decode(buffer);
        } finally { buffer.release(); }
        // Match vanilla's actual vector rotation instead of repeating our formula.
        for (double[] sample : new double[][] {{100, 200, 0, 0}, {-100, 200, 11, -40}, {100, -200, -30, 70}, {-100, -200, 90, 70}, {20, 30, 20, 50}}) {
            var delta = new net.minecraft.world.phys.Vec3(sample[2] - sample[0], 0, sample[3] - sample[1]).rotateClockwise90();
            double expected = Math.atan2(delta.z, delta.x);
            double actual = WaypointCoordinates.azimuth(sample[0], sample[1], sample[2], sample[3]);
            check(Math.abs(expected - actual) < 0.000001, "bearing matches vanilla at moving receiver positions");
        }
        var waypoint = new CommandWaypoint(java.util.UUID.randomUUID(), net.minecraft.resources.Identifier.parse("home"),
            new net.minecraft.core.BlockPos(-101, 0, 203), new net.minecraft.world.waypoints.Waypoint.Icon(), 100, true);
        waypoint.dimension = "minecraft:the_nether";
        waypoint.icon.color = java.util.Optional.of(0xff12ab34);
        var entry = waypoint.listEntry();
        check(entry.getString().equals("home | X: -101 Z: 203 | Nether"), "list includes name, X/Z, and source dimension");
        check(entry.getSiblings().getFirst().getStyle().getColor().getValue() == 0x12ab34, "list name uses waypoint RGB color");
        waypoint.icon.color = java.util.Optional.empty();
        int vanillaDefault = net.minecraft.util.ARGB.setBrightness(net.minecraft.util.ARGB.color(255, waypoint.uuid.hashCode()), 0.9f) & 0xffffff;
        check(waypoint.listEntry().getSiblings().getFirst().getStyle().getColor().getValue() == vanillaDefault,
            "unset color matches vanilla locator's UUID-derived color");
        waypoint.visible = false;
        check(waypoint.listEntry().getString().endsWith(" (hidden)"), "list retains hidden status");
        System.out.println("Waypoint login, bearing, and colored-list checks passed");
    }
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
