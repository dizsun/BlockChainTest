package com.dizsun.block;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

public class Main {
    private static String Drivde="org.sqlite.JDBC";

    public static void main(String[] args) {
//        if (args != null && (args.length == 2 || args.length == 3)) {
//            try {
//                int httpPort = Integer.valueOf(args[0]);
//                int p2pPort = Integer.valueOf(args[1]);
//                BlockService blockService = new BlockService();
//                P2PService p2pService = new P2PService(blockService);
//                p2pService.initP2PServer(p2pPort);
//                if (args.length == 3 && args[2] != null) {
//                    p2pService.connectToPeer(args[2]);
//                }
//                HTTPService httpService = new HTTPService(blockService, p2pService);
//                httpService.initHTTPServer(httpPort);
//            } catch (Exception e) {
//                System.out.println("startup is error:" + e.getMessage());
//            }
//        } else {
//            System.out.println("usage: java -jar naivechain.jar 8080 6001");
//        }
//        System.out.println(CryptoUtil.getSHA256("100Hello Block"));
        try {
            Class.forName(Drivde);// 加载驱动,连接sqlite的jdbc
            Connection connection= DriverManager.getConnection("jdbc:sqlite:db/blocks.db");//连接数据库zhou.db,不存在则创建
            Statement statement=connection.createStatement();   //创建连接对象，是Java的一个操作数据库的重要接口
            String sql="create table tables(name varchar(20),pwd varchar(20))";
            statement.executeUpdate("drop table if exists tables");//判断是否有表tables的存在。有则删除
            statement.executeUpdate(sql);            //创建数据库
            statement.executeUpdate("insert into tables values('zhou','156546')");//向数据库中插入数据
            ResultSet rSet=statement.executeQuery("select * from tables");//搜索数据库，将搜索的放入数据集ResultSet中
            while (rSet.next()) {            //遍历这个数据集
                System.out.println("姓名："+rSet.getString(1));//依次输出 也可以这样写 rSet.getString(“name”)
                System.out.println("密码："+rSet.getString("pwd"));
            }
            rSet.close();//关闭数据集
            connection.close();//关闭数据库连接
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }
}
