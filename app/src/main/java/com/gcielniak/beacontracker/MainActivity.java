package com.gcielniak.beacontracker;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.widget.TextView;

import java.util.List;

public class MainActivity extends AppCompatActivity implements OnScanListener, OnScanListListener, SensorEventListener {

    String TAG = "MainActivity";
    BluetoothScanner bluetooth_scanner;
    NNTracker bluetooth_tracker;
    List<Scan> current_scan;
    Scan current_estimate;
    MapView map_view;

    //Patrick: stuff for magnetometer & accelerometer
    private SensorManager mSensorManager;
    String magString = "0000";
    private Sensor mAccelerometer;
    private Sensor mMagnetometer;
    private float[] mLastAccelerometer = new float[3];
    private float[] mLastMagnetometer = new float[3];
    private boolean mLastAccelerometerSet = false;
    private boolean mLastMagnetometerSet = false;
    private float[] mR = new float[9];
    private float[] mOrientation = new float[3];
    private float mCurrentDegree = 0f;
    //North vector
    private float mAzInRadians = 0.0f;
    private float mAzOffset = 1.04f; //pi / 3
    private float[] mDirnRelToMap = new float[2]; //direction vector in map world

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        map_view = new MapView(this);
        setContentView(map_view);

        bluetooth_tracker = new NNTracker(this, this);
        bluetooth_scanner = new BluetoothScanner(bluetooth_tracker);
        bluetooth_tracker.alpha = 0.5f;

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mMagnetometer = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        //senSensorManager.registerListener(this, senMagnet , SensorManager.SENSOR_DELAY_GAME);
    }

    @Override
    public void onResume() {
        super.onResume();
        map_view.onResume();
        bluetooth_scanner.Start();
        mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_GAME);
        mSensorManager.registerListener(this, mMagnetometer, SensorManager.SENSOR_DELAY_GAME);
        //senSensorManager.registerListener(this, senMagnet, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    public void onPause() {
        super.onPause();
        bluetooth_scanner.Stop();
        mSensorManager.unregisterListener(this, mAccelerometer);
        mSensorManager.unregisterListener(this, mMagnetometer);
    }

    @Override
    public void onScan(Scan scan) {
        current_estimate = scan;
        MainActivity.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                map_view.invalidate();
            }
        });
    }

    @Override
    public void onScanList(List<Scan> scan_list) {
        current_scan = scan_list;
        MainActivity.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                map_view.invalidate();
            }
        });
    }

    public void onSensorChanged(SensorEvent event) {
        Sensor mySensor = event.sensor;

        if (event.sensor == mAccelerometer)
        {
            System.arraycopy(event.values, 0, mLastAccelerometer, 0, event.values.length);
            mLastAccelerometerSet = true;
        }
        else if (event.sensor == mMagnetometer)
        {
            System.arraycopy(event.values, 0, mLastMagnetometer, 0, event.values.length);
            mLastMagnetometerSet = true;
        }

        if (mLastAccelerometerSet && mLastMagnetometerSet)
        {
            SensorManager.getRotationMatrix(mR, null, mLastAccelerometer, mLastMagnetometer);
            SensorManager.getOrientation(mR, mOrientation);
            float azimuthInRadians = mOrientation[0];
            float azimuthInDegress = (float)(Math.toDegrees(azimuthInRadians)+360)%360;
            //RotateAnimation ra = new RotateAnimation(
            //        mCurrentDegree,
            //        -azimuthInDegress,
            //        Animation.RELATIVE_TO_SELF, 0.5f,
            //        Animation.RELATIVE_TO_SELF,
            //        0.5f);

            //ra.setDuration(250);
            //ra.setFillAfter(true);

            mCurrentDegree = -azimuthInDegress;
            magString = "Azimuth: " + azimuthInDegress;
            mAzInRadians = azimuthInRadians;
            MainActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    map_view.invalidate();
                }
            });
        }
        /*
        if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            // calculate th rotation matrix
            SensorManager.getRotationMatrixFromVector(rMat, event.values);
            // get the azimuth value (orientation[0]) in degree
            int mAzimuth = (int) (Math.toDegrees(SensorManager.getOrientation(rMat, orientation)[0]) + 360) % 360;
            TextView tv = (TextView) findViewById(R.id.textView100);
            tv.setText("Azimuth: " + mAzimuth);

            RotateAnimation ra = new RotateAnimation(
                    mCurrentDegree,
                    -mAzimuth,
                    Animation.RELATIVE_TO_SELF, 0.5f,
                    Animation.RELATIVE_TO_SELF,
                    0.5f);
            ra.setDuration(250);
            ra.setFillAfter(true);
            mPointer.startAnimation(ra);
            mCurrentDegree = -mAzimuth;
        }
        */
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    class MapView extends View {
        int width, height;
        float x_min, x_max, y_min, y_max;
        float b_width, b_height, ratio;
        Paint paint;

        public MapView(Context context) {

            super(context);
            paint = new Paint();
        }

        public void onResume() {
            x_min = Float.POSITIVE_INFINITY;
            x_max = Float.NEGATIVE_INFINITY;
            y_min = Float.POSITIVE_INFINITY;
            y_max = Float.NEGATIVE_INFINITY;

            List<Beacon> beacons = bluetooth_tracker.beacons;

            for (Beacon b : beacons) {
                if (b.x < x_min)
                    x_min = (float)b.x;
                else if (b.x > x_max)
                    x_max = (float)b.x;
                if (b.y < y_min)
                    y_min = (float)b.y;
                else if (b.y > y_max)
                    y_max = (float)b.y;
            }

            b_width = x_max - x_min;
            b_height = y_max - y_min;

            double padd = b_width*0.05;
            if (b_height*0.05 > padd)
                padd = b_height*0.05;

            x_min -= padd;
            x_max += padd;
            y_min -= padd;
            y_max += padd;

            b_width = x_max - x_min;
            b_height = y_max - y_min;
            ratio = b_width;
            if (b_height > ratio) {
                ratio = b_height;
            }
        }

        float getX(double x) {
            return (float)(width*(x-x_min-b_width/2)/ratio + width/2);
        }

        float getY(double y) { return (float)(height - (height*(y-y_min)/ratio)); }

        public void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            width = getWidth();
            height = getHeight();

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.WHITE);
            canvas.drawPaint(paint);
            paint.setColor(Color.argb(255, 128, 128, 128));

            float radius = 5;

            List<Beacon> beacons = bluetooth_tracker.beacons;

            for (Beacon b : beacons) {
                canvas.drawCircle(getX(b.x), getY(b.y), radius, paint);
            }

            if (current_scan != null) {
                for (Scan s : current_scan) {
                    for (Beacon b : beacons) {
                        if ((b.mac_address != null) && b.mac_address.equals(s.mac_address)) {
                            double strength = Math.min(Math.abs(-120-s.value)/80, 1.0);
                            strength *= strength;
                            radius = (float)(1-strength)*50;
                            int alpha = (int)(strength*255*0.5);
                            paint.setColor(Color.argb(alpha, 255, 0, 0));
                            canvas.drawCircle(getX(b.x), getY(b.y), radius, paint);
                        }
                    }
                }
            }
            if (current_estimate != null) {
                radius = 20;
                paint.setColor(Color.argb(180, 0, 255, 0));
                canvas.drawCircle(getX(current_estimate.translation[0]), getY(current_estimate.translation[1]), radius, paint);
            }

            //Draw Compass - Patrick
            //1. Central Circle
            paint.setColor(Color.argb(255, 0, 255, 255));
            float compassX = getX(x_max) - 60;
            float compassY = getY(0.0) - 20;
            float circleRad = 40.0f;
            canvas.drawCircle(compassX, compassY, circleRad, paint);
            //2. Draw North
            //y = cos(Az), x = -Sin(Az)
            paint.setColor(Color.argb(32, 0, 0, 0));
            float compassXN = compassX - ((float)Math.sin(mAzInRadians))*circleRad*0.9f;
            float compassYN = compassY - ((float)Math.cos(mAzInRadians))*circleRad*0.9f;
            paint.setStrokeWidth(5);
            canvas.drawLine(compassX, compassY, compassXN, compassYN, paint);
            //3. Draw Orientation relative to offset
            paint.setColor(Color.argb(255, 255, 0, 0));
            mDirnRelToMap[0] = (float)Math.sin(mAzOffset - mAzInRadians);
            mDirnRelToMap[1] = (float)Math.cos(mAzOffset - mAzInRadians);
            float compassXOS = compassX - (mDirnRelToMap[0])*circleRad;
            float compassYOS = compassY - (mDirnRelToMap[1])*circleRad;
            paint.setStrokeWidth(5);
            canvas.drawLine(compassX,compassY,compassXOS,compassYOS,paint);

            //draw some text
            //paint.setTypeface(Typeface.SERIF);
            //paint.setColor(Color.BLACK);
            //paint.setTextSize(24);
            //canvas.drawText(magString,500f,40f,paint);
        }
    }
}
