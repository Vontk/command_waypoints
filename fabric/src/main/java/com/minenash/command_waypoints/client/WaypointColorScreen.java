package com.minenash.command_waypoints.client;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import java.util.function.Consumer;

/** Native RGB sliders, palette, hexadecimal input, and a live colored name preview. */
public final class WaypointColorScreen extends WaypointScreen {
    private final Consumer<String> save;
    private final String waypointName;
    private final int automatic;
    private String hex;
    private int rgb;
    private EditBox hexBox;
    private boolean syncing;
    private static final int[] PALETTE = {0xff5555,0xffaa00,0xffff55,0x55ff55,0x55ffff,0x5555ff,0xff55ff,0xffffff};
    private final ChannelSlider[] sliders = new ChannelSlider[3];
    public WaypointColorScreen(Screen parent, String color, String name, int automatic, Consumer<String> save) {
        super(parent, "Waypoint color"); this.hex=color; this.waypointName=name.isBlank()?"Waypoint":name; this.automatic=automatic; this.save=save;
        rgb=parse(color,automatic);
    }
    private static int parse(String hex,int fallback) { try { return Integer.parseInt(hex.replaceFirst("^#",""),16)&0xffffff; } catch (NumberFormatException ignored) { return fallback; } }
    @Override protected void init() {
        super.init();
        int y=76;
        for(int n=0;n<3;n++) { sliders[n]=addRenderableWidget(new ChannelSlider(left,y+n*25,panelWidth,n)); }
        hexBox=field("Waypoint color hex RGB",hex,left,157,panelWidth-90);hexBox.setMaxLength(7);hexBox.setHint(Component.literal("Automatic"));
        hexBox.setResponder(value->{if(syncing)return;hex=value;String digits=value.replaceFirst("^#","");
            if(digits.isEmpty()||digits.matches("[0-9a-fA-F]{6}")){rgb=parse(value,automatic);for(var slider:sliders)slider.refresh();error="";}
        });
        tooltip(hexBox,"Colors the waypoint marker and its name in lists and shared messages. Six RGB hexadecimal digits; optional #.");
        button("Automatic",left+panelWidth-84,157,84,b->choose(""));
        int w=(panelWidth-28)/8;
        for(int n=0;n<8;n++){int c=PALETTE[n];var swatch=button("■",left+n*(w+4),184,w,b->choose(String.format("%06X",c)));swatch.setMessage(Component.literal("■").withStyle(s->s.withColor(c)));tooltip(swatch,"#"+String.format("%06X",c));}
        button("Cancel",left,height-28,panelWidth/2-3,b->onClose());
        button("Use color",left+panelWidth/2+3,height-28,panelWidth/2-3,b->{String digits=hex.replaceFirst("^#","");
            if(!digits.isEmpty()&&!digits.matches("[0-9a-fA-F]{6}")){error="Use six hexadecimal digits or Automatic.";return;}
            save.accept(digits);onClose();});
    }
    private void choose(String value){hex=value;rgb=parse(value,automatic);syncing=true;hexBox.setValue(value);syncing=false;for(var slider:sliders)slider.refresh();error="";}
    private final class ChannelSlider extends AbstractSliderButton {
        private final int channel;
        ChannelSlider(int x,int y,int w,int channel){super(x,y,w,20,Component.empty(),((rgb>>((2-channel)*8))&255)/255d);this.channel=channel;updateMessage();}
        void refresh(){value=((rgb>>((2-channel)*8))&255)/255d;updateMessage();}
        @Override protected void updateMessage(){setMessage(Component.literal(new String[]{"Red","Green","Blue"}[channel]+": "+(int)Math.round(value*255)));}
        @Override protected void applyValue(){int shift=(2-channel)*8;rgb=(rgb&~(255<<shift))|((int)Math.round(value*255)<<shift);hex=String.format("%06X",rgb);syncing=true;hexBox.setValue(hex);syncing=false;error="";}
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){super.extractRenderState(g,mx,my,delta);
        g.centeredText(font,"Marker and waypoint name",width/2,40,0xffc6cbd1);
        WaypointStyleScreen.preview(g,"minecraft:default",width/2-font.width(waypointName)/2-20,55,12,rgb);
        g.centeredText(font,Component.literal(waypointName).withStyle(s->s.withColor(rgb)),width/2,57,0xffffffff);
        label(g,"Hex RGB (optional)",left,146);
    }
}
