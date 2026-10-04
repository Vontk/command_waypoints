package com.minenash.command_waypoints.fabric;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record WaypointPayload(String json) implements CustomPacketPayload {
    public static final Type<WaypointPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("command_waypoints", "manage"));
    public static final StreamCodec<FriendlyByteBuf, WaypointPayload> CODEC = StreamCodec.of(
        (buf, payload) -> buf.writeUtf(payload.json, 262144), buf -> new WaypointPayload(buf.readUtf(262144)));
    @Override public Type<WaypointPayload> type() { return TYPE; }
}
