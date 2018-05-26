package com.dizsun.component;

import com.alibaba.fastjson.JSON;

public class VACK {
    private int VN;
    private String publicKey;

    private String sign;

    public VACK() {
    }

    public VACK(int VN, String sign) {
        this.VN = VN;
        this.sign = sign;
    }

    public VACK(String jsonStr) {
        VACK ack = JSON.parseObject(jsonStr,VACK.class);
        this.VN=ack.getVN();
        this.publicKey =ack.getPublicKey();
        this.sign=ack.getSign();
    }

    public int getVN() {
        return VN;
    }

    public void setVN(int VN) {
        this.VN = VN;
    }

    public String getSign() {
        return sign;
    }

    public void setSign(String sign) {
        this.sign = sign;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }
}
