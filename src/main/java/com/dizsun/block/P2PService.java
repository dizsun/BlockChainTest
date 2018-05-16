package com.dizsun.block;


import com.alibaba.fastjson.JSON;
import com.dizsun.util.ISubscriber;
import org.java_websocket.WebSocket;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.handshake.ServerHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class P2PService implements ISubscriber{
    private List<WebSocket> sockets;    //节点的套接字集合
    private HashSet<String> peers;  //节点的URI集合
    private BlockService blockService;
    private ExecutorService pool;   //线程池

    private final static int QUERY_LATEST_BLOCK = 0;
    private final static int QUERY_ALL_BLOCKS = 1;
    private final static int RESPONSE_BLOCKCHAIN = 2;
    private final static int QUERY_ALL_PEERS = 3;
    private final static int RESPONSE_ALL_PEERS = 4;
    private final static int REQUEST_NEGOTIATION = 5;
    private final static int RESPONSE_ACK = 6;

    private enum ViewState {
        Nagotiation,    //协商状态,此时各个节点协商是否开始竞选
        WatingNagotiation,  //等待协商状态,此时等待其他节点发送协商请求
        WaitingACK,     //等待其他节点协商同意
        Running,    //系统正常运行状态
        WritingBlock,
        WritingVBlock}
    public static final int LT=1000*60*60;
    //view number
    private int VN;
    //节点数3N+0,1,2
    private int N=1;
    private int NCounter=1;
    private ViewState viewState=ViewState.Running;
    private List<Block> vBlocks;
    private List<ACK> acks;


    public P2PService(BlockService blockService) {
        this.blockService = blockService;
        this.sockets = new ArrayList<>();
        this.peers = new HashSet<>();
        this.pool = Executors.newCachedThreadPool();
        this.acks=new ArrayList<>();
        this.VN=0;
    }

    public void initP2PServer(int port) {
        final WebSocketServer socket = new WebSocketServer(new InetSocketAddress(port)) {
            public void onOpen(WebSocket webSocket, ClientHandshake clientHandshake) {
                write(webSocket, queryChainLengthMsg());
                sockets.add(webSocket);
            }

            public void onClose(WebSocket webSocket, int i, String s, boolean b) {
                System.out.println("connection failed to peer:" + webSocket.getRemoteSocketAddress());
                sockets.remove(webSocket);
            }

            public void onMessage(WebSocket webSocket, String s) {
                Thread thread = new HandleMsgThread(webSocket,s);
                pool.execute(thread);
            }

            public void onError(WebSocket webSocket, Exception e) {
                System.out.println("connection failed to peer:" + webSocket.getRemoteSocketAddress());
                sockets.remove(webSocket);
            }

            public void onStart() {

            }
        };
        socket.start();
        System.out.println("listening websocket p2p port on: " + port);
    }

    /**
     * 相应peer的信息请求
     * @param webSocket
     * @param s
     */
    private void handleMessage(WebSocket webSocket, String s) {
        try {
            Message message = JSON.parseObject(s, Message.class);
            System.out.println("Received message" + JSON.toJSONString(message));
            switch (message.getType()) {
                case QUERY_LATEST_BLOCK:
                    write(webSocket, responseLatestMsg());
                    break;
                case QUERY_ALL_BLOCKS:
                    write(webSocket, responseChainMsg());
                    break;
                case RESPONSE_BLOCKCHAIN:
                    handleBlockChainResponse(message.getData());
                    break;
                case QUERY_ALL_PEERS:
                    write(webSocket,responseAllPeers());
                    break;
                case RESPONSE_ALL_PEERS:
                    handlePeersResponse(message.getData());
                    break;
                case REQUEST_NEGOTIATION:
                    N=sockets.size()/3;
                    if(viewState==ViewState.WatingNagotiation){
                        viewState=ViewState.Nagotiation;
                    }
                    if(viewState==ViewState.Nagotiation){
                        broadcast(responseACK());
                        viewState=ViewState.WaitingACK;
                    }
                    break;
                case RESPONSE_ACK:
                    ACK tempACK=new ACK(message.getData());
                    //TODO 加入线程锁
                    if(viewState==ViewState.WaitingACK && checkACK(tempACK)){
                        NCounter++;
                        acks.add(tempACK);
                        if(NCounter>=2*N+1){
                            viewState=ViewState.WritingVBlock;
                            writeVBlock();
                        }
                    }
                    break;
            }
        } catch (Exception e) {
            System.out.println("hanle message is error:" + e.getMessage());
        }
    }

    //TODO 写入虚区块
    private void writeVBlock() {

    }

    /**
     * 处理接收到的区块链
     * @param message
     */
    private void handleBlockChainResponse(String message) {
        List<Block> receiveBlocks = JSON.parseArray(message, Block.class);
        Collections.sort(receiveBlocks, new Comparator<Block>() {
            public int compare(Block o1, Block o2) {
                return o1.getIndex() - o1.getIndex();
            }
        });

        Block latestBlockReceived = receiveBlocks.get(receiveBlocks.size() - 1);
        Block latestBlock = blockService.getLatestBlock();
        if (latestBlockReceived.getIndex() > latestBlock.getIndex()) {
            if (latestBlock.getHash().equals(latestBlockReceived.getPreviousHash())) {
                System.out.println("We can append the received block to our chain");
                blockService.addBlock(latestBlockReceived);
                broadcast(responseLatestMsg());
            } else if (receiveBlocks.size() == 1) {
                System.out.println("We have to query the chain from our peer");
                broadcast(queryAllMsg());
            } else {
                blockService.replaceChain(receiveBlocks);
            }
        } else {
            System.out.println("received blockchain is not longer than received blockchain. Do nothing");
        }
    }

    /**
     * 处理接收到的节点
     * @param message
     */
    private void handlePeersResponse(String message){
        List<String> _peers = JSON.parseArray(message,String.class);
        for (String _peer : _peers) {
            if(!peers.contains(_peer)){
                connectToPeer(_peer);
            }
        }
    }

    public void connectToPeer(String peer) {
        try {
            final WebSocketClient socket = new WebSocketClient(new URI(peer)) {
                @Override
                public void onOpen(ServerHandshake serverHandshake) {
                    write(this, queryChainLengthMsg());
                    write(this,queryAllPeers());
                    sockets.add(this);
                    peers.add(peer);
                }

                @Override
                public void onMessage(String s) {
                    //handleMessage(this, s);
                    Thread thread = new HandleMsgThread(this,s);
                    pool.execute(thread);
                }

                @Override
                public void onClose(int i, String s, boolean b) {
                    System.out.println("connection failed");
                    sockets.remove(this);
                    peers.remove(peer);
                }

                @Override
                public void onError(Exception e) {
                    System.out.println("connection failed");
                    sockets.remove(this);
                    peers.remove(peer);
                }
            };
            socket.connect();
        } catch (URISyntaxException e) {
            System.out.println("p2p connect is error:" + e.getMessage());
        }

    }

    private void write(WebSocket ws, String message) {
        ws.send(message);
    }

    public void broadcast(String message) {
        for (WebSocket socket : sockets) {
            this.write(socket, message);
        }
    }

    //TODO 检查ack的合法性
    private boolean checkACK(ACK ack){
        return true;
    }

    private String queryAllMsg() {
        return JSON.toJSONString(new Message(QUERY_ALL_BLOCKS));
    }

    private String queryChainLengthMsg() {
        return JSON.toJSONString(new Message(QUERY_LATEST_BLOCK));
    }

    private String queryAllPeers(){
        return JSON.toJSONString(new Message(QUERY_ALL_PEERS));
    }

    private String requestNagotiation(){
        return JSON.toJSONString(new Message(REQUEST_NEGOTIATION));
    }

    private String responseChainMsg() {
        return JSON.toJSONString(new Message(RESPONSE_BLOCKCHAIN, JSON.toJSONString(blockService.getBlockChain())));
    }

    public String responseLatestMsg() {
        Block[] blocks = {blockService.getLatestBlock()};
        return JSON.toJSONString(new Message(RESPONSE_BLOCKCHAIN, JSON.toJSONString(blocks)));
    }

    private String responseAllPeers(){
        return JSON.toJSONString(new Message(RESPONSE_ALL_PEERS,JSON.toJSONString(peers.toArray())));
    }

    //TODO 完善数字签名
    private String responseACK(){
        ACK ack =new ACK();
        ack.setVN(this.VN);
        ack.setSign("temp");
        return JSON.toJSONString(new Message(RESPONSE_ACK,JSON.toJSONString(ack)));
    }

    public List<WebSocket> getSockets() {
        return sockets;
    }

    @Override
    public void doPerHour00() {
        switch (this.viewState){
            case WatingNagotiation:
                this.viewState=ViewState.Nagotiation;
                broadcast(requestNagotiation());
                break;
            default:break;
        }
    }

    @Override
    public void doPerHour45() {
        broadcast(queryAllPeers());
    }

    class HandleMsgThread extends Thread{
        private WebSocket ws;
        private String s;

        public HandleMsgThread(WebSocket ws, String s) {
            this.ws = ws;
            this.s = s;
        }

        @Override
        public void run() {
            handleMessage(ws, s);
        }

    }

    @Override
    public void doPerHour59() {
        this.viewState=ViewState.WatingNagotiation;
    }

    @Override
    public void doPerHour01() {
        switch (this.viewState){
            case Nagotiation:
                this.viewState=ViewState.Running;
                break;
                default:break;
        }
    }
}
