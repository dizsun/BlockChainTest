package com.dizsun.Service;


import com.alibaba.fastjson.JSON;
import com.dizsun.block.*;
import com.dizsun.component.ACK;
import com.dizsun.component.VACK;
import com.dizsun.component.VBlock;
import com.dizsun.util.ISubscriber;
import com.dizsun.util.RSAUtil;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class P2PService implements ISubscriber {
    //    private List<WebSocket> sockets;    //节点的套接字集合
//    private HashSet<String> peers;  //节点的URI集合
    private BlockService blockService;
    private VBlockService vBlockService;
    private ExecutorService pool;   //线程池
    private final Object ackLock = new Object();  //接收ack的线程锁
    private final Object vackLock = new Object();  //接收vack的线程锁
    private final Object vBlockLock = new Object();  //写vblock的线程锁
    private final Object blockLock = new Object();  //写block的线程锁
    private final Object peerLock = new Object();  //写peer的线程锁
    private RSAUtil rsaUtill;
    private PeerService peerService;

    private final static int QUERY_LATEST_BLOCK = 0;
    private final static int QUERY_ALL_BLOCKS = 1;
    private final static int RESPONSE_BLOCKCHAIN = 2;
    private final static int QUERY_ALL_PEERS = 3;
    private final static int RESPONSE_ALL_PEERS = 4;
    private final static int REQUEST_NEGOTIATION = 5;
    private final static int RESPONSE_ACK = 6;
    private final static int RESPONSE_VBLOCK = 7;
    private final static int RESPONSE_BLOCK = 8;
    private final static int RESPONSE_VBLOCKCHAIN = 9;
    private final static int QUERY_ALL_VBLOCKS = 10;
    private final static int RESPONSE_VACK = 11;


    private enum ViewState {
        Negotiation,    //协商状态,此时各个节点协商是否开始竞选
        WatingNegotiation,  //等待协商状态,此时等待其他节点发送协商请求
        WaitingACK,     //等待其他节点协商同意
        WaitingVACK,     //等待其他节点协商同意
        Running,    //系统正常运行状态
        WritingBlock,
        WaitingBlock,
        WritingVBlock
    }

    public static final int LT = 1000 * 60 * 60;
    //view number
    private int VN;
    //节点数3N+0,1,2
    private int N = 1;
//    private int NCounter = 1;
//    private int VCounter = 1;
    private ViewState viewState = ViewState.Running;
    //    private List<Block> vBlocks;
    private List<ACK> acks;
    private List<VACK> vacks;


    public P2PService() {
        this.blockService = BlockService.newBlockService();
//        this.sockets = new ArrayList<>();
//        this.peers = new HashSet<>();
        this.pool = Executors.newCachedThreadPool();
        this.acks = new ArrayList<>();
        this.vacks = new ArrayList<>();
        this.VN = 0;
        this.rsaUtill = RSAUtil.getInstance();
        this.vBlockService = VBlockService.newVBlockService();
        this.peerService = PeerService.newPeerService(this);
    }

    public void initP2PServer(int port) {
        final WebSocketServer socket = new WebSocketServer(new InetSocketAddress(port)) {
            public void onOpen(WebSocket webSocket, ClientHandshake clientHandshake) {
                peerService.write(webSocket, queryChainLengthMsg());
                peerService.addPeer(webSocket);
            }

            public void onClose(WebSocket webSocket, int i, String s, boolean b) {
                System.out.println("connection failed to peer:" + webSocket.getRemoteSocketAddress());
                peerService.removePeer(webSocket);
            }

            public void onMessage(WebSocket webSocket, String s) {
                Thread thread = new HandleMsgThread(webSocket, s);
                pool.execute(thread);
            }

            public void onError(WebSocket webSocket, Exception e) {
                System.out.println("connection failed to peer:" + webSocket.getRemoteSocketAddress());
                peerService.removePeer(webSocket);
            }

            public void onStart() {

            }
        };
        socket.start();
        System.out.println("listening websocket p2p port on: " + port);
    }

    /**
     * 相应peer的信息请求
     *
     * @param webSocket
     * @param s
     */
    private void handleMessage(WebSocket webSocket, String s) {
        try {
            Message message = JSON.parseObject(s, Message.class);
            System.out.println("Received message" + JSON.toJSONString(message));
            switch (message.getType()) {
                case QUERY_LATEST_BLOCK:
                    System.out.println("对方请求最新block...");
                    peerService.write(webSocket, responseLatestMsg());
                    break;
                case QUERY_ALL_BLOCKS:
                    System.out.println("对方请求所以block...");
                    peerService.write(webSocket, responseChainMsg());
                    synchronized (blockLock) {
                        handleBlockChainResponse(message.getData());
                    }
                    break;
                case QUERY_ALL_VBLOCKS:
                    System.out.println("对方请求所有vblock...");
                    peerService.write(webSocket, responseVChainMsg());
                    synchronized (vBlockLock) {
                        handleVBlockChainResponse(message.getData());
                    }
                    System.out.println("VChain length:" + vBlockService.getBlockChain().size());
                    break;
                case QUERY_ALL_PEERS:
                    System.out.println("对方请求所有peer...");
                    peerService.write(webSocket, responseAllPeers());
                    synchronized (peerLock) {
                        handlePeersResponse(message.getData());
                    }
                    break;
                case RESPONSE_BLOCKCHAIN:
                    System.out.println("收到blocks...");
                    synchronized (blockLock) {
                        handleBlockChainResponse(message.getData());
                    }
                    break;
                case RESPONSE_VBLOCKCHAIN:
                    System.out.println("收到vblocks...");
                    synchronized (vBlockLock) {
                        handleVBlockChainResponse(message.getData());
                    }
                    break;
                case RESPONSE_ALL_PEERS:
                    System.out.println("收到所有peer...");
                    synchronized (peerLock) {
                        handlePeersResponse(message.getData());
                    }
                    break;
                case REQUEST_NEGOTIATION:
                    System.out.println("收到协商请求...");
                    N = (peerService.length()+1) / 3;
                    System.out.println("N的大小:" + N);
                    if (viewState == ViewState.WatingNegotiation) {
                        System.out.println("广播ACK");
                        peerService.broadcast(responseACK());
                        viewState = ViewState.WaitingACK;
                    }
                    break;
                case RESPONSE_ACK:
                    System.out.println("收到ACK...");
                    ACK tempACK = new ACK(message.getData());
                    System.out.println("ACK正确性:" + checkACK(tempACK));
                    synchronized (ackLock) {
                        if (viewState == ViewState.WaitingACK && checkACK(tempACK)) {
                            acks.add(tempACK);
                            System.out.println("接收到的ACK数:" + acks.size()+",是否满足写虚区块条件:"+(acks.size() >= 2 * N));
                            if (acks.size() >= 2 * N) {
                                viewState = ViewState.WritingVBlock;
                                System.out.println("写虚区块");
                                writeVBlock();
                                viewState = ViewState.WaitingVACK;
                                System.out.println("广播虚区块");
                                peerService.broadcast(responseVBlock());
                            }
                        }
                    }
                    break;
                case RESPONSE_VBLOCK:
                    System.out.println("收到vblocks...");
                    switch (viewState) {
                        case Running:
                        case WaitingVACK:
                        case WritingBlock:
                        case WaitingBlock:
                            break;
                        case WritingVBlock:
                        case WaitingACK:
                        case Negotiation:
                        case WatingNegotiation:
                            System.out.println("停止写虚区块");
                            stopWriteVBlock();
                            synchronized (vBlockLock) {
                                handleVBlockChainResponse(message.getData());
                            }
                            viewState = ViewState.WaitingBlock;
                            System.out.println("回复VACK");
                            peerService.write(webSocket, responseVACK());
                            break;

                    }
                    break;
                case RESPONSE_VACK:
                    System.out.println("收到VACK...");
                    VACK tempVACK = new VACK(message.getData());
                    System.out.println("VACK正确性:" + checkACK(tempVACK));
                    synchronized (vackLock) {
                        if (viewState == ViewState.WaitingVACK && checkACK(tempVACK)) {
                            vacks.add(tempVACK);
                            System.out.println("接收到的vack数:" + vacks.size());
                            System.out.println("vack判定:" + (vacks.size() >= 2 * N));
                            if (vacks.size() >= 2 * N) {
                                viewState = ViewState.WritingBlock;
                                System.out.println("写区块");
                                writeBlock();
                                System.out.println("广播区块");
                                peerService.broadcast(responseBlock());
                                viewState = ViewState.Running;
                            }
                        }
                    }
                    break;
                case RESPONSE_BLOCK:
                    System.out.println("收到区块");
                    switch (viewState) {
                        case Running:
                            break;
                        case WritingVBlock:
                        case WritingBlock:
                        case WaitingACK:
                        case Negotiation:
                        case WaitingBlock:
                        case WatingNegotiation:
                        case WaitingVACK:
                            synchronized (blockLock) {
                                handleBlockChainResponse(message.getData());
                            }
                            viewState = ViewState.Running;
                            break;

                    }
                    break;
            }
        } catch (Exception e) {
            System.out.println("hanle message is error:" + e.getMessage());
            e.printStackTrace();
        }
    }

    private void stopWriteVBlock() {
        synchronized (vBlockLock) {
            if (viewState == ViewState.WritingVBlock) {
                viewState = ViewState.WaitingBlock;
            }
            if (vBlockService.getLatestBlock().getViewNumber() == VN + 1) {
                vBlockService.rollback();
            }
        }
    }

    private void writeBlock() {
        blockService.addBlock(blockService.generateNextBlock(getTimeFromTC()));
    }

    //TODO 写入虚区块,要验证合法性,即区块必须包含所有同意的ACK
    private void writeVBlock() {
        System.out.println(vBlockService.getJSONData(acks));
        VBlock vBlock = vBlockService.generateNextBlock(VN + 1, vBlockService.getJSONData(acks));
        synchronized (vBlockLock) {
            if (viewState == ViewState.WritingVBlock)
                vBlockService.addBlock(vBlock);
        }
    }

    private String getTimeFromTC() {
        return "time0000" + (VN + 1);
    }

    /**
     * 处理接收到的区块链
     *
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
                peerService.broadcast(responseLatestMsg());
            } else if (receiveBlocks.size() == 1) {
                System.out.println("We have to query the chain from our peer");
                peerService.broadcast(queryAllMsg());
            } else {
                blockService.replaceChain(receiveBlocks);
            }
        } else {
            System.out.println("received blockchain is not longer than received blockchain. Do nothing");
        }
    }

    /**
     * 处理接收到的虚区块链
     *
     * @param message
     */
    private void handleVBlockChainResponse(String message) {
        List<VBlock> receiveBlocks = JSON.parseArray(message, VBlock.class);
        Collections.sort(receiveBlocks, new Comparator<VBlock>() {
            public int compare(VBlock o1, VBlock o2) {
                return o1.getIndex() - o1.getIndex();
            }
        });
        VBlock latestBlockReceived = receiveBlocks.get(receiveBlocks.size() - 1);
//        if(receiveBlocks.size()>=1){
//            latestBlockReceived = receiveBlocks.get(receiveBlocks.size() - 1);
//        }
        VBlock latestBlock = vBlockService.getLatestBlock();
        if (latestBlockReceived.getIndex() > latestBlock.getIndex()) {
            if (latestBlock.getHash().equals(latestBlockReceived.getPreviousHash())) {
                System.out.println("We can append the received vblock to our vchain");
                vBlockService.addBlock(latestBlockReceived);
                peerService.broadcast(responseLatestVMsg());
            } else if (receiveBlocks.size() == 1) {
                System.out.println("We have to query the vchain from our peer");
                peerService.broadcast(queryAllVMsg());
            } else {
                vBlockService.replaceChain(receiveBlocks);
            }
        } else {
            System.out.println("received vblockchain is not longer than received vblockchain. Do nothing");
        }
    }

    /**
     * 处理接收到的节点
     *
     * @param message
     */
    private void handlePeersResponse(String message) {
        List<String> _peers = JSON.parseArray(message, String.class);
        for (String _peer : _peers) {
            peerService.connectToPeer(_peer);
        }
    }

    public void handleMsgThred(WebSocket webSocket, String msg) {
        Thread thread = new HandleMsgThread(webSocket, msg);
        pool.execute(thread);
    }

    private boolean checkACK(ACK ack) {
        System.out.println("ack检测一:" + ack.getVN() + "," + this.VN);
        if (ack.getVN() != this.VN) {
            return false;
        }
        String sign = rsaUtill.decrypt(ack.getPublicKey(), ack.getSign());
        if (!sign.equals(ack.getPublicKey() + ack.getVN()))
            return false;
        return true;
    }

    private boolean checkACK(VACK ack) {
        System.out.println("vack检测一:" + ack.getVN() + "," + this.VN);
        if (ack.getVN() != this.VN)
            return false;
        String sign = rsaUtill.decrypt(ack.getPublicKey(), ack.getSign());
        if (!sign.equals(ack.getPublicKey() + ack.getVN()))
            return false;
        return true;
    }

    public String queryAllMsg() {
        return JSON.toJSONString(new Message(QUERY_ALL_BLOCKS, JSON.toJSONString(blockService.getBlockChain())));
    }

    public String queryAllVMsg() {
        return JSON.toJSONString(new Message(QUERY_ALL_VBLOCKS, JSON.toJSONString(vBlockService.getBlockChain())));
    }

    public String queryChainLengthMsg() {
        return JSON.toJSONString(new Message(QUERY_LATEST_BLOCK));
    }

    public String queryAllPeers() {
        return JSON.toJSONString(new Message(QUERY_ALL_PEERS, JSON.toJSONString(peerService.getPeerArray())));
    }

    public String requestNagotiation() {
        return JSON.toJSONString(new Message(REQUEST_NEGOTIATION));
    }

    public String responseChainMsg() {
        return JSON.toJSONString(new Message(RESPONSE_BLOCKCHAIN, JSON.toJSONString(blockService.getBlockChain())));
    }

    public String responseLatestMsg() {
        Block[] blocks = {blockService.getLatestBlock()};
        return JSON.toJSONString(new Message(RESPONSE_BLOCKCHAIN, JSON.toJSONString(blocks)));
    }

    public String responseVChainMsg() {
        return JSON.toJSONString(new Message(RESPONSE_VBLOCKCHAIN, JSON.toJSONString(vBlockService.getBlockChain())));
    }

    public String responseLatestVMsg() {
        VBlock[] vblocks = {vBlockService.getLatestBlock()};
        return JSON.toJSONString(new Message(RESPONSE_VBLOCKCHAIN, JSON.toJSONString(vblocks)));
    }

    public String responseVBlock() {
        VBlock[] vblocks = {vBlockService.getLatestBlock()};
        return JSON.toJSONString(new Message(RESPONSE_VBLOCK, JSON.toJSONString(vblocks)));
    }

    public String responseBlock() {
        return JSON.toJSONString(new Message(RESPONSE_BLOCK, JSON.toJSONString(blockService.getBlockChain())));
    }

    public String responseAllPeers() {
        return JSON.toJSONString(new Message(RESPONSE_ALL_PEERS, JSON.toJSONString(peerService.getPeerArray())));
    }

    public String responseACK() {
        ACK ack = new ACK();
        ack.setVN(this.VN);
        ack.setPublicKey(rsaUtill.getPublicKeyBase64());
        ack.setSign(rsaUtill.encrypt(rsaUtill.getPublicKeyBase64() + this.VN));
        return JSON.toJSONString(new Message(RESPONSE_ACK, JSON.toJSONString(ack)));
    }

    public String responseVACK() {
        VACK ack = new VACK();
        ack.setVN(this.VN);
        ack.setPublicKey(rsaUtill.getPublicKeyBase64());
        ack.setSign(rsaUtill.encrypt(rsaUtill.getPublicKeyBase64() + this.VN));
        return JSON.toJSONString(new Message(RESPONSE_VACK, JSON.toJSONString(ack)));
    }


    @Override
    public void doPerHour00() {
        System.out.println("进入00,此时VN=" + VN);
        switch (this.viewState) {
            case WatingNegotiation:
                N = (peerService.length()+1) / 3;
                this.viewState = ViewState.WaitingACK;
                peerService.broadcast(requestNagotiation());
                break;
            default:
                break;
        }
    }

    @Override
    public void doPerHour45() {
        System.out.println("进入45,此时VN=" + VN);
        peerService.broadcast(queryAllPeers());
    }

    @Override
    public void doPerHour59() {
        N = (peerService.length()+1) / 3;
        System.out.println("进入59,此时VN=" + VN+",N="+N);
        this.viewState = ViewState.WatingNegotiation;
    }

    @Override
    public void doPerHour01() {
        System.out.println("进入01,此时VN=" + VN);
//        switch (this.viewState) {
//            case WatingNegotiation:
////                this.viewState=ViewState.Running;
////                break;
//            case WaitingACK:
//                this.viewState = ViewState.Running;
//                break;
//        }
        this.viewState = ViewState.Running;
        VN++;
        acks.clear();
        vacks.clear();
    }

    class HandleMsgThread extends Thread {
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
}