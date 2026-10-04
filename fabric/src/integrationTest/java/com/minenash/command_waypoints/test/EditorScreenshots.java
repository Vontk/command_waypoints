package com.minenash.command_waypoints.test;

import com.minenash.command_waypoints.client.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.*;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.multiplayer.*;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.input.KeyEvent;
import java.nio.file.*;

/** Screenshot-only driver: does not save or edit world waypoints. */
public final class EditorScreenshots {
    private int phase,ticks,due;
    private String pending,requested;
    private WaypointEditorScreen editor;
    public void init(){ClientTickEvents.END_CLIENT_TICK.register(client->{ticks++;if(ticks<due)return;
        try {
            if(requested!=null){client.gui.toastManager().clear();Screenshot.grab(client,false);pending=requested;requested=null;due=ticks+35;return;}
            if(pending!=null){try(var files=Files.list(Path.of("screenshots"))){var latest=files.filter(p->p.getFileName().toString().matches("[0-9].*\\.png")).max(java.util.Comparator.comparing(p->p.getFileName().toString())).orElseThrow();Files.move(latest,Path.of("screenshots",pending+".png"),StandardCopyOption.REPLACE_EXISTING);}pending=null;}
            switch(phase){
                case 0->{if(!(client.gui.screen() instanceof TitleScreen))return;ConnectScreen.startConnecting(client.gui.screen(),client,new ServerAddress("127.0.0.1",25577),new ServerData("Waypoint screenshots","127.0.0.1:25577",ServerData.Type.OTHER),false,null);}
                case 1->{if(client.player==null||!WaypointClient.ready||ticks<400)return;editor=new WaypointEditorScreen(new WaypointManagerScreen(null),null);client.gui.setScreen(editor);}
                case 2->{for(var child:editor.children())if(child instanceof EditBox box){if(box.getMessage().getString().equals("Name"))box.setValue("Home base");if(box.getMessage().getString().equals("Hex color"))box.setValue("55FF55");}capture(client,"editor");}
                case 3->{click(client.gui.screen(),"Pick color");}
                case 4->{capture(client,"color-picker");}
                case 5->{click(client.gui.screen(),"Use color");}
                case 6->{click(client.gui.screen(),"Default (round)");}
                case 7->{capture(client,"icon-styles");}
                case 8->{click(client.gui.screen(),"Bowtie");}
                case 9->{capture(client,"icon-styles-bowtie");}
                case 10->{click(client.gui.screen(),"Use shape");}
                case 11->{capture(client,"editor-bowtie");}
                case 12->{System.out.println("WAYPOINT_EDITOR_SCREENSHOTS_DONE");due=Integer.MAX_VALUE;return;}
            }
            phase++;due=ticks+40;
        } catch(Exception failure){failure.printStackTrace();System.out.println("WAYPOINT_EDITOR_SCREENSHOTS_FAILED");due=Integer.MAX_VALUE;}
    });}
    private void capture(Minecraft client,String name){client.gui.toastManager().clear();requested=name;System.out.println("CAPTURE "+name);}
    private void click(Screen screen,String text){screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast).filter(b->b.getMessage().getString().contains(text)).findFirst().orElseThrow().onPress(new KeyEvent(257,0,0));}
}
