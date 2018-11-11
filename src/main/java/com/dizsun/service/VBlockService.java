package com.dizsun.service;

import com.alibaba.fastjson.JSON;
import com.dizsun.component.ACK;
import com.dizsun.component.VBlock;
import com.dizsun.util.CryptoUtil;

import java.util.ArrayList;
import java.util.List;

public class VBlockService {
    private List<VBlock> blockChain;
    private static VBlockService vBlockService;
    private VBlockService() {
        this.blockChain = new ArrayList<>();
        this.blockChain.add(this.getFirstBlock());
    }

    public static VBlockService newVBlockService(){
        if(vBlockService==null){
            vBlockService=new VBlockService();
        }
        return vBlockService;
    }

    /**
     * 计算区块hash
     * 将(索引+前一个区块hash+时间戳+数据)进行hash
     * @param index
     * @param previousHash
     * @param timestamp
     * @param data
     * @return
     */
    private String calculateHash(int index, String previousHash, long timestamp, String data) {
        StringBuilder builder = new StringBuilder(index);
        builder.append(previousHash).append(timestamp).append(data);
        return CryptoUtil.getSHA256(builder.toString());
    }

    public VBlock getLatestBlock() {
        return blockChain.get(blockChain.size() - 1);
    }

    private VBlock getFirstBlock() {
        return new VBlock(1, "0", 0,0, "Hello VBlock", "1db6aa3c81dc4b05a49eaed6feba99ed4ef07aa418d10bfbbc12af68cab6fb2a");
    }

    /**
     * 生成新区块
     * @param blockData
     * @return
     */
    public VBlock generateNextBlock(int viewNumber,String blockData) {
        VBlock previousBlock = this.getLatestBlock();
        int nextIndex = previousBlock.getIndex() + 1;
        long nextTimestamp = System.currentTimeMillis();
        String nextHash = calculateHash(nextIndex, previousBlock.getHash(), nextTimestamp, blockData);
//        int proof=createProofOfWork(previousBlock.getProof(),previousBlock.getHash());
        return new VBlock(nextIndex, previousBlock.getHash(), nextTimestamp,viewNumber, blockData, nextHash);
    }

    public void addBlock(VBlock newBlock) {
        if (isValidNewBlock(newBlock, getLatestBlock())) {
            blockChain.add(newBlock);
        }
        garbageCollection();
    }

    /**
     * 验证新区块是否合法
     * @param newBlock
     * @param previousBlock
     * @return
     */
    private boolean isValidNewBlock(VBlock newBlock, VBlock previousBlock) {
        if (previousBlock.getIndex() + 1 != newBlock.getIndex()) {
            System.out.println("无效的 index of vBlock");
            return false;
        } else if (!previousBlock.getHash().equals(newBlock.getPreviousHash())) {
            System.out.println("无效的 previoushash of vBlock");
            return false;
        } else {
            String hash = calculateHash(newBlock.getIndex(), newBlock.getPreviousHash(), newBlock.getTimestamp(),
                    newBlock.getData());
            if (!hash.equals(newBlock.getHash())) {
                System.out.println("无效的 hash: " + hash + " " + newBlock.getHash() + "of vBlock");
                return false;
            }
//            if(!isValidProof(previousBlock.getProof(),newBlock.getProof(),previousBlock.getHash())) {
//                System.out.println("无效的证明:"+newBlock.getProof() + "of vBlock");
//                return false;
//            }
        }
        return true;
    }

    /**
     * 用新链替换旧链
     * @param newBlocks 新的vBlock链
     */
    public void replaceChain(List<VBlock> newBlocks) {
        if (isValidBlocks(newBlocks) && newBlocks.size() > blockChain.size()) {
            blockChain = newBlocks;
        } else {
            System.out.println("收到的区块链为无效链");
        }
    }

    /**
     * 验证区块链是否合法
     * @param newBlocks
     * @return
     */
    private boolean isValidBlocks(List<VBlock> newBlocks) {
        VBlock firstBlock = newBlocks.get(0);
        if (!firstBlock.equals(getFirstBlock())) {
            return false;
        }

        for (int i = 1; i < newBlocks.size(); i++) {
            if (!isValidNewBlock(newBlocks.get(i), newBlocks.get(i-1))){
                return false;
            }
        }
        return true;
    }

    /**
     * 验证工作量是否正确
     * TODO 工作量应该是有动态调节功能保证建立区块的时间稳定的,此处暂时没有写此功能
     * @param lastProof
     * @param proof
     * @param previousHash
     * @return
     */
    private boolean isValidProof(int lastProof,int proof,String previousHash){
        String guess=""+lastProof+proof+previousHash;
        String result = CryptoUtil.getSHA256(guess);
        return result.startsWith("0000");
    }

    /**
     * 创建工作量证明
     * @param lastProof
     * @param previousHash
     * @return
     */
    private int createProofOfWork(int lastProof,String previousHash){
        int proof=0;
        while (!isValidProof(lastProof,proof,previousHash))
            proof++;
        return proof;
    }

    public List<VBlock> getBlockChain() {
        return blockChain;
    }

    //TODO 垃圾回收机制,将区块个数限制在7个以内
    private void garbageCollection(){
        if(this.blockChain.size()>7){
            this.blockChain.remove(0);
        }
    }

    public void rollback(){
        if(this.blockChain.size()>0){
            this.blockChain.remove(this.blockChain.size()-1);
        }
    }

    public String getJSONData(List<ACK> acks){
        return JSON.toJSONString(acks);
    }
}
