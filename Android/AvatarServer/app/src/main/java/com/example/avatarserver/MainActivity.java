package com.example.avatarserver;

/**
 Create by Weijia Zhao in 03/01/2019
 */

import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.example.avatarserver.trilateration.NonLinearLeastSquaresSolver;
import com.example.avatarserver.trilateration.TrilaterationFunction;

import org.apache.commons.math3.fitting.leastsquares.LeastSquaresOptimizer;
import org.apache.commons.math3.fitting.leastsquares.LevenbergMarquardtOptimizer;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;

public class MainActivity extends AppCompatActivity {

    TextView textViewAddress;
    TextView textViewPort;
    TextView textViewNotification;
    Button buttonStartServer;
    Button buttonStopServer;

//    Button buttonSend;

    private AvatarServerThread serverThread;
    private String serverIPAddress;
    private final int serverPort = 8888;
    private AvatarOrientation avatarOrientation;

    WifiManager wifiManager;
    WifiReceiver wifiReceiver;
    private int timesScanned = 0;
    private HashMap<String, AccessPoint> accessPointsMap;
    private double[][] accessPointPositions;


    private double boundingBox[][] = new double[][]{{-64.3 * 12, 85.6 * 12},{-56.9 * 12, 18.7 * 12}};

    ProgressDialog progressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textViewAddress = (TextView) findViewById(R.id.address);
        textViewPort = (TextView) findViewById(R.id.port);
        textViewNotification = (TextView) findViewById(R.id.notification);
        buttonStartServer = (Button) findViewById(R.id.startserver);
        buttonStopServer = (Button) findViewById(R.id.stopserver);

//        buttonSend = (Button) findViewById(R.id.send);

        textViewAddress.setVisibility(View.GONE);
        textViewPort.setVisibility(View.GONE);

        buttonStopServer.setVisibility(View.GONE);

        // Set up wifi Manager
        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiManager.isWifiEnabled() == false) {
            Toast.makeText(getApplicationContext(), "WiFi is turned off, turning it on...", Toast.LENGTH_LONG).show();
            wifiManager.setWifiEnabled(true);
        }

        wifiReceiver = new WifiReceiver();
        registerReceiver(wifiReceiver,
                new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));

        initializeAccessPoints();

        // Set up android server socket
        buttonStartServer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (serverThread == null) {

                    serverIPAddress = getIpAddress();

                    textViewAddress.setText(serverIPAddress);
                    textViewPort.setText("Port: " + serverPort + "\n");

                    textViewAddress.setVisibility(View.VISIBLE);
                    textViewPort.setVisibility(View.VISIBLE);

                    serverThread = new AvatarServerThread(serverPort, MainActivity.this);
                    serverThread.start();

//                    orientation = new AvatarOrientation(MainActivity.this);
//                    orientation.start();

                    buttonStartServer.setVisibility(View.GONE);
//                    buttonStopServer.setVisibility(View.VISIBLE);


                }
            }
        });

        buttonStopServer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (serverThread != null) {
                    serverThread.close("Disconnecting...");
                    serverThread = null;
//                    buttonStopServer.setEnabled(false);
//                    buttonSend.setEnabled(false);

                    textViewAddress.setVisibility(View.GONE);
                    textViewPort.setVisibility(View.GONE);

                    buttonStopServer.setVisibility(View.GONE);

                    buttonStartServer.setVisibility(View.VISIBLE);

                    textViewNotification.setText("To better use this service, please start the server first, then in the client side, connect to the IP address provided.\n");

                    avatarOrientation.stop();

                }
            }
        });

//        buttonSend.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View view) {
//                if (serverThread != null) {
//                    sendLocation();
//
//                }
//            }
//        });
    }

    public String getIpAddress() {
        String ipAddress = "";
        try {
            Enumeration<NetworkInterface> enumNetworkInterfaces = NetworkInterface
                    .getNetworkInterfaces();
            while (enumNetworkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = enumNetworkInterfaces
                        .nextElement();
                Enumeration<InetAddress> enumInetAddress = networkInterface
                        .getInetAddresses();
                while (enumInetAddress.hasMoreElements()) {
                    InetAddress inetAddress = enumInetAddress
                            .nextElement();

                    if (inetAddress.isSiteLocalAddress()) {
                        if(!inetAddress.getHostAddress().contains("fec"))
                            ipAddress += "Server running at: \nIP Address: "
                                + inetAddress.getHostAddress();
                    }
                }
            }

        } catch (SocketException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            ipAddress += "Something Wrong! " + e.toString() + "\n";
        }
        return ipAddress;
    }

    public void sendRotation(final float currentRotation) {

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (serverThread != null) {
                    String data = "degree:" + currentRotation;
                    serverThread.sendData(data);

                }
            }
        });
    }

    public void sendLocation() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (serverThread != null) {
                    scanWifi();
                    double[] location = wifiReceiver.getAvatarLocation();
                    if(location != null && (location[0] != 0 && location[1] != 0)) {
                        String data = "location:" + location[0] + "," + location[1] + "," + location[2];
                        serverThread.sendData(data);
                    }

                }
            }
        });
    }

    public void sendMovedDistance(final double distance) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (serverThread != null) {
                    String data = "distance:" + distance;
                    serverThread.sendData(data);

                }
            }
        });
    }

    public void toastMessage(final String message) {

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    public void onServerStart() {

        runOnUiThread(new Runnable() {
            @Override
            public void run() {

                textViewNotification.setText("Connected. You are good to go!");
                textViewAddress.setVisibility(View.VISIBLE);
                textViewPort.setVisibility(View.VISIBLE);
                buttonStopServer.setVisibility(View.VISIBLE);

                avatarOrientation = new AvatarOrientation(MainActivity.this);
                avatarOrientation.start();

                scanWifi();
//                sendLocation();

//                double[] location = getAvatarLocation();
//                sendLocation(location);

            }
        });
    }

    public void onClientDown() {

        runOnUiThread(new Runnable() {
            @Override
            public void run() {

                textViewNotification.setText("Lost connection! Please check the network setting on client side and reconnect to the following address");

                buttonStartServer.setVisibility(View.VISIBLE);
                buttonStopServer.setVisibility(View.GONE);

                avatarOrientation.stop();

            }
        });
    }

    public void performStartClick() {

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                serverThread = null;
                buttonStartServer.performClick();

            }
        });
    }

    public void initializeAccessPoints() {

        accessPointsMap = new HashMap<>();

//        accessPointsMap.put("1133BCAve(2)", new AccessPoint("1133BCAve(2)", new double[] {145, 176, 0}));
//        accessPointsMap.put("SHL126", new AccessPoint("SHL126", new double[] {278, 213, 0}));
//        accessPointsMap.put("SHL", new AccessPoint("SHL", new double[] {278, 87, 0}));
//        accessPointsMap.put("linksys", new AccessPoint("linksys", new double[] {182, 87, 0}));


        accessPointsMap.put("1133BCAve(2)", new AccessPoint("1133BCAve(2)", new double[] {145, 176, 0}));
        accessPointsMap.put("SHL126", new AccessPoint("SHL126", new double[] {410, 213, 0}));
        accessPointsMap.put("SHL", new AccessPoint("SHL", new double[] {278, 87, 0}));
        accessPointsMap.put("linksys", new AccessPoint("linksys", new double[] {0, 0, 0}));

    }


    public void scanWifi() {
        wifiManager.startScan();

    }

    private class WifiReceiver extends BroadcastReceiver {

        final String TAG = "WifiReceiver";
        private double[] avatarLocation;
        private ArrayList<Triple> apList;

        public WifiReceiver() {
            this.avatarLocation = new double[3];
            this.apList = new ArrayList<>();

        }

        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "MainActivity: Wifi Scan Results received");

            ArrayList<ScanResult> results = (ArrayList) wifiManager.getScanResults();

            this.apList = new ArrayList<>();
            for (ScanResult scanResult : results) {
                if (scanResult.SSID.equals("SHL") || scanResult.SSID.equals("SHL126") ||
                        scanResult.SSID.equals("1133BCAve(2)") || scanResult.SSID.equals("linksys")) {
                    Log.e(TAG, scanResult.SSID + "(" + scanResult.BSSID + ") RSSI: " + scanResult.level);

                    apList.add(new Triple(scanResult.SSID, scanResult.level, scanResult.frequency));

                }
            }

            Collections.sort(apList, new Comparator<Triple>() {
                @Override
                public int compare(Triple t1, Triple t2) {
                    return t2.level - t1.level;
                }
            });

            Log.i(TAG, "onReceive: " + apList);
            updateAvatarLocation();

        }

        // Distance between Mobile phone and Access Point in Free Space situation
        // RSSI: received signal strength in dB; frequency: signal frequency in MHz
        // value returned is the distance measured in inches
        public double calculateDistance(double RSSILevel, double frequency) {
            double exp = (27.55 - (20 * Math.log10(frequency)) + Math.abs(RSSILevel)) / 20.0;

            return Math.pow(10.0, exp) / 0.0254;
        }

        public void updateAvatarLocation() {

            accessPointPositions = new double[3][3];
            double[] distances = new double[3];

            for(int i = 0; i < 3; i++) {
                String SSID = apList.get(i).SSID;
                accessPointPositions[i] = accessPointsMap.get(SSID).getPosition3d().clone();

                int RSSILevel = apList.get(i).level;
                int frequency = apList.get(i).frequency;


                distances[i] = calculateDistance(RSSILevel, frequency);
                Log.i("updateAvatarLocation", "distance from " + SSID + " to device: " + distances[i]);
            }

            NonLinearLeastSquaresSolver solver = new NonLinearLeastSquaresSolver(new TrilaterationFunction(accessPointPositions, distances), new LevenbergMarquardtOptimizer());
            LeastSquaresOptimizer.Optimum optimum = solver.solve();
            double[] location = optimum.getPoint().toArray();
            ArrayList<Double> list = new ArrayList<>();
            for(int i = 0; i < location.length; i++) {
                location[i] = Math.round(location[i] * 10) / 10.0;
                list.add(location[i]);
            }

            Log.i(TAG, "updateAvatarLocation: " + list);

            if(isInBoundingBox(location)) {
                Log.i(TAG, "updateAvatarLocation: position is good" );
                avatarLocation = location.clone();
            }


        }

        private double[] getAvatarLocation() {
            return avatarLocation;
        }

        private boolean isInBoundingBox(double[] location) {
            if(location[0] > boundingBox[0][0] && location[0] < boundingBox[0][1] &&
                location[1] > boundingBox[1][0] && location[1] < boundingBox[1][1]) {
                return true;
            }

            return false;
        }

    }

    private class Triple {

        public String SSID;
        public int level;
        public int frequency;

        public Triple(String SSID, int level, int frequency) {
            this.SSID = SSID;
            this.level = level;
            this.frequency = frequency;
        }

        public String toString() {
            return SSID + ": " + level;
        }
    }

}



