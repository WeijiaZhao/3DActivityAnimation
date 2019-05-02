package com.example.avatarserver;

/**
    Create by Weijia Zhao in 03/01/2019
*/

import android.app.Activity;
import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Enumeration;

import static android.content.ContentValues.TAG;

public class AvatarServerThread extends Thread{

    private int serverPort;

    private ServerSocket server;

    private Socket socket;
    private PrintWriter printWriter;
    private BufferedReader bufferedReader;
    MainActivity activity;


    public AvatarServerThread(int port, MainActivity activity) {
        super();
        this.serverPort = port;
        this.activity = activity;

    }


    public void sendData(String data){
        if(printWriter != null){
            printWriter.println(data);
            printWriter.flush();
        }
    }

    public void close(String message) {

        activity.toastMessage(message);

        if(printWriter != null){
            printWriter.close();
        }

        if(socket != null){
            try {

                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if(server != null) {
            try {
                server.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }


    }


    @Override
    public void run() {

        try {
            server = new ServerSocket(serverPort);
        } catch (IOException e) {
            Log.e(TAG, "Cannot start server!" );
            e.printStackTrace();
        }

        try {
            socket = server.accept();
            activity.toastMessage("Connected!");
            activity.onServerStart();

        } catch (IOException e) {
            Log.e(TAG, "Unable to accept client!" );
            e.printStackTrace();
        }


        try {
            OutputStream outputStream = socket.getOutputStream();
            printWriter = new PrintWriter(outputStream);
            bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

        } catch (IOException e) {
            e.printStackTrace();
        }
        try {

            while((bufferedReader.readLine()) == null) {
                close("Lost Connection!");
//                activity.serverThread = null;
                activity.onClientDown();
                activity.performStartClick();

                break;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }


}


