package com.dizsun.component;

public class VBlock {
    private int index;
    private String previousHash;
    private long timestamp;
    private int viewNumber;
    private String data;
    private String hash;
    private int proof;

    public VBlock() {
    }

    public VBlock(int index, String previousHash, long timestamp, int viewNumber, String data, String hash, int proof) {
        this.index = index;
        this.previousHash = previousHash;
        this.timestamp = timestamp;
        this.viewNumber = viewNumber;
        this.data = data;
        this.hash = hash;
        this.proof = proof;
    }

    @Override
    public boolean equals(Object obj) {
        VBlock block2=(VBlock)obj;
        if(this.hash.equals(block2.hash))
            return true;
        else return false;
    }

    @Override
    public String toString() {
        return "VBlock{" +
                "index=" + index +
                ", previousHash='" + previousHash + '\'' +
                ", timestamp=" + timestamp +
                ", data='" + data + '\'' +
                ", hash='" + hash + '\'' +
                ", proof=" + proof +
                '}';
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public int getViewNumber() {
        return viewNumber;
    }

    public void setViewNumber(int viewNumber) {
        this.viewNumber = viewNumber;
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
