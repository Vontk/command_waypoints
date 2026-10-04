package com.minenash.command_waypoints.test;

import com.minenash.command_waypoints.client.WaypointClient;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.multiplayer.*;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.*;

/** Second real client verifies personal privacy and public/private share delivery. */
public final class PeerChecks {
    private int phase;
    private String publicId;
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    public void init() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            try {
                if (phase == 0 && client.gui.screen() instanceof TitleScreen) {
                    ConnectScreen.startConnecting(client.gui.screen(), client, new ServerAddress("127.0.0.1",25577),new ServerData("Peer test","127.0.0.1:25577",ServerData.Type.OTHER),false,null);phase++;
                } else if (phase == 1 && client.player != null && WaypointClient.ready) {
                    check(WaypointClient.points.isEmpty(),"peer starts without the other player's personal destinations");
                    System.out.println("WAYPOINT_PEER_READY");phase++;
                } else if (phase == 2) {
                    var message=ClientChecks.messages.stream().filter(c->c.getString().contains("shared Nether fortress")).findFirst().orElse(null);
                    if (message==null)return;
                    var add=message.getSiblings().stream().filter(c->c.getString().equals("[Add]")).findFirst().orElseThrow();
                    publicId=((ClickEvent.RunCommand)add.getStyle().getClickEvent()).command();
                    client.getConnection().sendCommand(publicId.substring(1));phase++;
                } else if (phase == 3 && WaypointClient.points.stream().anyMatch(p->p.name().equals("Nether fortress"))) {
                    check(WaypointClient.points.size()==1 && WaypointClient.points.getFirst().personal() && WaypointClient.points.getFirst().x()==-101 && WaypointClient.points.getFirst().z()==203,"public Add creates only recipient's independent copy");
                    System.out.println("WAYPOINT_PEER_PUBLIC_CHECKS_PASSED");phase++;
                } else if (phase == 4) {
                    long count=ClientChecks.messages.stream().filter(c->c.getString().contains("shared Nether fortress")).count();
                    if (count<2)return;
                    var message=ClientChecks.messages.stream().filter(c->c.getString().contains("shared Nether fortress")).reduce((a,b)->b).orElseThrow();
                    var add=message.getSiblings().stream().filter(c->c.getString().equals("[Add]")).findFirst().orElseThrow();
                    String command=((ClickEvent.RunCommand)add.getStyle().getClickEvent()).command();
                    check(!command.equals(publicId),"private share has an independent token");
                    client.getConnection().sendCommand(command.substring(1));phase++;
                } else if (phase == 5 && WaypointClient.points.size()==2) {
                    check(WaypointClient.points.stream().allMatch(p->p.personal()&&p.x()==-101&&p.z()==203),"private Add copies properties and collision naming");
                    System.out.println("WAYPOINT_PEER_PRIVATE_CHECKS_PASSED");phase++;
                }
            }catch(Throwable failure){failure.printStackTrace();System.out.println("WAYPOINT_PEER_CHECKS_FAILED");phase=99;}
        });
    }
}
