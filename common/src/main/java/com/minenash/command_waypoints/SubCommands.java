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
import static com.mojang.brigadier.arguments.StringArgumentType.string;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;

public class SubCommands {
    public static final SuggestionProvider<CommandSourceStack> SUGGEST_IDS = (ctx, builder) ->
        SharedSuggestionProvider.suggest(points(ctx).values().stream().map(p -> WaypointNames.commandName(p.displayName())), builder);

    public static LiteralArgumentBuilder<CommandSourceStack> addSubCommands(LiteralArgumentBuilder<CommandSourceStack> original) {
        var vanillaPermission = original.getRequirement();
        // Personal navigation is available in survival on every server. Vanilla
        // entity edits and legacy shared waypoint edits keep their permissions.
        var root = literal(original.getLiteral());
        for (var child : original.getArguments()) {
            var branch = child.createBuilder();
            child.getChildren().forEach(branch::then);
            if (child.getName().equals("modify")) branch.requires(vanillaPermission);
            root.then(branch);
        }
        root
            .then(literal("add").then(argument("id", string()).executes(SubCommands::addWaypointNoArgs)))
            .then(literal("remove").then(argument("id", string()).suggests(SUGGEST_IDS).executes(SubCommands::removeWaypoint)))
             .then(literal("list").executes(ctx -> listWaypoints(ctx, null))
                .then(argument("dimension", com.mojang.brigadier.arguments.StringArgumentType.word())
                    .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(new String[]{"overworld", "nether", "end", "all"}, builder))
                    .executes(ctx -> listWaypoints(ctx, com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "dimension")))))
            .then(literal("accept").then(argument("token", net.minecraft.commands.arguments.UuidArgument.uuid())
                .executes(ctx -> {
                    try {
                        var imported = com.minenash.command_waypoints.fabric.WaypointNetworking.accept(ctx.getSource().getPlayerOrException(), net.minecraft.commands.arguments.UuidArgument.getUuid(ctx, "token"));
                        ctx.getSource().sendSuccess(() -> Component.literal("Added " + imported.displayName()), false);
                        return 1;
                    } catch (IllegalArgumentException error) { ctx.getSource().sendFailure(Component.literal(error.getMessage())); return 0; }
                })));
        root.then(literal("share").then(argument("id", string()).suggests(SUGGEST_IDS)
            .then(argument("recipient", com.mojang.brigadier.arguments.StringArgumentType.word())
                .suggests((ctx, builder) -> { var names = new java.util.ArrayList<>(ctx.getSource().getOnlinePlayerNames()); names.add("all"); return SharedSuggestionProvider.suggest(names, builder); })
                .executes(ctx -> {
                    var point = find(ctx, points(ctx));
                    if (point == null) { ctx.getSource().sendFailure(Component.literal("Waypoint does not exist.")); return 0; }
                    try {
                        com.minenash.command_waypoints.fabric.WaypointNetworking.share(ctx.getSource().getPlayerOrException(), point, com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "recipient"));
                        return 1;
                    } catch (IllegalArgumentException error) { ctx.getSource().sendFailure(Component.literal(error.getMessage())); return 0; }
                }))));

        var destinations = argument("id", string());
        for (String dimension : new String[] {"overworld", "nether", "end"}) {
            destinations.then(literal(dimension)
                .then(argument("x", integer(-30000000, 30000000))
                    .then(argument("z", integer(-30000000, 30000000))
                        .executes(ctx -> gotoWaypoint(ctx, dimension)))));
        }
        root.then(literal("goto").then(destinations));

        return root.then(argument("id", string()).suggests(SUGGEST_IDS)
            .then(literal("visible").then(argument("visible", bool()).executes(SubCommands::setVisible)))
            .then(literal("color")
                .then(literal("hex").then(argument("hex_color", hexColor()).executes(SubCommands::modifyWayPointHexColor)))
                .then(argument("color", teamColor()).executes(SubCommands::modifyWayPointColor)))
            .then(literal("range").then(argument("range", integer(0, Integer.MAX_VALUE)).executes(SubCommands::modifyWayPointRange)))
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
        String name = getString(ctx, "id").trim();
        if (name.isEmpty() || name.length() > 80 || name.chars().anyMatch(Character::isISOControl)) {
            ctx.getSource().sendFailure(Component.literal("Name must contain 1–80 printable characters.")); return 0;
        }
        var available = CommandWaypoints.points(owner, ctx.getSource().getPlayer() == null ? null : ctx.getSource().getPlayer().getUUID());
        CommandWaypoint point;
        try { point = WaypointNames.resolve(available.values(), name); }
        catch (IllegalArgumentException error) { ctx.getSource().sendFailure(Component.literal(error.getMessage())); return 0; }
        var id = point == null ? WaypointNames.identifier(available.keySet(), name) : point.id;
        if (point != null && !moveExisting) {
            ctx.getSource().sendFailure(Component.translatable("commands.waypoint.static.add.already_exists", name));
            return 0;
        }
        if (point == null) {
            var player = ctx.getSource().getPlayer();
            if (player != null && com.minenash.command_waypoints.fabric.WaypointNetworking.accessible(player).stream().filter(p -> p.owner != null).count() >= 512) {
                ctx.getSource().sendFailure(Component.literal("You can save up to 512 personal waypoints.")); return 0;
            }
            point = new CommandWaypoint(UUID.randomUUID(), id, pos, new Waypoint.Icon(), 60000000, true);
            point.name = name;
            point.owner = ctx.getSource().getPlayer() == null ? null : ctx.getSource().getPlayer().getUUID();
        } else {
            if (!canEdit(ctx, point)) return 0;
            CommandWaypoints.remove(point);
        }
        point.pos = pos;
        point.dimension = owner.dimension().identifier().toString();
        CommandWaypoints.put(owner, point);
        save(ctx);
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.waypoint.static.add.success", name), false);
        return 1;
    }

    public static int setVisible(CommandContext<CommandSourceStack> ctx) {
        var point = point(ctx);
        if (point == null) return 0;
        point.visible = getBool(ctx, "visible");
        updateWaypoint(ctx, point);
        return 1;
    }

    public static int listWaypoints(CommandContext<CommandSourceStack> ctx) { return listWaypoints(ctx, null); }
    public static int listWaypoints(CommandContext<CommandSourceStack> ctx, String filter) {
        java.util.List<CommandWaypoint> entries;
        if (filter == null) entries = new java.util.ArrayList<>(points(ctx).values());
        else {
            String dimension = WaypointData.dimensionId(filter);
            if (!filter.equals("all") && ctx.getSource().getServer().getLevel(ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, Identifier.tryParse(dimension) == null ? Identifier.withDefaultNamespace("invalid") : Identifier.parse(dimension))) == null) {
                ctx.getSource().sendFailure(Component.literal("Unknown dimension: " + filter)); return 0;
            }
            var player = ctx.getSource().getPlayer();
            entries = CommandWaypoints.all(p -> (p.owner == null || player != null && p.owner.equals(player.getUUID()))
                && (filter.equals("all") || p.dimension.equals(dimension)));
        }
        var message = Component.empty();
        if (entries.isEmpty()) message.append("No waypoints in this dimension set.");
        else {
            var sorted = entries.stream().sorted(java.util.Comparator.comparing(point -> point.id.toString())).toList();
            for (int i = 0; i < sorted.size(); i++) {
                if (i > 0) message.append("\n");
                message.append(sorted.get(i).listEntry(filter == null || filter.equals("all")));
            }
        }
        ctx.getSource().sendSuccess(() -> message, false);
        return entries.size();
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
        var point = find(ctx, points(ctx));
        if (point == null)
            ctx.getSource().sendFailure(Component.translatable("commands.waypoint.static.doesnt_exist", getString(ctx, "id")));
        return point != null && canEdit(ctx, point) ? point : null;
    }
    private static CommandWaypoint find(CommandContext<CommandSourceStack> ctx, Map<Identifier, CommandWaypoint> available) {
        try { return WaypointNames.resolve(available.values(), getString(ctx, "id")); }
        catch (IllegalArgumentException error) { ctx.getSource().sendFailure(Component.literal(error.getMessage())); return null; }
    }
    private static boolean canEdit(CommandContext<CommandSourceStack> ctx, CommandWaypoint point) {
        var player = ctx.getSource().getPlayer();
        boolean allowed = player == null || com.minenash.command_waypoints.fabric.WaypointNetworking.editable(player, point);
        if (!allowed) ctx.getSource().sendFailure(Component.literal("Only an operator can edit a legacy shared waypoint."));
        return allowed;
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
        return CommandWaypoints.points(ctx.getSource().getLevel(), ctx.getSource().getPlayer() == null ? null : ctx.getSource().getPlayer().getUUID());
    }
}
