package com.dizsun.block;

import com.dizsun.util.DateUtil;
import com.dizsun.util.ISubscriber;

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

/**
 * 广播时间变化事件
 */
public class Broadcaster {
    private Timer timer;
    private ArrayList<ISubscriber> subscribers;
    private DateUtil dateUtil;

    public Broadcaster() {
        timer=new Timer();
        subscribers=new ArrayList<>();
        dateUtil=DateUtil.newDataUtil();
    }

    public void broadcast(){
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                if(dateUtil.getCurrentMinute()%5==1){
                    for (ISubscriber s : subscribers) {
                        s.doPerHour45();
                    }
                }
                else if(dateUtil.getCurrentMinute()%5==2){
                    for (ISubscriber s : subscribers) {
                        s.doPerHour59();
                    }
                }
                else if(dateUtil.getCurrentMinute()%5==3){
                    for (ISubscriber s : subscribers) {
                        s.doPerHour00();
                    }
                }
                else if(dateUtil.getCurrentMinute()%5==4){
                    for (ISubscriber s : subscribers) {
                        s.doPerHour01();
                    }
                }
            }
        },1,1000*60);
    }

    public void subscribe(ISubscriber subscriber){
        subscribers.add(subscriber);
    }

    public void unSubscribe(ISubscriber subscriber){
        subscribers.remove(subscriber);
    }

}
