package com.dizsun.block;

import com.dizsun.util.CryptoUtil;

public class Main {
    private static String Drivde="org.sqlite.JDBC";

    public static void main(String[] args) {
        if (args != null && (args.length == 2 || args.length == 3)) {
            try {
                int httpPort = Integer.valueOf(args[0]);
                int p2pPort = Integer.valueOf(args[1]);
                BlockService blockService = new BlockService();
                P2PService p2pService = new P2PService(blockService);
                p2pService.initP2PServer(p2pPort);
                if (args.length == 3 && args[2] != null) {
                    p2pService.connectToPeer(args[2]);
                }
                HTTPService httpService = new HTTPService(blockService, p2pService);
                httpService.initHTTPServer(httpPort);
            } catch (Exception e) {
                System.out.println("startup is error:" + e.getMessage());
            }
        } else {
            System.out.println("usage: java -jar naivechain.jar 8080 6001");
        }

//        System.out.println(CryptoUtil.getSHA256("100Hello Block"));



//        System.out.println(createProofOfWork(83337,"1db6aa3c81dc4b05a49eaed6feba99ed4ef07aa418d10bfbbc12af68cab6fb2a"));

    }
//    static boolean isValidProof(int lastProof,int proof,String previousHash){
//        String guess=""+lastProof+proof+previousHash;
//        String result = CryptoUtil.getSHA256(guess);
//        return result.startsWith("00000");
//    }
//
//    /**
//     * 创建工作量证明
//     * @param lastProof
//     * @param previousHash
//     * @return
//     */
//    static int createProofOfWork(int lastProof,String previousHash){
//        int proof=0;
//        while (!isValidProof(lastProof,proof,previousHash))
//            proof++;
//        return proof;
//    }
}
