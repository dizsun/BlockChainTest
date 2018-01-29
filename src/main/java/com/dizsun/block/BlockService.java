package com.dizsun.block;

import java.util.ArrayList;
import java.util.List;


public class BlockService {
    private List<Block> blockChain;

    public BlockService() {
        this.blockChain = new ArrayList<Block>();
        blockChain.add(this.getFirstBlock());
    }

    private String calculateHash(int index, String previousHash, long timestamp, String data) {
        StringBuilder builder = new StringBuilder(index);
        builder.append(previousHash).append(timestamp).append(data);
        return CryptoUtil.getSHA256(builder.toString());
    }

    public Block getLatestBlock() {
        return blockChain.get(blockChain.size() - 1);
    }

    private Block getFirstBlock() {
        return new Block(1, "0", 0, "Hello Block", "1db6aa3c81dc4b05a49eaed6feba99ed4ef07aa418d10bfbbc12af68cab6fb2a");
    }

    public Block generateNextBlock(String blockData) {
        Block previousBlock = this.getLatestBlock();
        int nextIndex = previousBlock.getIndex() + 1;
        long nextTimestamp = System.currentTimeMillis();
        String nextHash = calculateHash(nextIndex, previousBlock.getHash(), nextTimestamp, blockData);
        return new Block(nextIndex, previousBlock.getHash(), nextTimestamp, blockData, nextHash);
    }

    public void addBlock(Block newBlock) {
        if (isValidNewBlock(newBlock, getLatestBlock())) {
            blockChain.add(newBlock);
        }
    }

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
        }
        return true;
    }

    public void replaceChain(List<Block> newBlocks) {
        if (isValidBlocks(newBlocks) && newBlocks.size() > blockChain.size()) {
            blockChain = newBlocks;
        } else {
            System.out.println("Received blockchain invalid");
        }
    }

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

    public List<Block> getBlockChain() {
        return blockChain;
    }
}
