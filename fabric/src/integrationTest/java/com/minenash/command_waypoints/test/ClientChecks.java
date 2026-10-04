package com.minenash.command_waypoints.test;

import com.minenash.command_waypoints.*;
import com.minenash.command_waypoints.client.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.multiplayer.*;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.*;
import java.util.*;
import java.io.File;

/** Drives real native screens and real payloads against a disposable offline server. */
public final class ClientChecks implements ClientModInitializer {
    public static final List<Component> messages = new ArrayList<>();
    private int ticks, phase, due;
    private boolean busy;
    private String pendingScreenshot, requestedScreenshot;
    private String savedUuid;
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    @Override public void onInitializeClient() {
        if (Boolean.getBoolean("waypoint.test.peer")) { new PeerChecks().init(); return; }
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ticks++;
            if (busy || ticks < due) return;
            try {
                if (requestedScreenshot != null) {
                    Screenshot.grab(client, false); pendingScreenshot = requestedScreenshot; requestedScreenshot = null;
                    due = ticks + 35; return;
                }
                if (pendingScreenshot != null) {
                    var folder = java.nio.file.Path.of("screenshots");
                    try (var files = java.nio.file.Files.list(folder)) {
                        var latest = files.filter(p -> p.getFileName().toString().matches("[0-9].*\\.png"))
                            .max(java.util.Comparator.comparing(p -> p.getFileName().toString())).orElseThrow();
                        java.nio.file.Files.move(latest, folder.resolve(pendingScreenshot + ".png"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }
                    pendingScreenshot = null;
                }
                tick(client);
            }
            catch(Throwable failure) { failure.printStackTrace(); System.out.println("WAYPOINT_CLIENT_CHECKS_FAILED phase=" + phase); busy = true; }
        });
    }
    private void delay() { due = ticks + 35; }
    private void capture(Minecraft client, String name) {
        client.gui.toastManager().clear(); requestedScreenshot = name; System.out.println("SCREENSHOT " + name);
        due = ticks + 8;
    }
    private Button button(Screen screen, String text) {
        return screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
            .filter(b -> b.visible && b.getMessage().getString().contains(text)).findFirst().orElseThrow(() -> new AssertionError("Missing button: " + text));
    }
    private EditBox box(Screen screen, String text) {
        return screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast)
            .filter(b -> b.getMessage().getString().equals(text)).findFirst().orElseThrow(() -> new AssertionError("Missing field: " + text));
    }
    private void click(Screen screen, String name) { var button = button(screen,name); check(button.active, "button enabled: " + name); button.onPress(new KeyEvent(257,0,0)); delay(); }
    private void tick(Minecraft client) {
        switch(phase) {
            case 0 -> {
                if (!(client.gui.screen() instanceof TitleScreen)) return;
                var factory = new WaypointModMenu().getModConfigScreenFactory();
                check(factory.create(client.gui.screen()) instanceof WaypointManagerScreen, "Mod Menu config factory opens manager");
                ConnectScreen.startConnecting(client.gui.screen(), client, new ServerAddress("127.0.0.1", 25577), new ServerData("Waypoint test", "127.0.0.1:25577", ServerData.Type.OTHER), false, null);
                phase++; delay();
            }
            case 1 -> {
                if (client.player == null || ticks < 160 || !WaypointClient.ready || client.getConnection().getOnlinePlayers().isEmpty()) return;
                if (Boolean.getBoolean("waypoint.test.render")) {
                    check(client.getConnection().getWaypointManager().hasWaypoints(), "native locator packets received");
                    phase=28; delay(); return;
                }
                if (Boolean.getBoolean("waypoint.test.restart")) {
                    check(WaypointClient.points.stream().anyMatch(p -> p.name().equals("Nether fortress") && p.personal() && p.x()==-101 && p.color()==0xffaa00), "saved personal waypoints received after restart");
                    System.out.println("NATIVE_LOCATOR_HAS_WAYPOINTS=" + client.getConnection().getWaypointManager().hasWaypoints());
                    System.out.println("WAYPOINT_CLIENT_RESTART_CHECKS_PASSED"); busy=true; return;
                }
                client.gui.setScreen(new WaypointManagerScreen(null)); phase++; delay();
            }
            case 2 -> { click(client.gui.screen(), "New"); phase++; }
            case 3 -> {
                var screen = client.gui.screen(); check(screen instanceof WaypointEditorScreen,"New opens editor");
                check(box(screen,"Name").getValue().isEmpty(), "default name blank");
                check(Integer.parseInt(box(screen,"X coordinate").getValue()) == (int)Math.floor(client.player.getX()), "default X uses player location");
                check(Integer.parseInt(box(screen,"Z coordinate").getValue()) == (int)Math.floor(client.player.getZ()), "default Z uses player location");
                box(screen,"Name").setValue("Home base"); box(screen,"Hex color").setValue("55FF55");
                box(screen,"X coordinate").setValue("-120"); box(screen,"Z coordinate").setValue("84");
                capture(client,"editor"); phase++;
            }
            case 4 -> { click(client.gui.screen(),"Save"); phase++; }
            case 5 -> {
                if (!(client.gui.screen() instanceof WaypointManagerScreen)) return;
                check(WaypointClient.points.stream().anyMatch(p -> p.name().equals("Home base") && p.x()==-120 && p.z()==84), "GUI save round-trip");
                savedUuid = WaypointClient.points.stream().filter(p->p.name().equals("Home base")).findFirst().orElseThrow().uuid();
                var object = WaypointClient.action("save"); object.add("point", WaypointData.GSON.toJsonTree(new WaypointData("","","Nether fortress", -101,203,"minecraft:the_nether",0xffaa00,"minecraft:default",60000000,true,true,true)));
                busy=true; WaypointClient.request(object, error -> { check(error.isEmpty(),error); busy=false; phase++; delay(); });
            }
            case 6 -> {
                var object = WaypointClient.action("save"); object.add("point", WaypointData.GSON.toJsonTree(new WaypointData("","","End city",1200,-800,"minecraft:the_end",0xff55ff,"minecraft:bowtie",60000000,true,true,true)));
                busy=true; WaypointClient.request(object, error -> { check(error.isEmpty(),error); busy=false; phase++; delay(); });
            }
            case 7 -> {
                var manager=client.gui.screen();
                click(manager,"All dimensions");
                var rows=manager.children().stream().filter(Button.class::isInstance).map(Button.class::cast).filter(b->b.visible&&b.getMessage().getString().contains(" | ")).toList();
                check(rows.size()==1 && rows.getFirst().getMessage().getString().contains("Home base | -120 84") && !rows.getFirst().getMessage().getString().contains("Overworld"),"GUI dimension filter excludes other dimensions and omits suffix");
                click(manager,"Overworld"); click(manager,"End"); click(manager,"Nether");
                box(manager,"Search waypoints").setValue("fort");
                check(manager.children().stream().filter(Button.class::isInstance).map(Button.class::cast).filter(b->b.visible&&b.getMessage().getString().contains(" | ")).count()==1,"live waypoint search");
                box(manager,"Search waypoints").setValue("");
                click(manager,"Home base"); capture(client,"manager"); phase++;
            }
            case 8 -> { click(client.gui.screen(),"Share"); phase++; }
            case 9 -> { capture(client,"share-public"); phase++; }
            case 10 -> { click(client.gui.screen(),"Specific player"); box(client.gui.screen(),"Search online players").setValue(client.getUser().getName().substring(0,2)); phase++; }
            case 11 -> { click(client.gui.screen(),client.getUser().getName()); capture(client,"share-private"); phase++; }
            case 12 -> { click(client.gui.screen(),"Send privately"); phase++; }
            case 13 -> {
                if (!(client.gui.screen() instanceof WaypointManagerScreen)) return;
                var share = messages.stream().filter(c->c.getString().contains("shared Home base")).reduce((a,b)->b).orElseThrow();
                check(share.getString().contains(client.getUser().getName()) && share.getString().contains("[Add]"), "formatted private share sender/name/Add");
                var add = share.getSiblings().stream().filter(c -> c.getString().equals("[Add]")).findFirst().orElseThrow();
                check(add.getStyle().isBold() && add.getStyle().getClickEvent() instanceof ClickEvent.RunCommand, "highlighted clickable Add");
                client.getConnection().sendCommand(((ClickEvent.RunCommand)add.getStyle().getClickEvent()).command().substring(1)); phase++; delay();
            }
            case 14 -> {
                check(WaypointClient.points.stream().anyMatch(p -> p.name().equals("Home base 2") && p.personal()), "chat Add imports a personal copy");
                click(client.gui.screen(),"Settings"); phase++;
            }
            case 15 -> { capture(client,"settings"); phase++; }
            case 16 -> {
                var settings = new WaypointSettings(); check(settings.size(300)==6 && settings.size(200)==6 && settings.size(100)==10 && settings.size(0)==14,"linear distance scaling bounds and midpoint");
                check(client.gui.screen() instanceof WaypointSettingsScreen,"settings screen");
                click(client.gui.screen(),"Key bindings"); phase++;
            }
            case 17 -> { check(client.gui.screen() instanceof net.minecraft.client.gui.screens.options.controls.KeyBindsScreen,"native configurable keybindings"); capture(client,"keybinds"); client.gui.screen().onClose(); phase++; delay(); }
            case 18 -> {
                box(client.gui.screen(),"Growth distance").setValue("400");
                box(client.gui.screen(),"Minimum icon size").setValue("4");
                box(client.gui.screen(),"Maximum icon size").setValue("12");
                click(client.gui.screen(),"Save");
                check(WaypointSettings.INSTANCE.size(200)==8,"display settings save applies immediately");
                click(client.gui.screen(),"Home base |"); click(client.gui.screen(),"Edit"); phase++;
            }
            case 19 -> { box(client.gui.screen(),"X coordinate").setValue("invalid"); click(client.gui.screen(),"Save"); phase++; }
            case 20 -> { check(client.gui.screen() instanceof WaypointEditorScreen,"invalid input does not close editor"); box(client.gui.screen(),"X coordinate").setValue("-119");
                box(client.gui.screen(),"Display range").setValue("1234"); box(client.gui.screen(),"Icon style").setValue("minecraft:bowtie");
                box(client.gui.screen(),"Hex color").setValue("FF00FF"); click(client.gui.screen(),"Overworld"); click(client.gui.screen(),"End");
                click(client.gui.screen(),"Save"); phase++; }
            case 21 -> { if (!(client.gui.screen() instanceof WaypointManagerScreen)) return; check(WaypointClient.points.stream().anyMatch(p -> p.uuid().equals(savedUuid) && p.x()==-119 && p.range()==1234 && p.style().equals("minecraft:bowtie") && p.dimension().equals("minecraft:the_nether") && p.color()==0xff00ff),"edit updates same waypoint"); click(client.gui.screen(),"Home base |"); click(client.gui.screen(),"Hide"); phase++; }
            case 22 -> { check(WaypointClient.points.stream().anyMatch(p->p.uuid().equals(savedUuid)&&!p.visible()),"GUI visibility toggle"); click(client.gui.screen(),"Delete"); phase++; }
            case 23 -> { check(client.gui.screen() instanceof ConfirmScreen,"delete confirmation"); click(client.gui.screen(),"Delete"); phase++; }
            case 24 -> { check(WaypointClient.points.stream().noneMatch(p->p.uuid().equals(savedUuid)),"GUI deletion"); client.gui.setScreen(null); WaypointClient.addKey.setDown(false); KeyMapping.click(net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper.getBoundKeyOf(WaypointClient.addKey)); phase++; delay(); }
            case 25 -> { check(client.gui.screen() instanceof WaypointEditorScreen,"quick-add key opens editor"); check(box(client.gui.screen(),"Name").getValue().isEmpty(),"quick-add name blank"); click(client.gui.screen(),"Save"); phase++; }
            case 26 -> {
                if (!(client.gui.screen() instanceof WaypointManagerScreen)) return;
                check(WaypointClient.points.stream().anyMatch(p->p.name().equals("waypoint1")),"blank GUI name automatically assigned");
                var point=WaypointClient.points.stream().filter(p->p.name().equals("Nether fortress")).findFirst().orElseThrow();
                var object=WaypointClient.action("share");object.addProperty("uuid",point.uuid());object.addProperty("recipient","all");
                busy=true;WaypointClient.request(object,error->{check(error.isEmpty(),error);busy=false;phase++;delay();});
            }
            case 27 -> {
                check(messages.stream().anyMatch(c->c.getString().contains("shared Nether fortress")),"public chat share");
                client.gui.setScreen(null); client.gui.openChatScreen(ChatComponent.ChatMethod.MESSAGE); capture(client,"sharing-chat"); phase++;
            }
            case 28 -> {
                WaypointSettings.INSTANCE = new WaypointSettings(); client.gui.setScreen(null); client.player.setYRot(0); client.player.setXRot(0);
                var object=WaypointClient.action("save"); object.add("point",WaypointData.GSON.toJsonTree(new WaypointData(WaypointClient.points.stream().filter(p->p.name().equals("Distance sample")).map(WaypointData::uuid).findFirst().orElse(""),"","Distance sample",(int)Math.floor(client.player.getX()),(int)Math.floor(client.player.getZ())+201,"minecraft:overworld",0x55ffff,"minecraft:default",60000000,true,true,true)));
                busy=true;WaypointClient.request(object,error->{check(error.isEmpty(),error);busy=false;phase++;delay();});
            }
            case 29 -> { check(client.getConnection().getWaypointManager().hasWaypoints(),"native locator waypoint exists during render checks"); capture(client,"locator-far"); phase++; }
            case 30 -> {
                var sample=WaypointClient.points.stream().filter(p->p.name().equals("Distance sample")).findFirst().orElseThrow();
                var data=new WaypointData(sample.uuid(),sample.id(),sample.name(),sample.x(),(int)Math.floor(client.player.getZ())+100,sample.dimension(),sample.color(),sample.style(),sample.range(),true,true,true);
                var object=WaypointClient.action("save"); object.add("point",WaypointData.GSON.toJsonTree(data));
                busy=true;WaypointClient.request(object,error->{check(error.isEmpty(),error);busy=false;phase++;delay();});
            }
            case 31 -> { capture(client,"locator-midpoint"); phase++; }
            case 32 -> {
                var sample=WaypointClient.points.stream().filter(p->p.name().equals("Distance sample")).findFirst().orElseThrow();
                var data=new WaypointData(sample.uuid(),sample.id(),sample.name(),sample.x(),(int)Math.floor(client.player.getZ())+1,sample.dimension(),sample.color(),sample.style(),sample.range(),true,true,true);
                var object=WaypointClient.action("save"); object.add("point",WaypointData.GSON.toJsonTree(data));
                busy=true;WaypointClient.request(object,error->{check(error.isEmpty(),error);busy=false;phase++;delay();});
            }
            case 33 -> { capture(client,"locator-near"); phase++; }
            case 34 -> { client.player.setXRot(85); phase++; delay(); }
            case 35 -> { capture(client,"locator-looking-down"); phase++; }
            case 36 -> { client.player.setXRot(-85); phase++; delay(); }
            case 37 -> { capture(client,"locator-looking-up"); phase++; }
            case 38 -> { System.out.println("WAYPOINT_CLIENT_CHECKS_PASSED"); busy=true; }
        }
    }
}
