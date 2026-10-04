package com.minenash.command_waypoints.mixin;

import com.minenash.command_waypoints.*;
import com.minenash.command_waypoints.client.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.contextualbar.LocatorBar;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.waypoints.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocatorBar.class)
public class LocatorBarMixin {
    @Inject(method = "lambda$extractRenderState$1", at = @At("HEAD"), cancellable = true)
    private void command_waypoints$render(Entity camera, Level level, PartialTickSupplier ticks,
                                          GuiGraphicsExtractor graphics, int top, TrackedWaypoint tracked, CallbackInfo ci) {
        var id = tracked.id().left().orElse(null);
        if (id == null) return;
        var point = WaypointClient.points.stream().filter(p -> p.uuid().equals(id.toString())).findFirst().orElse(null);
        if (point == null) return;
        ci.cancel(); // Custom X/Z-only destinations never display camera-pitch arrows.
        var client = Minecraft.getInstance();
        double yaw = tracked.yawAngleToCamera(level, client.gameRenderer.mainCamera(), ticks);
        if (yaw <= -60 || yaw > 60 || !WaypointCoordinates.shares(point.dimension(), level.dimension().identifier().toString())) return;
        double conversion = WaypointCoordinates.scale(point.dimension(), level.dimension().identifier().toString());
        double distance = Math.sqrt(WaypointCoordinates.distanceSquared(point.x() * conversion, point.z() * conversion, camera.getX(), camera.getZ()));
        float size = (float) WaypointSettings.INSTANCE.size(distance);
        float x = (float)Math.ceil((graphics.guiWidth() - 9) / 2f) + (float)Math.floor(yaw * 173 / 2 / 60) + 4.5f;
        float y = top + 2.5f;
        int color = point.color() == null ? net.minecraft.util.ARGB.setBrightness(net.minecraft.util.ARGB.color(255, id.hashCode()), 0.9f) : 0xff000000 | point.color();
        var sprite = client.gui.hud.getWaypointStyles().get(ResourceKey.create(WaypointStyleAssets.ROOT_ID, Identifier.parse(point.style()))).sprite(0);
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y).scale(size / 9f, size / 9f).translate(-4.5f, -4.5f);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, 0, 0, 9, 9, color);
        graphics.pose().popMatrix();
    }
}
