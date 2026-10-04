package com.minenash.command_waypoints;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.protocol.game.ClientboundTrackedWaypointPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.waypoints.Waypoint;
import net.minecraft.world.waypoints.WaypointTransmitter;

import java.util.Optional;
import java.util.UUID;

public class CommandWaypoint implements WaypointTransmitter {

    public static final Codec<CommandWaypoint> CODEC = RecordCodecBuilder.create((instance) -> instance.group(
        UUIDUtil.CODEC.fieldOf("uuid").forGetter(CommandWaypoint::uuid),
        Identifier.CODEC.fieldOf("id").forGetter(CommandWaypoint::id),
        BlockPos.CODEC.fieldOf("pos").forGetter(CommandWaypoint::pos),
        Icon.CODEC.fieldOf("icon").forGetter(CommandWaypoint::icon),
        Codec.INT.fieldOf("range").forGetter(CommandWaypoint::range)
    ).apply(instance, CommandWaypoint::new));

    public final UUID uuid;
    public final Identifier id;
    public BlockPos pos;
    public Waypoint.Icon icon;
    public int range;

    public UUID uuid() { return uuid; }
    public Identifier id() { return id; }
    public BlockPos pos() { return pos; }
    public Waypoint.Icon icon() { return icon; }
    public int range() { return range; }



    public CommandWaypoint(UUID uuid, Identifier id, BlockPos pos, Waypoint.Icon icon, int range) {
        this.uuid = uuid;
        this.id = id;
        this.pos = pos;
        this.icon = icon;
        this.range = range;
    }

    @Override
    public String toString() {
        return id + "[" +  pos.getX() + " " + pos.getY() + " " + pos.getZ() + "]";
    }

    @Override
    public boolean isTransmittingWaypoint() {
        return true;
    }

    @Override
    public Optional<Connection> makeWaypointConnectionWith(ServerPlayer receiver) {
        return Optional.of(new BlockPosConnection(receiver));
    }

    @Override
    public Icon waypointIcon() {
        return icon;
    }

    public class BlockPosConnection implements WaypointTransmitter.Connection {

        private final ServerPlayer receiver;

        public BlockPosConnection(ServerPlayer receiver) {
            this.receiver = receiver;
        }

        @Override
        public void connect() {
            this.receiver.connection.send(ClientboundTrackedWaypointPacket.addWaypointPosition(uuid, icon, pos));
        }

        @Override
        public void disconnect() {
            this.receiver.connection.send(ClientboundTrackedWaypointPacket.removeWaypoint(uuid));
        }

        @Override
        public void update() {
            this.receiver.connection.send(ClientboundTrackedWaypointPacket.updateWaypointPosition(uuid, icon, pos));
        }

        @Override
        public boolean isBroken() {
            double d = Math.min(range, receiver.getAttributeValue(Attributes.WAYPOINT_RECEIVE_RANGE));
            return receiver.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos)) >= d;
        }
    }
}
