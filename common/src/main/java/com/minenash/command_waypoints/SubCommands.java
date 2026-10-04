package com.minenash.command_waypoints;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.waypoints.Waypoint;
import net.minecraft.world.waypoints.WaypointStyleAssets;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.mojang.brigadier.arguments.BoolArgumentType.bool;
import static com.mojang.brigadier.arguments.BoolArgumentType.getBool;
import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;
import static net.minecraft.commands.arguments.TeamColorArgument.teamColor;
import static net.minecraft.commands.arguments.TeamColorArgument.getTeamColor;
import static net.minecraft.commands.arguments.HexColorArgument.getHexColor;
import static net.minecraft.commands.arguments.HexColorArgument.hexColor;
import static net.minecraft.commands.arguments.IdentifierArgument.getId;
import static net.minecraft.commands.arguments.IdentifierArgument.id;

public class SubCommands {
    public static final SuggestionProvider<CommandSourceStack> SUGGEST_IDS = (ctx, builder) ->
        SharedSuggestionProvider.suggestResource(points(ctx).keySet(), builder);

    public static LiteralArgumentBuilder<CommandSourceStack> addSubCommands(LiteralArgumentBuilder<CommandSourceStack> original) {
        var vanillaPermission = original.getRequirement();
        // Singleplayer navigation must work without enabling cheats. Keep the
        // vanilla entity-editing branch and dedicated server access restricted.
        // Minecraft probes restrictions with a serverless compilation source
        // while serializing login commands. Mark this navigation root unrestricted
        // for that probe; real dedicated-server sources still require permission.
        var root = literal(original.getLiteral())
            .requires(source -> source.getServer() == null || source.getServer().isSingleplayer() || vanillaPermission.test(source));
        for (var child : original.getArguments()) {
            var branch = child.createBuilder();
            child.getChildren().forEach(branch::then);
            if (child.getName().equals("modify")) branch.requires(vanillaPermission);
            root.then(branch);
        }
        root
            .then(literal("add").then(argument("id", id()).executes(SubCommands::addWaypointNoArgs)))
            .then(literal("remove").then(argument("id", id()).suggests(SUGGEST_IDS).executes(SubCommands::removeWaypoint)))
            .then(literal("list").executes(SubCommands::listWaypoints));

        var destinations = argument("id", id());
        for (String dimension : new String[] {"overworld", "nether", "end"}) {
            destinations.then(literal(dimension)
                .then(argument("x", integer(-30000000, 30000000))
                    .then(argument("z", integer(-30000000, 30000000))
                        .executes(ctx -> gotoWaypoint(ctx, dimension)))));
        }
        root.then(literal("goto").then(destinations));

        return root.then(argument("id", id()).suggests(SUGGEST_IDS)
            .then(literal("visible").then(argument("visible", bool()).executes(SubCommands::setVisible)))
            .then(literal("color")
                .then(literal("hex").then(argument("hex_color", hexColor()).executes(SubCommands::modifyWayPointHexColor)))
                .then(argument("color", teamColor()).executes(SubCommands::modifyWayPointColor)))
            .then(literal("range").then(argument("range", integer(0, 60000000)).executes(SubCommands::modifyWayPointRange)))
            .then(literal("style")
                .then(literal("reset").executes(SubCommands::modifyWayPointResetStyle))
                .then(literal("set").then(argument("style", id()).executes(SubCommands::modifyWayPointStyle)))));
    }

    public static int addWaypointNoArgs(CommandContext<CommandSourceStack> ctx) {
        var p = ctx.getSource().getPosition();
        return create(ctx, ctx.getSource().getLevel(), BlockPos.containing(p.x, 0, p.z), false);
    }

    public static int gotoWaypoint(CommandContext<CommandSourceStack> ctx, String dimension) {
        var key = switch (dimension) {
            case "nether" -> net.minecraft.world.level.Level.NETHER;
            case "end" -> net.minecraft.world.level.Level.END;
            default -> net.minecraft.world.level.Level.OVERWORLD;
        };
        ServerLevel target = ctx.getSource().getServer().getLevel(key);
        if (target == null) {
            ctx.getSource().sendFailure(Component.literal("Dimension is unavailable: " + dimension));
            return 0;
        }
        return create(ctx, target, new BlockPos(getInteger(ctx, "x"), 0, getInteger(ctx, "z")), true);
    }

    private static int create(CommandContext<CommandSourceStack> ctx, ServerLevel owner, BlockPos pos, boolean moveExisting) {
        var id = getId(ctx, "id");
        var point = CommandWaypoints.points(owner).get(id);
        if (point != null && !moveExisting) {
            ctx.getSource().sendFailure(Component.translatable("commands.waypoint.static.add.already_exists", id.toString()));
            return 0;
        }
        if (point == null) point = new CommandWaypoint(UUID.randomUUID(), id, pos, new Waypoint.Icon(), 60000000, true);
        else CommandWaypoints.remove(point);
        point.pos = pos;
        point.dimension = owner.dimension().identifier().toString();
        CommandWaypoints.waypoints.computeIfAbsent(owner, level -> new HashMap<>()).put(id, point);
        save(ctx);
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.waypoint.static.add.success", id.toString()), false);
        return 1;
    }

    public static int setVisible(CommandContext<CommandSourceStack> ctx) {
        var point = point(ctx);
        if (point == null) return 0;
        point.visible = getBool(ctx, "visible");
        updateWaypoint(ctx, point);
        return 1;
    }

    public static int listWaypoints(CommandContext<CommandSourceStack> ctx) {
        var points = points(ctx);
        var message = Component.empty();
        if (points.isEmpty()) message.append("No waypoints in this dimension set.");
        else {
            var sorted = points.values().stream().sorted(java.util.Comparator.comparing(point -> point.id.toString())).toList();
            for (int i = 0; i < sorted.size(); i++) {
                if (i > 0) message.append("\n");
                message.append(sorted.get(i).listEntry());
            }
        }
        ctx.getSource().sendSuccess(() -> message, false);
        return points.size();
    }
    public static int modifyWayPointColor(CommandContext<CommandSourceStack> ctx) {
        var point = point(ctx);
        if (point == null)
            return 0;
        point.icon.color = Optional.ofNullable(getTeamColor(ctx, "color").rgb());
        updateWaypoint(ctx, point);
        return 1;
    }
    public static int modifyWayPointHexColor(CommandContext<CommandSourceStack> ctx) {
        var point = point(ctx);
        if (point == null)
            return 0;
        point.icon.color = Optional.of(getHexColor(ctx, "hex_color"));
        updateWaypoint(ctx, point);
        return 1;
    }
    public static int modifyWayPointRange(CommandContext<CommandSourceStack> ctx) {
        var point = point(ctx);
        if (point == null)
            return 0;
        point.range = getInteger(ctx, "range");
        updateWaypoint(ctx, point);
        return 1;
    }
    public static int modifyWayPointResetStyle(CommandContext<CommandSourceStack> ctx) {
        var point = point(ctx);
        if (point == null)
            return 0;
        point.icon.style = WaypointStyleAssets.DEFAULT;
        updateWaypoint(ctx, point);
        return 1;
    }
    public static int modifyWayPointStyle(CommandContext<CommandSourceStack> ctx) {
        var point = point(ctx);
        if (point == null)
            return 0;
        point.icon.style = ResourceKey.create(WaypointStyleAssets.ROOT_ID, getId(ctx, "style"));
        updateWaypoint(ctx, point);
        return 1;
    }

    private static CommandWaypoint point(CommandContext<CommandSourceStack> ctx) {
        var id = getId(ctx, "id");
        var point = points(ctx).get(id);
        if (point == null)
            ctx.getSource().sendFailure(Component.translatable("commands.waypoint.static.doesnt_exist", id.toString()));
        return point;
    }
    private static void updateWaypoint(CommandContext<CommandSourceStack> ctx, CommandWaypoint waypoint) {
        save(ctx);
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.waypoint.static.modify.success"), false);
    }


    public static int removeWaypoint(CommandContext<CommandSourceStack> ctx) {
        var point = point(ctx);
        if (point == null) return 0;
        CommandWaypoints.remove(point);

        save(ctx);
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.waypoint.static.remove.success"), false);
        return 1;
    }

    public static void save(CommandContext<CommandSourceStack> ctx) {
        CommandWaypoints.save();
    }

    public static Map<Identifier,CommandWaypoint> points(CommandContext<CommandSourceStack> ctx) {
        return CommandWaypoints.points(ctx.getSource().getLevel());
    }
}
