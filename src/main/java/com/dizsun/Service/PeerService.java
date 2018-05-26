package com.dizsun.Service;

import org.java_websocket.WebSocket;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashSet;

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
     * @param host 输入的host格式示例: 192.168.1.1
     */
    public void connectToPeer(String host) {
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
                    p2PService.handleMsgThred(this, s);
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

    public Object[] getPeerArray(){
        return peers.toArray();
    }
}
