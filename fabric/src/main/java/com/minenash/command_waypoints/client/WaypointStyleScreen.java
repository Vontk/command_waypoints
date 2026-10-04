package com.minenash.command_waypoints.client;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.resources.*;
import net.minecraft.world.waypoints.WaypointStyleAssets;
import java.util.function.Consumer;

/** Shows the actual resource-pack sprite used by our locator markers. */
public final class WaypointStyleScreen extends WaypointScreen {
    private final Consumer<String> save;
    private String style;
    private final int color;
    private EditBox custom;
    public WaypointStyleScreen(Screen parent,String style,int color,Consumer<String> save){super(parent,"Waypoint marker shape");this.style=style;this.color=color;this.save=save;}
    public static String label(String style){return switch(style){case "minecraft:default"->"Default (round)";case "minecraft:bowtie"->"Bowtie";default->"Custom style";};}
    @Override protected void init(){super.init();
        button("Default (round)",left+42,67,panelWidth-42,b->choose("minecraft:default"));
        button("Bowtie",left+42,101,panelWidth-42,b->choose("minecraft:bowtie"));
        custom=field("Resource-pack icon style",style,left,151,panelWidth);custom.setResponder(value->style=value);
        tooltip(custom,"Optional: a waypoint-style identifier supplied by a resource pack. Built-in choices above need no resource pack.");
        button("Cancel",left,height-28,panelWidth/2-3,b->onClose());
        button("Use shape",left+panelWidth/2+3,height-28,panelWidth/2-3,b->{var id=Identifier.tryParse(style.strip());if(id==null){error="Enter a valid resource-pack style identifier.";return;}save.accept(id.toString());onClose();});
    }
    private void choose(String value){style=value;custom.setValue(value);error="";}
    public static void preview(GuiGraphicsExtractor g,String style,int x,int y,int size,int color){var client=net.minecraft.client.Minecraft.getInstance();var id=Identifier.tryParse(style);if(id==null)return;
        var sprite=client.gui.hud.getWaypointStyles().get(ResourceKey.create(WaypointStyleAssets.ROOT_ID,id)).sprite(0);
        g.blitSprite(RenderPipelines.GUI_TEXTURED,sprite,x,y,size,size,0xff000000|color);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){super.extractRenderState(g,mx,my,delta);
        g.centeredText(font,"Choose the shape shown on the Locator Bar",width/2,42,0xffc6cbd1);
        preview(g,"minecraft:default",left+9,69,16,color);preview(g,"minecraft:bowtie",left+9,103,16,color);
        label(g,"Resource-pack style (advanced)",left,140);
        preview(g,style,left+9,185,18,color);label(g,"Selected: "+label(style),left+38,190);
        if(height>=280)label(g,"Previews follow your active resource pack.",left,214);
    }
}
