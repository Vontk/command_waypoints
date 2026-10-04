package com.minenash.command_waypoints.test;
import com.minenash.command_waypoints.*;
import com.minenash.command_waypoints.fabric.WaypointNetworking;
import com.mojang.authlib.GameProfile;
import com.mojang.serialization.JsonOps;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import net.minecraft.world.waypoints.Waypoint;
import java.util.*;
public final class ServerChecks implements ModInitializer {
    private int peerPhase, nextPeerTick;
    @Override public void onInitialize() {
        var scheduled = new HashMap<ServerPlayer, Integer>();
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (handler.player.getGameProfile().name().equals("WaypointTester")) scheduled.put(handler.player, server.getTickCount() + 20);
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (Boolean.getBoolean("waypoint.test.peerserver") && server.getTickCount() > nextPeerTick) {
                var main = server.getPlayerList().getPlayerByName("WaypointTester");
                var peer = server.getPlayerList().getPlayerByName("WaypointPeer");
                if (main != null && peer != null && server.getTickCount() > 120) try {
                    var sharedPoint = WaypointNetworking.accessible(main).stream().filter(p->p.displayName().equals("Nether fortress")).findFirst().orElseThrow();
                    if (peerPhase==0) {
                        WaypointNetworking.share(main,sharedPoint,"all"); peerPhase=1; nextPeerTick=server.getTickCount()+80;
                    } else if (peerPhase==1 && WaypointNetworking.accessible(peer).size()==1) {
                        WaypointNetworking.share(main,sharedPoint,"WaypointPeer"); peerPhase=2; nextPeerTick=server.getTickCount()+40;
                    } else if (peerPhase==2 && WaypointNetworking.accessible(peer).size()==2) {
                        var field=WaypointNetworking.class.getDeclaredField("shares");field.setAccessible(true);
                        var active=(Map<UUID,?>)field.get(null);UUID last=active.keySet().stream().reduce((a,b)->b).orElseThrow();
                        boolean rejected=false;try { WaypointNetworking.accept(main,last); } catch(IllegalArgumentException expected){rejected=true;}
                        check(rejected,"sender cannot accept a private share issued only to the peer");
                        check(WaypointNetworking.accessible(main).stream().noneMatch(p->p.displayName().equals("Nether fortress 2")),"peer imports do not change sender's list");
                        System.out.println("WAYPOINT_TWO_CLIENT_SHARING_CHECKS_PASSED"); peerPhase=3;
                    }
                } catch(Throwable failure){failure.printStackTrace();System.out.println("WAYPOINT_TWO_CLIENT_SHARING_CHECKS_FAILED");peerPhase=99;}
            }
            var iterator = scheduled.entrySet().iterator();
            while (iterator.hasNext()) {
                var entry = iterator.next();
                if (server.getTickCount() < entry.getValue()) continue;
                iterator.remove();
                try { run(entry.getKey()); System.out.println("WAYPOINT_SERVER_CHECKS_PASSED"); }
                catch(Throwable failure) { failure.printStackTrace(); System.out.println("WAYPOINT_SERVER_CHECKS_FAILED"); }
            }
        });
    }
    static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private void run(ServerPlayer player) throws Exception {
        var server = player.level().getServer();
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);
        var manager = player.level().getWaypointManager();
        var receivers = manager.getClass().getDeclaredField("players"); receivers.setAccessible(true);
        System.out.println("LOCATOR_TEST_STATE rule="+player.level().getGameRules().get(net.minecraft.world.level.gamerules.GameRules.LOCATOR_BAR)+" receivers="+((java.util.Set<?>)receivers.get(manager)).contains(player)+" range="+player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.WAYPOINT_RECEIVE_RANGE));
        if (Boolean.getBoolean("waypoint.test.restart")) {
            var loaded = WaypointNetworking.accessible(player);
            check(loaded.stream().anyMatch(p -> p.displayName().equals("Home base 2") && p.owner.equals(player.getUUID()) && p.icon.color.orElse(0)==0x55ff55), "personal saved copy retains owner/color/name");
            check(loaded.stream().anyMatch(p -> p.displayName().equals("End city") && p.dimension.equals("minecraft:the_end") && p.icon.style.identifier().toString().equals("minecraft:bowtie")), "End point/style persisted");
            check(loaded.stream().anyMatch(p -> p.displayName().equals("waypoint1")), "generated name persisted");
            System.out.println("WAYPOINT_SERVER_RESTART_CHECKS_PASSED"); return;
        }
        for (var stale : WaypointNetworking.accessible(player)) CommandWaypoints.remove(stale);
        CommandWaypoints.save();
        check(server.getCommands().getDispatcher().getRoot().getChild("waypoint").canUse(player.createCommandSourceStack()), "non-operator dedicated-server navigation");
        check(!server.getCommands().getDispatcher().getRoot().getChild("waypoint").getChild("modify").canUse(player.createCommandSourceStack()), "vanilla permissions retained");
        var stranger = new ServerPlayer(server, server.overworld(), new GameProfile(UUID.randomUUID(), "Stranger"), ClientInformation.createDefault());
        var data = new WaypointData("", "", "", -101, 203, "minecraft:the_nether", 0x12ab34, "minecraft:default", 100, true, true, true);
        var point = WaypointNetworking.save(player, data);
        check(point.displayName().equals("waypoint1") && point.owner.equals(player.getUUID()), "blank name unique and personal");
        var second = WaypointNetworking.save(player, data);
        check(second.displayName().equals("waypoint2"), "default name incremented");
        stranger.setPos(-808, 0, 1624);
        check(!point.canReceive(stranger), "personal locator visibility is private");
        check(WaypointNetworking.accessible(stranger).stream().noneMatch(p -> p == point), "personal list is private");
        var old = CommandWaypoint.CODEC.encodeStart(JsonOps.INSTANCE, point).getOrThrow();
        var restored = CommandWaypoint.CODEC.parse(JsonOps.INSTANCE, old).getOrThrow();
        check(restored.owner.equals(point.owner) && restored.name.equals(point.name), "personal codec round-trip");
        var legacy = old.deepCopy().getAsJsonObject(); legacy.remove("owner"); legacy.remove("name"); legacy.remove("visible");
        check(CommandWaypoint.CODEC.parse(JsonOps.INSTANCE, legacy).getOrThrow().visible, "legacy persistence defaults");
        WaypointNetworking.share(player, point, player.getGameProfile().name());
        var field = WaypointNetworking.class.getDeclaredField("shares"); field.setAccessible(true);
        var shares = (Map<?, ?>)field.get(null); UUID token = (UUID) shares.keySet().iterator().next();
        boolean rejected = false;
        try { WaypointNetworking.accept(stranger, token); } catch(IllegalArgumentException expected) { rejected = true; }
        check(rejected, "private share cannot be accepted by another player");
        var copy = WaypointNetworking.accept(player, token);
        check(copy.owner.equals(player.getUUID()) && !copy.uuid.equals(point.uuid) && copy.pos.equals(point.pos) && copy.icon.color.equals(point.icon.color), "share preserves properties in an independent copy");
        rejected = false; try { WaypointNetworking.accept(player, token); } catch(IllegalArgumentException expected) { rejected = true; }
        check(rejected, "Add is one-time per recipient");
        var endData = new WaypointData("", "", "waypoint1", 300, -400, "minecraft:the_end", null, "minecraft:bowtie", 50, false, true, true);
        var end = WaypointNetworking.save(player, endData);
        check(CommandWaypoints.points(server.overworld(), player.getUUID()).values().stream().noneMatch(p -> p == end), "End separation");
        var edited = new WaypointData(point.uuid.toString(), point.id.toString(), "Renamed destination", 9, -7, "minecraft:overworld", 0xff00ff, "minecraft:bowtie", 150, false, true, true);
        var moved = WaypointNetworking.save(player, edited);
        check(moved.uuid.equals(point.uuid) && moved.pos.equals(new BlockPos(9,0,-7)) && !moved.visible && moved.range == 150, "editor preserves identity while moving and changing properties");
        check(moved.listEntry(false).getString().equals("Renamed destination | 9 -7 (hidden)"), "dimension-filtered coordinate format");
        int netherCount = server.getCommands().getDispatcher().execute("waypoint list nether", player.createCommandSourceStack());
        check(netherCount == 2, "list filters exact saved Nether dimension");
        check(server.getCommands().getDispatcher().execute("waypoint list all", player.createCommandSourceStack()) == 4, "list all crosses End boundary");
        for (var p : List.of(second, copy, end, moved)) CommandWaypoints.remove(p);
        CommandWaypoints.save();
    }
}
