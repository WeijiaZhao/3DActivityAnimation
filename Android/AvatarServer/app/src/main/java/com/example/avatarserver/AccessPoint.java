package com.example.avatarserver;

import java.util.Arrays;

/**
 Create by Weijia Zhao in 03/13/2019
 */

public class AccessPoint {

    public final static String TAG = "Access Point";
    private String SSID;
    private String BSSID;
    private double[] position3d;
    private double distance = -1;

    public AccessPoint(String SSID, double[] position3d) {
        this.SSID = SSID;
//        this.BSSID = BSSID;
        this.position3d = position3d.clone();
    }

    @Override
    public String toString() {

        String str = this.SSID + "[" + this.BSSID + "] : " + Arrays.toString(this.position3d);

        if(distance != -1) {
            str += "\ndistance: " + Math.round(distance * 10) / 10.0 + "inches";
        }
        return str;
    }

    public double[] getPosition3d() {
        return position3d;
    }


    public String getSSID() {
        return SSID;
    }
}
