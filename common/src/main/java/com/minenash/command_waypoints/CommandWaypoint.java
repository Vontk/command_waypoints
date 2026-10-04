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
        Codec.INT.fieldOf("range").forGetter(CommandWaypoint::range),
        Codec.BOOL.optionalFieldOf("visible", true).forGetter(CommandWaypoint::visible)
    ).apply(instance, CommandWaypoint::new));

    public final UUID uuid;
    public final Identifier id;
    public BlockPos pos;
    public Waypoint.Icon icon;
    public int range;
    public boolean visible;
    // The owning level supplies this on load, preserving old per-level saved data.
    public String dimension;

    public UUID uuid() { return uuid; }
    public Identifier id() { return id; }
    public BlockPos pos() { return pos; }
    public Waypoint.Icon icon() { return icon; }
    public int range() { return range; }
    public boolean visible() { return visible; }



    public CommandWaypoint(UUID uuid, Identifier id, BlockPos pos, Waypoint.Icon icon, int range, boolean visible) {
        this.uuid = uuid;
        this.id = id;
        this.pos = pos;
        this.icon = icon;
        this.range = range;
        this.visible = visible;
    }

    @Override
    public String toString() {
        return id + " [" + pos.getX() + " " + pos.getZ() + "] " + dimension + (visible ? " (visible)" : " (hidden)");
    }

    @Override
    public boolean isTransmittingWaypoint() {
        return visible;
    }

    @Override
    public Optional<Connection> makeWaypointConnectionWith(ServerPlayer receiver) {
        return canReceive(receiver) ? Optional.of(new HorizontalConnection(receiver)) : Optional.empty();
    }

    @Override
    public Icon waypointIcon() {
        return icon;
    }

    private double scale(ServerPlayer receiver) {
        return WaypointCoordinates.scale(dimension, receiver.level().dimension().identifier().toString());
    }

    public boolean canReceive(ServerPlayer receiver) {
        String destination = receiver.level().dimension().identifier().toString();
        if (!visible || dimension == null || !WaypointCoordinates.shares(dimension, destination)) return false;
        double scale = scale(receiver);
        double distance = WaypointCoordinates.distanceSquared(pos.getX() * scale, pos.getZ() * scale,
            receiver.getX(), receiver.getZ());
        double limit = Math.min(range, receiver.getAttributeValue(Attributes.WAYPOINT_RECEIVE_RANGE));
        return limit > 0 && distance <= limit * limit;
    }

    public class HorizontalConnection implements WaypointTransmitter.Connection {

        private final ServerPlayer receiver;

        public HorizontalConnection(ServerPlayer receiver) {
            this.receiver = receiver;
        }

        @Override
        public void connect() {
            this.receiver.connection.send(ClientboundTrackedWaypointPacket.addWaypointAzimuth(uuid, icon, angle()));
        }

        @Override
        public void disconnect() {
            this.receiver.connection.send(ClientboundTrackedWaypointPacket.removeWaypoint(uuid));
        }

        @Override
        public void update() {
            this.receiver.connection.send(ClientboundTrackedWaypointPacket.updateWaypointAzimuth(uuid, icon, angle()));
        }

        @Override
        public boolean isBroken() {
            return !canReceive(receiver);
        }

        private float angle() {
            double scale = scale(receiver);
            return WaypointCoordinates.azimuth(pos.getX() * scale, pos.getZ() * scale,
                receiver.getX(), receiver.getZ());
        }
    }
}
