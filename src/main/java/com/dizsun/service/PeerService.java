package com.dizsun.service;

import org.java_websocket.WebSocket;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 管理peer节点的连接和移除及通信
 */
public class PeerService {
    private HashSet<String> peers;
    private ArrayList<WebSocket> sockets;
    private static PeerService peerService;
    private String localHost;
    private P2PService p2PService;

    private PeerService(P2PService _p2PService) {
        peers = new HashSet<>();
        sockets = new ArrayList<>();
        this.p2PService = _p2PService;
    }

    public static PeerService newPeerService(P2PService _p2PService) {
        if (peerService == null) {
            peerService = new PeerService(_p2PService);
        }
        return peerService;
    }

    public void addPeer(WebSocket webSocket) {
        String host = webSocket.getRemoteSocketAddress().getHostString();
        localHost = webSocket.getLocalSocketAddress().getHostString();
        if (!peers.contains(host) && !host.equals(localHost)) {
            peers.add(webSocket.getRemoteSocketAddress().getHostString());
            sockets.add(webSocket);
        }
    }

    public void removePeer(WebSocket webSocket) {
        peers.remove(webSocket.getRemoteSocketAddress().getHostString());
        sockets.remove(webSocket);
    }

    public void write(WebSocket webSocket, String msg) {
        webSocket.send(msg);
    }

    public void broadcast(String msg) {
        for (WebSocket ws : sockets) {
            this.write(ws, msg);
        }
    }

    public boolean contains(String host) {
        return peers.contains(host);
    }

    /**
     * 连接peer
     *
     * @param host 输入的host格式示例: 192.168.1.1 或者http://192.168.1.1:6001
     */
    public void connectToPeer(String host) {
        if (isIP(host)) {
            if (peers.contains(host) || host.equals(localHost))
                return;
            host = "http://" + host + ":6001";
        }
        try {
            final WebSocketClient socket = new WebSocketClient(new URI(host)) {
                @Override
                public void onOpen(ServerHandshake serverHandshake) {
                    write(this, p2PService.queryChainLengthMsg());
                    write(this, p2PService.queryAllPeers());
                    write(this, p2PService.queryAllVMsg());
                    addPeer(this);
                }

                @Override
                public void onMessage(String s) {
                    //handleMessage(this, s);
                    p2PService.handleMsgThread(this, s);
                }

                @Override
                public void onClose(int i, String s, boolean b) {
                    System.out.println("connection failed");
                    removePeer(this);
                }

                @Override
                public void onError(Exception e) {
                    System.out.println("connection failed");
                    removePeer(this);
                }
            };
            socket.connect();
        } catch (URISyntaxException e) {
            System.out.println("p2p connect is error:" + e.getMessage());
        }

    }

    public int length() {
        return peers.size();
    }

    public ArrayList<WebSocket> getSockets() {
        return sockets;
    }

    public Object[] getPeerArray() {
        return peers.toArray();
    }

    public boolean isIP(String addr) {
        if (addr.length() < 7 || addr.length() > 15 || "".equals(addr)) {
            return false;
        }
        /**
         * 判断IP格式和范围
         */
        String rexp = "([1-9]|[1-9]\\d|1\\d{2}|2[0-4]\\d|25[0-5])(\\.(\\d|[1-9]\\d|1\\d{2}|2[0-4]\\d|25[0-5])){3}";

        Pattern pat = Pattern.compile(rexp);

        Matcher mat = pat.matcher(addr);

        boolean ipAddress = mat.find();

        return ipAddress;
    }
}

