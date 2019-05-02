package com.example.avatarserver;

/**
 Create by Weijia Zhao in 03/12/2019
 */

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

import static android.content.ContentValues.TAG;
import static android.content.Context.SENSOR_SERVICE;

public class AvatarOrientation implements SensorEventListener {
    MainActivity activity;
    private float[] gravity;
    private float[] geomagnetic;
    private float azimuth;

    private SensorManager sensorManager;
    private Sensor accelerometer;
    private Sensor magneticSensor;

    private float currentAzimuth = 0;

    private int stepCount = 0;
    private float[] mGravity;
    private double mAccel;
    private double mAccelCurrent;
    private double mAccelLast;

    private int hitCount = 0;
    private double hitSum = 0;
    private double hitResult = 0;

    private final int SAMPLE_SIZE = 25; // change this sample size as you want, higher is more precise but slow measure.
    private final double THRESHOLD = 0.7; // change this threshold as you want, higher is more spike movement


    public AvatarOrientation(MainActivity activity) {
//        Log.i(TAG, "AvatarOrientation: ");
        this.activity = activity;
        this.gravity = new float[3];
        this.geomagnetic = new float[3];

        sensorManager = (SensorManager) activity.getApplicationContext().getSystemService(SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        magneticSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

        mAccel = 0.00f;
        mAccelCurrent = SensorManager.GRAVITY_EARTH;
        mAccelLast = SensorManager.GRAVITY_EARTH;

    }

    public void start() {
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
        sensorManager.registerListener(this, magneticSensor, SensorManager.SENSOR_DELAY_GAME);
    }

    public void stop() {
        sensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        onOrientationChanged(sensorEvent);
        onPositionChanged(sensorEvent);
    }

    private void onPositionChanged(SensorEvent sensorEvent) {
        if (sensorEvent.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            mGravity = sensorEvent.values.clone();

            double x = mGravity[0];
            double y = mGravity[1];
            double z = mGravity[2];
            mAccelLast = mAccelCurrent;
            mAccelCurrent = Math.sqrt(x * x + y * y + z * z);
            double delta = mAccelCurrent - mAccelLast;
            mAccel = mAccel * 0.9f + delta;

            if (hitCount <= SAMPLE_SIZE) {
                hitCount++;
                hitSum += Math.abs(mAccel);
            } else {
                hitResult = hitSum / SAMPLE_SIZE;

                if (hitResult > THRESHOLD) {
//                    Toast.makeText(getApplicationContext(),"Walking", Toast.LENGTH_SHORT).show();
//                    activity.setStatus("Walking");
                    Log.e(TAG, "onPositionChanged: Movement detected." );
                    activity.sendMovedDistance(20);
                    stepCount++;

                    if(stepCount >= 15) {
                        activity.sendLocation();
                        stepCount = 0;
                    }
//                    activity.scanWifi();
//                    activity.sendLocation();

                }

                hitCount = 0;
                hitSum = 0;
                hitResult = 0;
            }
        }
    }

    private void onOrientationChanged(SensorEvent sensorEvent) {
        if (sensorEvent.sensor.getType() == Sensor.TYPE_ACCELEROMETER)
            gravity = sensorEvent.values;

        if (sensorEvent.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD)
            geomagnetic = sensorEvent.values;

        if (gravity != null && geomagnetic != null) {
            float R[] = new float[9];
            float I[] = new float[9];

            if (SensorManager.getRotationMatrix(R, I, gravity, geomagnetic)) {

                // orientation contains azimuth, pitch and roll
                float orientation[] = new float[3];
                SensorManager.getOrientation(R, orientation);

                // in radians
                azimuth = orientation[0];


                if(Math.abs(currentAzimuth - azimuth) >= 0.15) {

                    currentAzimuth = azimuth;

                    // offset for the Atanasoff Hall direction and the Geographic Coordinate System
                    // was 27
                    float offset = -15;

                    // convert to degree. North - 0; East - 90; South - 180; West - 270
                    // https://developer.android.com/guide/topics/sensors/sensors_position
                    float degree = (float)(Math.toDegrees(currentAzimuth) + 360 - offset) % 360;
//                    Log.e(TAG, "onSensorChanged: degree = " + degree);

                    // convert current coordinate system consistent to the one in SketchUp
                    //  East - 0; North - 90; West - 180; South - 270
                    if (degree >= 0 && degree <= 90) {
                        degree = 90 - degree;
                    }
                    else if (degree > 90 && degree <= 180) {
                        degree = 270 + 180 - degree;
                    }
                    else if (degree > 180 && degree <= 270) {
                        degree = 270 + 180 - degree;

                    }
                    else if (degree > 270 && degree <= 360) {
                        degree = 90 + 360 - degree;
                    }

//                    Log.e(TAG, "onSensorChanged: current degree = " + degree);

                    degree += 90; //
                    activity.sendRotation(degree);
                }

            }
        }
    }


    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }
}
