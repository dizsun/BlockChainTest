package com.dizsun.util;

import java.io.*;

public class LogUtil {
    public static final String CONSENSUS = "consensus.txt";
    public static final String NTP = "ntp.txt";
    public static void writeLog(String msg, String fileName){
        File file = new File(fileName);
        if(!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        try {
            FileWriter fw  =new FileWriter(file.getName(),true);
            fw.write(msg+"\n");
            fw.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
