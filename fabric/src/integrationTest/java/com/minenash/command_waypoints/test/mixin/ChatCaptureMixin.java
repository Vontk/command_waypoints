package com.minenash.command_waypoints.test.mixin;
import com.minenash.command_waypoints.test.ClientChecks;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ChatComponent.class)
public class ChatCaptureMixin {
    @Inject(method="addServerSystemMessage", at=@At("HEAD"))
    private void capture(Component component, CallbackInfo ci) { ClientChecks.messages.add(component); }
}
