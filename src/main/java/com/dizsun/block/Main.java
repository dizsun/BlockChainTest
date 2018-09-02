package com.dizsun.block;

import com.dizsun.util.*;
import com.dizsun.service.*;

import java.io.*;

public class Main {
    private static String Drivder = "org.sqlite.JDBC";
    public static void main(String[] args) {
        File dbFileFolder = new File("./db");
        if(!dbFileFolder.exists()){
            System.out.println("db文件夹不存在!");
            if(dbFileFolder.mkdir()){
                System.out.println("db文件夹创建成功!");
            }else {
                System.out.println("db文件夹创建失败!");
                return;
            }
        }
        File dbFile = new File("./db/blocks.db");
        if(!dbFile.exists()){
            System.out.println("db文件不存在!");
            try {
                SQLUtil sqlUtil=new SQLUtil();
                sqlUtil.initBlocks(null);
                System.out.println("db文件创建成功!");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (args != null && args.length == 2) {
            try {
                Broadcaster broadcaster = new Broadcaster();
                int httpPort = Integer.valueOf(args[0]);
                int p2pPort = Integer.valueOf(args[1]);
                P2PService p2pService = new P2PService();
                broadcaster.subscribe(p2pService);
                p2pService.initP2PServer(p2pPort);
                HTTPService httpService = new HTTPService(p2pService);
                broadcaster.broadcast();
                httpService.initHTTPServer(httpPort);
            } catch (Exception e) {
                System.out.println("startup is error:" + e.getMessage());
            }
        } else {
            System.out.println("usage: java -jar naivechain.jar 9000 6001");
        }
    }
//    public static void main(String[] args) {
//        DateUtil dateUtil=DateUtil.newDataUtil();
//        System.out.println("main:"+dateUtil.getTimeFromRC());
//    }

}