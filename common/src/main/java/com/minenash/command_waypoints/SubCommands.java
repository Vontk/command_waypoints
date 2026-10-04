package com.minenash.command_waypoints;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.world.scores.TeamColor;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.waypoints.Waypoint;
import net.minecraft.world.waypoints.WaypointStyleAssets;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static java.lang.Math.floor;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;
import static net.minecraft.commands.arguments.TeamColorArgument.teamColor;
import static net.minecraft.commands.arguments.TeamColorArgument.getTeamColor;
import static net.minecraft.commands.arguments.HexColorArgument.getHexColor;
import static net.minecraft.commands.arguments.HexColorArgument.hexColor;
import static net.minecraft.commands.arguments.IdentifierArgument.getId;
import static net.minecraft.commands.arguments.IdentifierArgument.id;
import static net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos;
import static net.minecraft.commands.arguments.coordinates.BlockPosArgument.getBlockPos;

public class SubCommands {

    public static final SuggestionProvider<CommandSourceStack> SUGGEST_STATIC_IDS = (ctx, builder) ->
        SharedSuggestionProvider.suggestResource(points(ctx).keySet(), builder);

    public static LiteralArgumentBuilder<CommandSourceStack> addSubCommands(LiteralArgumentBuilder<CommandSourceStack> original) {

        return original.then(literal("static")
            .then(literal("add")
                .then(argument("id", id()).executes(SubCommands::addWaypointNoArgs)
                    .then(argument("location", blockPos()).executes(SubCommands::addWaypointPos)
                        .then(literal("color")
                            .then(literal("hex").then(argument("color", hexColor()).executes(SubCommands::addWaypoint)
                                .then(literal("style").then(argument("style", id()).executes(SubCommands::addWaypoint)
                                    .then(literal("range").then(argument("range", integer(0, 60000000)).executes(SubCommands::addWaypoint)))))
                                .then(literal("range").then(argument("range", integer(0, 60000000)).executes(SubCommands::addWaypoint)
                                    .then(literal("style").then(argument("style", id()).executes(SubCommands::addWaypoint)))))
                            ))
                            .then(argument("color", teamColor()).executes(SubCommands::addWaypoint)
                                .then(literal("style").then(argument("style", id()).executes(SubCommands::addWaypoint)
                                    .then(literal("range").then(argument("range", integer(0, 60000000)).executes(SubCommands::addWaypoint)))))
                                .then(literal("range").then(argument("range", integer(0, 60000000)).executes(SubCommands::addWaypoint)
                                    .then(literal("style").then(argument("style", id()).executes(SubCommands::addWaypoint)))))
                            ))
                        .then(literal("style").then(argument("style", id()).executes(SubCommands::addWaypoint)
                            .then(literal("color")
                                .then(literal("hex").then(argument("color", hexColor()).executes(SubCommands::addWaypoint)
                                    .then(literal("range").then(argument("range", integer(0, 60000000)).executes(SubCommands::addWaypoint)))))
                                .then(argument("color", teamColor()).executes(SubCommands::addWaypoint)
                                    .then(literal("range").then(argument("range", integer(0, 60000000)).executes(SubCommands::addWaypoint)))))
                            .then(literal("range").then(argument("range", integer(0, 60000000)).executes(SubCommands::addWaypoint)
                                .then(literal("color")
                                    .then(literal("hex").then(argument("color", hexColor()).executes(SubCommands::addWaypoint)))
                                    .then(argument("color", teamColor()).executes(SubCommands::addWaypoint)))
                            ))))
                        .then(literal("range").then(argument("range", integer(0, 60000000)).executes(SubCommands::addWaypoint)
                            .then(literal("color")
                                .then(literal("hex").then(argument("color", hexColor()).executes(SubCommands::addWaypoint)
                                    .then(literal("style").then(argument("style", id()).executes(SubCommands::addWaypoint)))))
                                .then(argument("color", teamColor()).executes(SubCommands::addWaypoint)
                                    .then(literal("style").then(argument("style", id()).executes(SubCommands::addWaypoint)))))
                            .then(literal("style").then(argument("style", id()).executes(SubCommands::addWaypoint)
                                .then(literal("color")
                                    .then(literal("hex").then(argument("color", hexColor()).executes(SubCommands::addWaypoint)))
                                    .then(argument("color", teamColor()).executes(SubCommands::addWaypoint)))
                            ))
                        ))
                    )))
            .then(literal("modify")
                .then(argument("id", id()).suggests(SUGGEST_STATIC_IDS)
                .then(literal("color")
                    .then(literal("hex").then(argument("hex_color", hexColor()).executes(SubCommands::modifyWayPointHexColor)))
                    .then(argument("color", teamColor()).executes(SubCommands::modifyWayPointColor)))
                .then(literal("range").then(argument("range", integer(0, 60000000)).executes(SubCommands::modifyWayPointRange)))
                .then(literal("location").then(argument("location", blockPos()).executes(SubCommands::modifyWayPointPos)))
                .then(literal("style")
                    .then(literal("reset").executes(SubCommands::modifyWayPointResetStyle))
                    .then(literal("set")
                        .then(argument("style", id()).executes(SubCommands::modifyWayPointStyle))))))
            .then(literal("remove")
                .then(argument("id", id()).suggests(SUGGEST_STATIC_IDS).executes(SubCommands::removeWaypoint))));
    }

    public static int addWaypoint(CommandContext<CommandSourceStack> ctx) {
        var location = getBlockPos(ctx, "location");

        var color = getArg(ctx, "color", TeamColor.class);
        var hexColor = getArg(ctx, "hex_color", Integer.class);
        var style = getArg(ctx, "style", Identifier.class);
        var range = getArg(ctx, "range", Integer.class);

        return addWaypoint(ctx, location,
            hexColor != null ? hexColor : color != null ? color.rgb() : null,
            range != null ? range : 60000000,
            style);
    }

    public static <V> V getArg(CommandContext<CommandSourceStack> ctx, String name, Class<V> clazz) {
        try { return ctx.getArgument(name, clazz); }
        catch (Exception ignored) { return null; }
    }


    public static int addWaypointNoArgs(CommandContext<CommandSourceStack> ctx) {
        var p = ctx.getSource().getPosition();
        var pos = new BlockPos((int)floor(p.x), (int)floor(p.y), (int)floor(p.z));

        return addWaypoint(ctx, pos, null, 60000000, null);
    }

    public static int addWaypointPos(CommandContext<CommandSourceStack> ctx) {
        return addWaypoint(ctx, getBlockPos(ctx, "location"), null, 60000000, null);
    }

    public static int addWaypoint(CommandContext<CommandSourceStack> ctx, BlockPos pos, Integer color, int range, Identifier style) {
        var manager = ctx.getSource().getLevel().getWaypointManager();
        var id = getId(ctx, "id");

        var point = points(ctx).get(id);
        if (point != null) {
            ctx.getSource().sendFailure(Component.translatable("commands.waypoint.static.add.already_exists", id.toString()));
            return 0;
        }

        var icon = new Waypoint.Icon();
        if (color != null)
            icon.color = Optional.of(color);
        if (style != null)
            icon.style = ResourceKey.create(WaypointStyleAssets.ROOT_ID, style);

        point = new CommandWaypoint(UUID.randomUUID(), id, pos, icon, range);
        manager.trackWaypoint(point);

        var points = points(ctx);
        points.put(id, point);
        CommandWaypoints.waypoints.put(ctx.getSource().getLevel(), points);

        save(ctx);
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.waypoint.static.add.success"), false);
        return 1;
    }

    public static int modifyWayPointPos(CommandContext<CommandSourceStack> ctx) {
        var point = point(ctx);
        if (point == null)
            return 0;
        point.pos = getBlockPos(ctx, "location");
        updateWaypoint(ctx, point);
        return 1;
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
        point.icon.color = Optional.of(getHexColor(ctx, "color"));
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
        var manager = ctx.getSource().getLevel().getWaypointManager();
        manager.untrackWaypoint(waypoint);
        manager.trackWaypoint(waypoint);
        save(ctx);
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.waypoint.static.modify.success"), false);
    }


    public static int removeWaypoint(CommandContext<CommandSourceStack> ctx) {
        var manager = ctx.getSource().getLevel().getWaypointManager();
        var id = getId(ctx, "id");

        var points = points(ctx);
        var point = points.get(id);
        if (point == null)
            return 0;

        manager.untrackWaypoint(point);
        points.remove(id);
        CommandWaypoints.waypoints.put(ctx.getSource().getLevel(), points);

        save(ctx);
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.waypoint.static.remove.success"), false);
        return 1;
    }

    public static void save(CommandContext<CommandSourceStack> ctx) {
        CommandWaypoints.saveWaypoints.accept(ctx.getSource().getLevel());
    }

    public static Map<Identifier,CommandWaypoint> points(CommandContext<CommandSourceStack> ctx) {
        return CommandWaypoints.waypoints.getOrDefault(ctx.getSource().getLevel(), new HashMap<>());
    }
}
