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
        System.out.println("Waypoint login command-packet checks passed");
    }
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
