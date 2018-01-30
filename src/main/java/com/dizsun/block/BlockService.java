package com.dizsun.block;

import com.dizsun.util.CryptoUtil;

import java.util.ArrayList;
import java.util.List;


public class BlockService {
    private List<Block> blockChain;

    public BlockService() {
        this.blockChain = new ArrayList<Block>();
        blockChain.add(this.getFirstBlock());
    }

    /**
     * 计算区块hash
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

    public Block getLatestBlock() {
        return blockChain.get(blockChain.size() - 1);
    }

    private Block getFirstBlock() {
        return new Block(1, "0", 0, "Hello Block", "1db6aa3c81dc4b05a49eaed6feba99ed4ef07aa418d10bfbbc12af68cab6fb2a",100);
    }

    /**
     * 生成新区块
     * @param blockData
     * @return
     */
    public Block generateNextBlock(String blockData) {
        Block previousBlock = this.getLatestBlock();
        int nextIndex = previousBlock.getIndex() + 1;
        long nextTimestamp = System.currentTimeMillis();
        String nextHash = calculateHash(nextIndex, previousBlock.getHash(), nextTimestamp, blockData);
        int proof=createProofOfWork(previousBlock.getProof(),previousBlock.getHash());
        return new Block(nextIndex, previousBlock.getHash(), nextTimestamp, blockData, nextHash,proof);
    }

    public void addBlock(Block newBlock) {
        if (isValidNewBlock(newBlock, getLatestBlock())) {
            blockChain.add(newBlock);
        }
    }

    /**
     * 验证新区块是否合法
     * @param newBlock
     * @param previousBlock
     * @return
     */
    private boolean isValidNewBlock(Block newBlock, Block previousBlock) {
        if (previousBlock.getIndex() + 1 != newBlock.getIndex()) {
            System.out.println("invalid index");
            return false;
        } else if (!previousBlock.getHash().equals(newBlock.getPreviousHash())) {
            System.out.println("invalid previoushash");
            return false;
        } else {
            String hash = calculateHash(newBlock.getIndex(), newBlock.getPreviousHash(), newBlock.getTimestamp(),
                    newBlock.getData());
            if (!hash.equals(newBlock.getHash())) {
                System.out.println("invalid hash: " + hash + " " + newBlock.getHash());
                return false;
            }
            if(!isValidProof(previousBlock.getProof(),newBlock.getProof(),previousBlock.getHash()))
                return false;
        }
        return true;
    }

    /**
     * 用新链替换旧链
     * @param newBlocks
     */
    public void replaceChain(List<Block> newBlocks) {
        if (isValidBlocks(newBlocks) && newBlocks.size() > blockChain.size()) {
            blockChain = newBlocks;
        } else {
            System.out.println("Received blockchain invalid");
        }
    }

    /**
     * 验证区块链是否合法
     * @param newBlocks
     * @return
     */
    private boolean isValidBlocks(List<Block> newBlocks) {
        Block firstBlock = newBlocks.get(0);
        if (!firstBlock.equals(getFirstBlock())) {
            return false;
        }

        for (int i = 1; i < newBlocks.size(); i++) {
            if (isValidNewBlock(newBlocks.get(i), firstBlock)) {
                firstBlock = newBlocks.get(i);
            } else {
                return false;
            }
        }
        return true;
    }

    /**
     * 验证工作量是否正确
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

    public List<Block> getBlockChain() {
        return blockChain;
    }
}
