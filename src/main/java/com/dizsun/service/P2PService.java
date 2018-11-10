package com.dizsun.service;


import com.alibaba.fastjson.JSON;
import com.dizsun.component.*;
import com.dizsun.util.DateUtil;
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
    private long startTime = 0;
    private long endTime = 0;
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
    private DateUtil dateUtil;

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
        WritingBlock,   //写区块
        WaitingBlock,   //等待区块
        WritingVBlock   //写虚区块
    }

    public static final int LT = 1000 * 60 * 60;
    //view number
    private int VN;
    //节点数3N+0,1,2
    private int N = 1;
    //    private int NCounter = 1;
//    private int VCounter = 1;
    private int stabilityValue = 128;
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
        this.dateUtil = DateUtil.newDataUtil();
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
//                    System.out.println("对方请求最新block...");
                    peerService.write(webSocket, responseLatestMsg());
                    break;
                case QUERY_ALL_BLOCKS:
//                    System.out.println("对方请求所以block...");
                    peerService.write(webSocket, responseChainMsg());
                    synchronized (blockLock) {
                        handleBlockChainResponse(message.getData());
                    }
                    break;
                case QUERY_ALL_VBLOCKS:
//                    System.out.println("对方请求所有vblock...");
                    peerService.write(webSocket, responseVChainMsg());
                    synchronized (vBlockLock) {
                        handleVBlockChainResponse(message.getData());
                    }
//                    System.out.println("VChain length:" + vBlockService.getBlockChain().size());
                    break;
                case QUERY_ALL_PEERS:
//                    System.out.println("对方请求所有peer...");
                    peerService.write(webSocket, responseAllPeers());
                    synchronized (peerLock) {
                        handlePeersResponse(message.getData());
                    }
                    break;
                case RESPONSE_BLOCKCHAIN:
//                    System.out.println("收到blocks...");
                    synchronized (blockLock) {
                        handleBlockChainResponse(message.getData());
                    }
                    break;
                case RESPONSE_VBLOCKCHAIN:
//                    System.out.println("收到vblocks...");
                    synchronized (vBlockLock) {
                        handleVBlockChainResponse(message.getData());
                    }
                    break;
                case RESPONSE_ALL_PEERS:
//                    System.out.println("收到所有peer...");
                    synchronized (peerLock) {
                        handlePeersResponse(message.getData());
                    }
                    break;
                case REQUEST_NEGOTIATION:
//                    System.out.println("收到协商请求...");
                    N = (peerService.length() + 1) / 3;
//                    System.out.println("N的大小:" + N);
                    if (viewState == ViewState.WatingNegotiation) {
                        startTime = System.nanoTime();
//                        System.out.println("广播ACK");
                        peerService.broadcast(responseACK());
                        viewState = ViewState.WaitingACK;
                    }
                    break;
                case RESPONSE_ACK:
//                    System.out.println("收到ACK...");
                    ACK tempACK = new ACK(message.getData());
//                    System.out.println("ACK正确性:" + checkACK(tempACK));
                    synchronized (ackLock) {
                        if (viewState == ViewState.WaitingACK && checkACK(tempACK)) {
                            if (stabilityValue == 1) {
                                peerService.updateSI(webSocket, 1);
                            } else {
                                peerService.updateSI(webSocket, stabilityValue / 2);
                                stabilityValue /= 2;
                            }
                            acks.add(tempACK);
//                            System.out.println("接收到的ACK数:" + acks.size() + ",是否满足写虚区块条件:" + (acks.size() >= 2 * N));
                            if (acks.size() >= 2 * N) {
                                viewState = ViewState.WritingVBlock;
                                writeVBlock();
                                viewState = ViewState.WaitingVACK;
                                peerService.broadcast(responseVBlock());
                            }
                        }
                    }
                    break;
                case RESPONSE_VBLOCK:
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
                            stopWriteVBlock();
                            synchronized (vBlockLock) {
                                handleVBlockChainResponse(message.getData());
                            }
                            viewState = ViewState.WaitingBlock;
                            peerService.write(webSocket, responseVACK());
                            break;

                    }
                    break;
                case RESPONSE_VACK:
                    VACK tempVACK = new VACK(message.getData());
                    synchronized (vackLock) {
                        if (viewState == ViewState.WaitingVACK && checkACK(tempVACK)) {
                            if (stabilityValue == 1) {
                                peerService.updateSI(webSocket, 1);
                            } else {
                                peerService.updateSI(webSocket, stabilityValue / 2);
                                stabilityValue /= 2;
                            }
                            vacks.add(tempVACK);
                            if (vacks.size() >= 2 * N) {
                                viewState = ViewState.WritingBlock;
                                writeBlock();
                                peerService.broadcast(responseBlock());
                                viewState = ViewState.Running;
                                endTime = System.nanoTime() - startTime;
                                System.out.println("Consensus duration:" + endTime / 1000000000.0 + "s");
                            }
                        }
                    }
                    break;
                case RESPONSE_BLOCK:
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
                            endTime = System.nanoTime() - startTime;
                            System.out.println("Consensus duration:" + endTime / 1000000000.0 + "s");
                            break;

                    }
                    break;
            }
        } catch (Exception e) {
            System.out.println("hanle message is error:" + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 停止写虚区块,若是已经计算完毕则回滚
     */
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
        blockService.addBlock(blockService.generateNextBlock(dateUtil.getTimeFromRC()));
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

    public void handleMsgThread(WebSocket webSocket, String msg) {
        Thread thread = new HandleMsgThread(webSocket, msg);
        pool.execute(thread);
    }

    private boolean checkACK(ACK ack) {
//        System.out.println("ack检测一:" + ack.getVN() + "," + this.VN);
        if (ack.getVN() != this.VN) {
            return false;
        }
        String sign = rsaUtill.decrypt(ack.getPublicKey(), ack.getSign());
        if (!sign.equals(ack.getPublicKey() + ack.getVN()))
            return false;
        return true;
    }

    private boolean checkACK(VACK ack) {
//        System.out.println("vack检测一:" + ack.getVN() + "," + this.VN);
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
//        System.out.println("进入00,此时VN=" + VN);
        switch (this.viewState) {
            case WatingNegotiation:
                startTime = System.nanoTime();
                N = (peerService.length() + 1) / 3;
                this.viewState = ViewState.WaitingACK;
                peerService.broadcast(requestNagotiation());
                break;
            default:
                break;
        }
    }

    @Override
    public void doPerHour45() {
//        System.out.println("进入45,此时VN=" + VN);
        peerService.broadcast(queryAllPeers());
    }

    @Override
    public void doPerHour59() {
        N = (peerService.length() + 1) / 3;
//        System.out.println("进入59,此时VN=" + VN + ",N=" + N);
        this.viewState = ViewState.WatingNegotiation;
    }

    @Override
    public void doPerHour01() {
//        System.out.println("进入01,此时VN=" + VN);
        this.viewState = ViewState.Running;
        VN = (VN + 1) % 65535;
        acks.clear();
        vacks.clear();
        startTime = 0;
        endTime = 0;
        stabilityValue=128;
        peerService.updateDelay();
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