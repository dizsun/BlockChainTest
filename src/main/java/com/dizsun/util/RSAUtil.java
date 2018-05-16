package com.dizsun.util;

import sun.misc.BASE64Decoder;
import sun.misc.BASE64Encoder;

import javax.crypto.Cipher;
import java.security.*;

public class RSAUtil {
    private static final String ALGORITHM = "RSA";
    /**
     * 密钥长度，用来初始化
     */
    private static final int KEYSIZE = 1024;
    private static RSAUtil rsaUtil=null;
    private String publicKeyBase64;
    //private String privateKeyBase64;
    private Key publicKey;
    private Key privateKey;

    private RSAUtil() {
        try {
            /** RSA算法要求有一个可信任的随机数源 */
            SecureRandom secureRandom = new SecureRandom();

            /** 为RSA算法创建一个KeyPairGenerator对象 */
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(ALGORITHM);

            /** 利用上面的随机数据源初始化这个KeyPairGenerator对象 */
            keyPairGenerator.initialize(KEYSIZE, secureRandom);
            //keyPairGenerator.initialize(KEYSIZE);

            /** 生成密匙对 */
            KeyPair keyPair = keyPairGenerator.generateKeyPair();

            /** 得到公钥 */
            this.publicKey = keyPair.getPublic();

            /** 得到私钥 */
            this.privateKey = keyPair.getPrivate();

            byte[] publicKeyBytes = this.publicKey.getEncoded();
            //byte[] privateKeyBytes = privateKey.getEncoded();

            this.publicKeyBase64 = new BASE64Encoder().encode(publicKeyBytes);
            //this.privateKeyBase64 = new BASE64Encoder().encode(privateKeyBytes);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static RSAUtil getInstance(){
        if(rsaUtil==null){
            rsaUtil = new RSAUtil();
        }
        return rsaUtil;
    }

    public String getPublicKeyBase64() {
        return publicKeyBase64;
    }

    /**
     * 加密方法
     *
     * @param source 源数据
     * @return
     * @throws Exception
     */
    public String encrypt(String source){
        try {
            /** 得到Cipher对象来实现对源数据的RSA加密 */
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, this.privateKey);
            byte[] b = source.getBytes();
            /** 执行加密操作 */
            byte[] b1 = cipher.doFinal(b);
            BASE64Encoder encoder = new BASE64Encoder();
            return encoder.encode(b1);
        }catch (Exception e){
            e.printStackTrace();
            return null;
        }

    }

    /**
     * 解密算法
     *
     * @param cryptograph 密文
     * @return
     * @throws Exception
     */
    public String decrypt(String cryptograph){
        try {
            /** 得到Cipher对象对已用私钥加密的数据进行RSA解密 */
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, this.publicKey);
            BASE64Decoder decoder = new BASE64Decoder();
            byte[] b1 = decoder.decodeBuffer(cryptograph);
            /** 执行解密操作 */
            byte[] b = cipher.doFinal(b1);
            return new String(b);
        }catch (Exception e){
            e.printStackTrace();
            return null;
        }

    }
}
