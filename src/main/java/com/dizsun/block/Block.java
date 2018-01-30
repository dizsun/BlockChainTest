package com.dizsun.block;

public class Block {
    private int index;
    private String previousHash;
    private long timestamp;
    private String data;
    private String hash;
    private int proof;

    public Block() {
    }

//    public Block(int index, String previousHash, long timestamp, String data, String hash) {
//        this.index = index;
//        this.previousHash = previousHash;
//        this.timestamp = timestamp;
//        this.data = data;
//        this.hash = hash;
//    }

    public Block(int index, String previousHash, long timestamp, String data, String hash, int proof) {
        this.index = index;
        this.previousHash = previousHash;
        this.timestamp = timestamp;
        this.data = data;
        this.hash = hash;
        this.proof = proof;
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public String getPreviousHash() {
        return previousHash;
    }

    public void setPreviousHash(String previousHash) {
        this.previousHash = previousHash;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    public int getProof() {
        return proof;
    }

    public void setProof(int proof) {
        this.proof = proof;
    }
}
