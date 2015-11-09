package com.gcielniak.beacontracker;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Environment;
import android.os.SystemClock;
import android.os.Vibrator;
import android.support.v4.view.GestureDetectorCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.widget.TextView;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

public class MainActivity extends AppCompatActivity implements OnScanListener, OnScanListListener, SensorEventListener, GestureDetector.OnGestureListener {

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
    private Sensor mLinAccelerometer;
    private Sensor mGravityAccelerometer;
    private Sensor mStepSensor1;
    private Sensor mStepSensor2;
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
    private float mCompassX;
    private float mCompassY;
    private float mCompassRad;

    //Patrick: UI stuff
    private GestureDetectorCompat mDetector;

    //Patrick: for saving relative orientation
    public static final String mPrefFileName = "PrefsBeacon";
    public static final String mPrefKey0 = "PrefsOrient0";

    //for step detection
    private float[] mGravity = new float[3];
    float mGravityMagnitude = 0f;
    private float[] mGravityDirn = new float[3];
    private float[] mLinAccn = new float[3];
    private float mLinAcParaG;
    private float[] mLinAcPerpG = new float[3];
    float alpha = 0.9f;
    boolean aboveThreshold = false;
    int belowThresholdCount = 0;
    double thresholdVal = 2.5;
    double gapVal = 12;
    int nSteps = 0;

    //Patrick: logging accelerometer data
    private boolean bExternalStore = false;
    private boolean bWriteAccelData = true;
    File file;
    FileWriter filewr;
    File file2;
    FileWriter filewr2;
    File file3;
    FileWriter filewr3;

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
        mLinAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        mGravityAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
        mStepSensor1 = mSensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);
        mStepSensor2 = mSensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);

        mCompassX = map_view.getX(map_view.x_max) - 60;
        mCompassY = map_view.getY(0.0) - 20;
        mCompassRad = 40.0f;
        //load the last saved orientation offset
        SharedPreferences settings = getSharedPreferences(mPrefFileName, 0);
        mAzOffset = settings.getFloat(mPrefKey0, 0.0f);

        //Patrick: set up external files for logging accelerometer data
        String state = Environment.getExternalStorageState();
        if ((Environment.MEDIA_MOUNTED.equals(state))&&(bWriteAccelData)) {
            bExternalStore = true;
            Log.e("pdlog", "Setting up file");
            file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "BT-stepcounter.txt");
            if(file.exists()) file.delete();
            try {file.createNewFile();}
            catch (IOException ioe)
            {
                    Log.e("pdlog", "Error creating stepcounter.txt");
            }
            try {filewr = new FileWriter(file,true);} catch (IOException ioe) {Log.e("pdlog", "Error creating filewriter");}
            try{
                filewr.write("********************************************\n");
                filewr.flush();
            } catch (IOException ioe) {Log.e("pdlog", "filewriter write error");}
            //*****************Parallel to gravity
            file2 = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "BT-parallel-to-gravity.txt");
            if(file2.exists()) file2.delete();
            try {file2.createNewFile();}
            catch (IOException ioe) {Log.e("pdlog", "Error creating parallel-to-gravity.txt");}
            try {filewr2 = new FileWriter(file2,true);} catch (IOException ioe) {Log.e("pdlog", "Error creating BT-parallel-to-gravity.txt");}
            try{
                filewr2.write("#############################################\n");
                filewr2.flush();
            } catch (IOException ioe) {Log.e("pdlog", "filewriter write error");}
            //*****************Perpendicular to gravity
            file3 = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "BT-perpendicular-to-gravity.txt");
            if(file3.exists()) file3.delete();
            try {file3.createNewFile();}
            catch (IOException ioe) {Log.e("pdlog", "Error creating perpendicular-to-gravity.txt");}
            try {filewr3 = new FileWriter(file3,true);} catch (IOException ioe) {Log.e("pdlog", "Error creating BT-perpendicular-to-gravity.txt");}
            try{
                filewr3.write("#############################################\n");
                filewr3.flush();
            } catch (IOException ioe) {Log.e("pdlog", "filewriter write error");}
            //**************************************************
        } else {
            bExternalStore = false;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        map_view.onResume();
        bluetooth_scanner.Start();
        mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_GAME);
        mSensorManager.registerListener(this, mMagnetometer, SensorManager.SENSOR_DELAY_GAME);
        mSensorManager.registerListener(this, mLinAccelerometer , SensorManager.SENSOR_DELAY_GAME);
        mSensorManager.registerListener(this, mGravityAccelerometer , SensorManager.SENSOR_DELAY_GAME);
        mSensorManager.registerListener(this, mStepSensor1 , SensorManager.SENSOR_DELAY_GAME);
        mSensorManager.registerListener(this, mStepSensor2 , SensorManager.SENSOR_DELAY_GAME);
        mDetector = new GestureDetectorCompat(this,this);
        //initialise gravity
        mGravity[0] = 0.0f;
        mGravity[1] = 0.0f;
        mGravity[2] = 9.8f;
    }

    @Override
    public void onPause() {
        super.onPause();
        bluetooth_scanner.Stop();
        mSensorManager.unregisterListener(this, mAccelerometer);
        mSensorManager.unregisterListener(this, mMagnetometer);
        mSensorManager.unregisterListener(this, mGravityAccelerometer);
        mSensorManager.unregisterListener(this, mLinAccelerometer);
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

            /*------------------------------------------------------------------------
               Holding phone flat gives                 0,0,9.8  (upside down, -9.8)
               Holding phone directly up                0,9.8,0
               Holding phone directly up and on side    9.8,0,0
            --------------------------------------------------------------------------*/
            float oneMinusAlpha = 1.0f - alpha;
            mGravity[0] = ((mGravity[0] * alpha) + (mLastAccelerometer[0] * oneMinusAlpha));
            mGravity[1] = ((mGravity[1] * alpha) + (mLastAccelerometer[1] * oneMinusAlpha));
            mGravity[2] = ((mGravity[2] * alpha) + (mLastAccelerometer[2] * oneMinusAlpha));
            mGravityMagnitude = (float)Math.sqrt((double)((mGravity[0]*mGravity[0])+(mGravity[1]*mGravity[1])+(mGravity[2]*mGravity[2])));
            mGravityDirn[0] = mGravity[0]/mGravityMagnitude;
            mGravityDirn[1] = mGravity[1]/mGravityMagnitude;
            mGravityDirn[2] = mGravity[2]/mGravityMagnitude;
            //find Linear Acceleration (Gravity removed)
            mLinAccn[0] = mLastAccelerometer[0] - mGravity[0];
            mLinAccn[1] = mLastAccelerometer[1] - mGravity[1];
            mLinAccn[2] = mLastAccelerometer[2] - mGravity[2];
            //resolve linear acceleration parallel and perpendicular to gravity
            mLinAcParaG = (mLinAccn[0]*mGravityDirn[0])+(mLinAccn[1]*mGravityDirn[1])+(mLinAccn[2]*mGravityDirn[2]);
            mLinAcPerpG[0] = mLinAccn[0] - mGravityDirn[0]*mLinAcParaG;
            mLinAcPerpG[1] = mLinAccn[1] - mGravityDirn[1]*mLinAcParaG;
            mLinAcPerpG[2] = mLinAccn[2] - mGravityDirn[2]*mLinAcParaG;

            //Patrick: log accelerometer data for debugging
            if ((bExternalStore)&&(bWriteAccelData)) {
                double mag = Math.sqrt((mLastAccelerometer[0] * mLastAccelerometer[0]) + (mLastAccelerometer[1] * mLastAccelerometer[1]) + (mLastAccelerometer[2] * mLastAccelerometer[2]));
                try {
                    String s = Double.toString(mag);
                    String ss = "";
                    if (s.length() > 7) ss = s.substring(0, 6);
                    else ss = s;
                    filewr.write(ss + "\n");
                    filewr.flush();
                } catch (IOException ioe) {
                    Log.e("pdlog", "filewriter write error");
                }

                double mag1 = mLinAcParaG;
                double x2 = mLinAcPerpG[0];
                double y2 = mLinAcPerpG[1];
                double z2 = mLinAcPerpG[2];
                double mag2 = Math.sqrt((x2 * x2) + (y2 * y2) + (z2 * z2));
                try {
                    String s = Double.toString(mag1);
                    String ss = "";
                    if (s.length() > 7) ss = s.substring(0, 6);
                    else ss = s;
                    filewr2.write(ss + "\n");
                    filewr2.flush();
                    s = Double.toString(mag2);
                    ss = "";
                    if (s.length() > 7) ss = s.substring(0, 6);
                    else ss = s;
                    filewr3.write(ss + "\n");
                    filewr3.flush();

                } catch (IOException ioe) {
                    Log.e("pdlog", "filewriter write error");
                }
            }

        }
        else if (event.sensor == mMagnetometer)
        {
            System.arraycopy(event.values, 0, mLastMagnetometer, 0, event.values.length);
            mLastMagnetometerSet = true;
        }
        else if (mySensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION) {
            Log.d("P:", "LINEAR ACCN");

        }
        else if (mySensor.getType() == Sensor.TYPE_GRAVITY ){
            Log.d("P:", "GRAVITY");
        }
        else if (mySensor.getType() == Sensor.TYPE_STEP_DETECTOR) {
            Log.d("P:", "STEP 1");

        }
        else if (mySensor.getType() == Sensor.TYPE_STEP_COUNTER) {
            Log.d("P:", "STEP 2");

        }

        if (mLastAccelerometerSet && mLastMagnetometerSet)
        {
            SensorManager.getRotationMatrix(mR, null, mLastAccelerometer, mLastMagnetometer);
            SensorManager.getOrientation(mR, mOrientation);
            float azimuthInRadians = mOrientation[0];
            float azimuthInDegress = (float)(Math.toDegrees(azimuthInRadians)+360)%360;
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

    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    // ////////////////////////////////////////////////
    //Patrick : Added UI functions
    // ////////////////////////////////////////////////

    @Override
    public boolean onTouchEvent(MotionEvent event){
        this.mDetector.onTouchEvent(event);
        // Be sure to call the superclass implementation
        return super.onTouchEvent(event);
    }

    @Override
    public boolean onDown(MotionEvent event) {
        return true;
    }

    @Override
    public boolean onFling(MotionEvent event1, MotionEvent event2,
                           float velocityX, float velocityY) {
        return true;
    }

    @Override
    public void onLongPress(MotionEvent event) {
        //if within compass then reset orientation offset
        float hack = 200.0f;
        float x = (event.getX() - mCompassX)*(event.getX() - mCompassX);
        float y = (event.getY() - mCompassY - hack)*(event.getY() - mCompassY - hack);
        float r = (float)Math.sqrt(x+y);
        Log.d("P:", "onDoubleTapEvent: " + event.toString());
        Log.d("P:", "onDoubleTapEvent: " + x + " " + y + " " + r);

        if(r<=mCompassRad) {
            Vibrator v = (Vibrator) this.getSystemService(Context.VIBRATOR_SERVICE);
            // Vibrate for 500 milliseconds
            v.vibrate(500);
            mAzOffset = mAzInRadians;
            //save the orientation offset
            SharedPreferences settings = getSharedPreferences(mPrefFileName, 0);
            SharedPreferences.Editor editor = settings.edit();
            editor.putFloat(mPrefKey0, mAzOffset);
            editor.commit();
        }
    }

    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX,
                            float distanceY) {
        return true;
    }

    @Override
    public void onShowPress(MotionEvent event) {
    }

    @Override
    public boolean onSingleTapUp(MotionEvent event) {
        return true;
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
            mCompassX = getX(x_max) - 60;
            mCompassY = getY(0.0) - 20;
            mCompassRad = 40.0f;
            canvas.drawCircle(mCompassX, mCompassY, mCompassRad, paint);
            //2. Draw North
            //y = cos(Az), x = -Sin(Az)
            paint.setColor(Color.argb(32, 0, 0, 0));
            float compassXN = mCompassX - ((float)Math.sin(mAzInRadians))*mCompassRad*0.9f;
            float compassYN = mCompassY - ((float)Math.cos(mAzInRadians))*mCompassRad*0.9f;
            paint.setStrokeWidth(5);
            canvas.drawLine(mCompassX, mCompassY, compassXN, compassYN, paint);
            //3. Draw Orientation relative to offset
            paint.setColor(Color.argb(255, 255, 0, 0));
            mDirnRelToMap[0] = (float)Math.sin(mAzOffset - mAzInRadians);
            mDirnRelToMap[1] = (float)Math.cos(mAzOffset - mAzInRadians);
            float compassXOS = mCompassX - (mDirnRelToMap[0])*mCompassRad;
            float compassYOS = mCompassY - (mDirnRelToMap[1])*mCompassRad;
            paint.setStrokeWidth(5);
            canvas.drawLine(mCompassX,mCompassY,compassXOS,compassYOS,paint);
            //draw some text : raw acceleratometer values
            paint.setTypeface(Typeface.SERIF);
            paint.setColor(Color.BLACK);
            paint.setTextSize(24);
            canvas.drawText(Float.toString(mLastAccelerometer[0]), 400f, 940f, paint);
            canvas.drawText(Float.toString(mLastAccelerometer[1]), 400f, 965f, paint);
            canvas.drawText(Float.toString(mLastAccelerometer[2]), 400f, 990f, paint);
        }
    }


}
