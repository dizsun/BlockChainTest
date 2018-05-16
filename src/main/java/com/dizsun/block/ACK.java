package com.dizsun.block;

import java.io.Serializable;
import com.alibaba.fastjson.JSON;

public class ACK implements Serializable {
    private int VN;
    private String sign;

    public ACK() {
    }

    public ACK(int VN, String sign) {
        this.VN = VN;
        this.sign = sign;
    }

    public ACK(String jsonStr) {
        ACK ack = (ACK) JSON.parse(jsonStr);
        this.VN=ack.getVN();
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
}
