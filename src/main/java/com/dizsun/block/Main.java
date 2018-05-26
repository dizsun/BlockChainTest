package com.dizsun.block;

import com.dizsun.util.*;
import com.dizsun.Service.*;
import org.java_websocket.WebSocket;

import java.util.Timer;
import java.util.TimerTask;

public class Main {
//    private static String Drivde = "org.sqlite.JDBC";

        public static void main(String[] args) {
        if (args != null && (args.length == 2 || args.length == 3)) {
            try {
                Broadcaster broadcaster = new Broadcaster();
                int httpPort = Integer.valueOf(args[0]);
                int p2pPort = Integer.valueOf(args[1]);
//                BlockService blockService = BlockService.newBlockService();
//                VBlockService vBlockService=VBlockService.newVBlockService();
                P2PService p2pService = new P2PService();
                broadcaster.subscribe(p2pService);
                p2pService.initP2PServer(p2pPort);
                if (args.length == 3 && args[2] != null) {
                    p2pService.connectToPeer(args[2]);
                }
                HTTPService httpService = new HTTPService(p2pService);
                broadcaster.broadcast();
                httpService.initHTTPServer(httpPort);
            } catch (Exception e) {
                System.out.println("startup is error:" + e.getMessage());
            }
        } else {
            System.out.println("usage: java -jar naivechain.jar 9000 6001");
        }
    }
//    public static void main(String[] args) {
//
//    }

}