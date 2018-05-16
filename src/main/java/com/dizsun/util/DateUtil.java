package com.dizsun.util;

import java.text.SimpleDateFormat;
import java.util.Date;

public class DateUtil {
    Date date;
    SimpleDateFormat sdf;

    public DateUtil() {}

    public int getCurrentMinute(){
        sdf = new SimpleDateFormat("mm");
        date=new Date();
        return Integer.valueOf(sdf.format(date));
    }

    public int getCurrentSecond(){
        sdf = new SimpleDateFormat("ss");
        date=new Date();
        return Integer.valueOf(sdf.format(date));
    }
}
