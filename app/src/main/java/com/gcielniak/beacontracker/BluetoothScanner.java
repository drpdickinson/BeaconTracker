package com.gcielniak.beacontracker;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.os.SystemClock;

import java.util.Arrays;

/**
 * Created by gcielniak on 04/10/2015.
 */
public class BluetoothScanner {
    Pose current_pose;
    BluetoothAdapter adapter;
    BluetoothScanReceiver receiver;
    //add logger, patrick
    ScanLogger logger;
    BluetoothScanReceiver receiver2;

    BluetoothScanner(OnScanListener listener) {
        adapter = BluetoothAdapter.getDefaultAdapter();
        receiver = new BluetoothScanReceiver(listener);
        current_pose = new Pose();

        logger = new ScanLogger();
        receiver2 = new BluetoothScanReceiver(logger);
    }

    public void UpdatePose(Pose current_pose) {
        this.current_pose = current_pose;
    }

    public void Start() {
        logger.Start();
        adapter.startLeScan(receiver);
        adapter.startLeScan(receiver2);
    }

    public void Stop() {
        //logger.Stop(this);
        adapter.stopLeScan(receiver);
        adapter.startLeScan(receiver2);
    }

    private class BluetoothScanReceiver implements BluetoothAdapter.LeScanCallback {

        OnScanListener listener;

        BluetoothScanReceiver(OnScanListener listener) {
            this.listener = listener;
        }

        @Override
        public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {

            Scan scan = new Scan();
            scan.device_type = DeviceType.BT_BEACON;
            scan.mac_address = device.getAddress();
            scan.name = device.getName();
            scan.timestamp = SystemClock.elapsedRealtimeNanos() / 1000;
            scan.value = (double) rssi;
            scan.translation = current_pose.translation;
            scan.rotation = current_pose.rotation;
            scan.uuid = new UUID(Arrays.copyOfRange(scanRecord, 9, 29));

            listener.onScan(scan);
        }
    }
}
