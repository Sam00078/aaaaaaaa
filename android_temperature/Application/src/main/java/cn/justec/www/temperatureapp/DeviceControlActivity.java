/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cn.justec.www.temperatureapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.media.MediaScannerConnection;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.text.format.Time;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ExpandableListView;
import android.widget.SimpleExpandableListAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.utils.ColorTemplate;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

/**
 * For a given BLE device, this Activity provides the user interface to connect, display data,
 * and display GATT services and characteristics supported by the device.  The Activity
 * communicates with {@code BluetoothLeService}, which in turn interacts with the
 * Bluetooth LE API.
 */
public class DeviceControlActivity extends Activity {
    private final static String TAG = DeviceControlActivity.class.getSimpleName();

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";

    //    private TextView mConnectionState;
//    private TextView mDataField;
    private String mDeviceName;
    private String mDeviceAddress;
    //    private ExpandableListView mGattServicesList;
    private BluetoothLeService mBluetoothLeService;

    private ArrayList<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics =
            new ArrayList<ArrayList<BluetoothGattCharacteristic>>();
    private boolean mConnected = false;
    private BluetoothGattCharacteristic mNotifyCharacteristic;

    private final String LIST_NAME = "NAME";
    private final String LIST_UUID = "UUID";

    private LineChart mChart;

    private static final int WAVE_NUM = 150;
    private static float[] mWaveVal = new float[WAVE_NUM];
    private static int[] mWaveColor = new int[WAVE_NUM];
    private static String[] mWaveTime = new String[WAVE_NUM];
    private static int mWaveValIndex = 0;
    private static int mFrameSerialNumber = 0;
    private static boolean mToggleDataTransfer = false;
    private boolean mGetCharacteristicSuccess = false;

    private static final int AUTO_CONNECT_PERIOD = 10;
    private android.os.Handler handler = new android.os.Handler();

    Runnable runnable = new Runnable() {
        @Override
        public void run() {
            if (!mConnected) {
                mBluetoothLeService.connect(mDeviceAddress);
            }

            handler.postDelayed(this, AUTO_CONNECT_PERIOD);
        }
    };

    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            // Automatically connects to the device upon successful start-up initialization.
            mBluetoothLeService.connect(mDeviceAddress);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };


    // Handles various events fired by the Service.
    // ACTION_GATT_CONNECTED: connected to a GATT server.
    // ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
    // ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
    // ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
    //                        or notification operations.
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                mConnected = true;
                updateConnectionState(R.string.connected);
                invalidateOptionsMenu();

                handler.removeCallbacks(runnable);
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
                mGetCharacteristicSuccess = false;

                TextView mValTemp = (TextView) findViewById(R.id.val_temp);
                mValTemp.setText(getString(R.string.label_default));

                TextView mValOther = (TextView) findViewById(R.id.val_other);
                mValOther.setText("");

                updateConnectionState(R.string.disconnected);
                invalidateOptionsMenu();
                clearUI();

                handler.postDelayed(runnable, AUTO_CONNECT_PERIOD);

            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                // Show all the supported services and characteristics on the user interface.
                displayGattServices(mBluetoothLeService.getSupportedGattServices());
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
//                displayData(intent.getStringExtra(BluetoothLeService.EXTRA_DATA));
            } else if (BluetoothLeService.ACTION_GATT_NOTIFICATION.equals(action)) {
                if (mToggleDataTransfer == false) {
                    mToggleDataTransfer = true;
                    invalidateOptionsMenu();
                }

                dataDecode(intent);
            }
        }
    };

    // If a given GATT characteristic is selected, check for supported features.  This sample
    // demonstrates 'Read' and 'Notify' features.  See
    // http://d.android.com/reference/android/bluetooth/BluetoothGatt.html for the complete
    // list of supported characteristic features.
    private final ExpandableListView.OnChildClickListener servicesListClickListner =
            new ExpandableListView.OnChildClickListener() {
                @Override
                public boolean onChildClick(ExpandableListView parent, View v, int groupPosition,
                                            int childPosition, long id) {
                    if (mGattCharacteristics != null) {
                        final BluetoothGattCharacteristic characteristic =
                                mGattCharacteristics.get(groupPosition).get(childPosition);
                        final int charaProp = characteristic.getProperties();
                        if ((charaProp | BluetoothGattCharacteristic.PROPERTY_READ) > 0) {
                            // If there is an active notification on a characteristic, clear
                            // it first so it doesn't update the data field on the user interface.
                            if (mNotifyCharacteristic != null) {
//                                mBluetoothLeService.setCharacteristicNotification(
//                                        mNotifyCharacteristic, false);
                                mNotifyCharacteristic = null;
                            }
                            mBluetoothLeService.readCharacteristic(characteristic);
                        }
                        if ((charaProp | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                            mNotifyCharacteristic = characteristic;
                            mBluetoothLeService.setCharacteristicNotification(
                                    characteristic, true);
                        }
                        return true;
                    }
                    return false;
                }
            };

    private void clearUI() {
//        mGattServicesList.setAdapter((SimpleExpandableListAdapter) null);
//        mDataField.setText(R.string.no_data);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.gatt_services_characteristics);

        final Intent intent = getIntent();
        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);

        // Sets up UI references.
//        ((TextView) findViewById(R.id.device_address)).setText(mDeviceAddress);
//        mGattServicesList = (ExpandableListView) findViewById(R.id.gatt_services_list);
//        mGattServicesList.setOnChildClickListener(servicesListClickListner);
//        mConnectionState = (TextView) findViewById(R.id.connection_state);
//        mDataField = (TextView) findViewById(R.id.data_value);

        getActionBar().setTitle(mDeviceName);
        getActionBar().setDisplayHomeAsUpEnabled(true);
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

        chartConfig();

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBluetoothLeService != null) {
            final boolean result = mBluetoothLeService.connect(mDeviceAddress);
            Log.d(TAG, "Connect request result=" + result);

            BluetoothGattCharacteristic characteristic =
                    mBluetoothLeService.getSpecialGattCharacteristic(
                            SampleGattAttributes.SIMPLE_PROFILE_SERVICE,
                            SampleGattAttributes.CUSTOM_NOTIFICATION);

            if (characteristic != null) {
                mBluetoothLeService.setCharacteristicNotification(
                        characteristic, true);
            }
        }

        Arrays.fill(mWaveVal, 0);
        Arrays.fill(mWaveTime, "");
        mWaveValIndex = 0;

        mToggleDataTransfer = false;
        invalidateOptionsMenu();
    }

    @Override
    protected void onPause() {
        super.onPause();

        BluetoothGattCharacteristic characteristic =
                mBluetoothLeService.getSpecialGattCharacteristic(
                        SampleGattAttributes.SIMPLE_PROFILE_SERVICE,
                        SampleGattAttributes.CUSTOM_NOTIFICATION);

        if (characteristic != null) {
            mBluetoothLeService.setCharacteristicNotification(
                    characteristic, false);
        }

        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        mBluetoothLeService = null;

        handler.removeCallbacks(runnable);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        if (Build.MANUFACTURER.toUpperCase().equals("SAMSUNG")) {
            getMenuInflater().inflate(R.menu.gatt_services_samsung, menu);
        } else {
            getMenuInflater().inflate(R.menu.gatt_services, menu);
        }

        menu.findItem(R.id.menu_refresh).setEnabled(false);
        menu.findItem(R.id.menu_refresh).setActionView(null);

        if (mConnected) {
            menu.findItem(R.id.menu_connect).setVisible(false);
            menu.findItem(R.id.menu_disconnect).setVisible(true);
        } else {
            menu.findItem(R.id.menu_connect).setVisible(true);
            menu.findItem(R.id.menu_disconnect).setVisible(false);
        }

        if (mConnected && mGetCharacteristicSuccess) {
            if (mToggleDataTransfer == false) {
                menu.findItem(R.id.menu_toggle).setTitle(getString(R.string.menu_getRealtimeData));
            } else {
                menu.findItem(R.id.menu_toggle).setTitle(getString(R.string.menu_stopTransmission));
            }

            menu.findItem(R.id.menu_toggle).setVisible(true);
        } else {
            menu.findItem(R.id.menu_toggle).setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_connect:
                mBluetoothLeService.connect(mDeviceAddress);
                return true;
            case R.id.menu_disconnect:
//                mBluetoothLeService.disconnect();
                return true;
            case android.R.id.home:
                onBackPressed();
                return true;
            case R.id.menu_exit:
                new AlertDialog.Builder(this)
                        .setTitle(this.getString(R.string.menu_exit))
                        .setMessage(this.getString(R.string.exitDlg_Content))
                        .setNegativeButton(android.R.string.no, null)
                        .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface arg0, int arg1) {
                                Intent _intent = new Intent(getApplicationContext(), DeviceScanActivity.class);
                                _intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                                _intent.putExtra("EXIT", true);
                                startActivity(_intent);
                            }
                        }).create().show();
                return true;
            case R.id.menu_about:
                final Dialog dialog = new AboutDialog(this);
                dialog.show();
                return true;
            case R.id.menu_cali:
                final CalibrationDialog calibrationDialog = new CalibrationDialog(this, new CalibrationDialog.DialogListener() {
                    @Override
                    public void refreshActivity(byte[] val) {
                        SendValToBluetooth(val);
                    }
                });

                calibrationDialog.show();
                return true;
            case R.id.menu_toggle:
                if (!mToggleDataTransfer) {
                    item.setTitle(getString(R.string.menu_stopTransmission));

                    cmdGetRealtimeData(0);

                } else {
                    item.setTitle(getString(R.string.menu_getRealtimeData));

//                    BluetoothGattCharacteristic characteristic =
//                            mBluetoothLeService.getSpecialGattCharacteristic(
//                                    SampleGattAttributes.SIMPLE_PROFILE_SERVICE,
//                                    SampleGattAttributes.CUSTOM_NOTIFICATION);
//
//                    mBluetoothLeService.setCharacteristicNotification(
//                            characteristic, false);

                    cmdStopRealtimeData();
                }
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateConnectionState(final int resourceId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
//                mConnectionState.setText(resourceId);
            }
        });
    }

    private void displayData(String data) {
        if (data != null) {
//            mDataField.setText(data);
        }
    }

    private void displayTemp(float temp) {

        BigDecimal bd = new BigDecimal(temp);
        bd = bd.setScale(2, BigDecimal.ROUND_HALF_UP);

        TextView mValTemp = (TextView) findViewById(R.id.val_temp);

//        if (temp > 50 || temp < 20) {
//            mValTemp.setText(this.getString(R.string.label_default));
//        } else {
//            mValTemp.setText(bd.toString() + "\u2103");
//        }

        mValTemp.setText(bd.toString() + "\u2103");

        saveData(this.getString(R.string.saved_file_name), bd.toString() + "\u2103\t");

        Time now = new Time();
        now.setToNow();
        mWaveTime[mWaveValIndex] = now.format("%k:%M:%S");

        mWaveVal[mWaveValIndex++] = temp;

        if (mWaveValIndex >= WAVE_NUM) mWaveValIndex = 0;

        displayWave();
    }

    private TextView mValOther;
    private void displayTouchStatus(float touchStatus) {
        String touchStatusStr;

        touchStatusStr = Float.toString(touchStatus);
        mValOther = (TextView) findViewById(R.id.val_other);

        if (touchStatus > (2.3 * 0x7f / 2.5)) {
            touchStatusStr = touchStatusStr + this.getString(R.string.label_notouch);
            mValOther.setTextColor(Color.RED);
        } else if (touchStatus > (1.8 * 0x7f / 2.5)) {
            touchStatusStr = touchStatusStr + this.getString(R.string.label_looseContact);
            mValOther.setTextColor(Color.BLUE);
        } else {
            touchStatusStr = touchStatusStr + this.getString(R.string.label_goodContact);
            mValOther.setTextColor(Color.BLUE);
        }


        // mValOther.setTextColor(Color.parseColor("#ffff0000"));
        mValOther.setText(touchStatusStr);

        saveData(this.getString(R.string.saved_file_name), touchStatusStr + "\r\n");
    }

    private void saveData(String name, String str) {
        try {
            // Creates a trace file in the primary external storage space of the
            // current application.
            // If the file does not exists, it is created.

            boolean isOK;

            File traceFile = new File(this.getExternalFilesDir(null), name);

            isOK = traceFile.exists() || traceFile.createNewFile();

            if (isOK) {
                // Adds a line to the trace file
                BufferedWriter writer = new BufferedWriter(new FileWriter(traceFile, true /*append*/));
                writer.write(str);
                writer.close();
                // Refresh the data so it can seen when the device is plugged in a
                // computer. You may have to unplug and replug the device to see the
                // latest changes. This is not necessary if the user should not modify
                // the files.
                MediaScannerConnection.scanFile(this,
                        new String[]{traceFile.toString()},
                        null,
                        null);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void displayOther(String bodyRes, String hallSensor) {
        TextView mValOther = (TextView) findViewById(R.id.val_other);
        mValOther.setText(bodyRes);
    }

    private void chartConfig() {
        mChart = (LineChart) findViewById(R.id.chart);
        // if enabled, the chart will always start at zero on the y-axis
        mChart.getAxisLeft().setStartAtZero(false);
        mChart.getAxisRight().setStartAtZero(false);

        // no description text
        mChart.setDescription("");

        // enable value highlighting
        mChart.setHighlightEnabled(true);

        // enable touch gestures
        mChart.setTouchEnabled(false);

        // enable scaling and dragging
        mChart.setDragEnabled(true);
        mChart.setScaleEnabled(true);

        // if disabled, scaling can be done on x- and y-axis separately
        mChart.setPinchZoom(false);

        mChart.setDrawGridBackground(false);

        XAxis x = mChart.getXAxis();
        x.setTextColor(Color.CYAN);
        x.setAdjustXLabels(true);
        x.setSpaceBetweenLabels(10);
        x.setEnabled(true);

        YAxis y = mChart.getAxisLeft();
        y.setTextColor(Color.rgb(50, 145, 239));
        y.setLabelCount(5);
        y.setEnabled(true);

        mChart.getAxisRight().setEnabled(false);

        // add data
        setData(10, 100);

        mChart.getLegend().setEnabled(false);

//        mChart.animateXY(2000, 2000);

        // dont forget to refresh the drawing
        mChart.invalidate();
    }

    private void setData(int count, float range) {
        ArrayList<String> xVals = new ArrayList<String>();
        for (int i = 0; i < count; i++) {
            xVals.add((1990 + i) + "");
        }

        ArrayList<Entry> vals1 = new ArrayList<Entry>();

        for (int i = 0; i < count; i++) {
            float mult = (range + 1);
            float val = (float) (Math.random() * mult) + 20;// + (float)
            // ((mult *
            // 0.1) / 10);
            vals1.add(new Entry(val, i));
        }

        // create a dataset and give it a type
        LineDataSet set1 = new LineDataSet(vals1, "DataSet 1");
        set1.setDrawCubic(true);
        set1.setCubicIntensity(0.2f);
        //set1.setDrawFilled(true);
        set1.setDrawCircles(false);
        set1.setLineWidth(2f);
        set1.setCircleSize(5f);
        set1.setHighLightColor(Color.rgb(244, 117, 117));
        set1.setColor(Color.rgb(50, 145, 239));
//        set1.setColors(ColorTemplate.VORDIPLOM_COLORS);
        set1.setFillColor(ColorTemplate.getHoloBlue());

        // create a data object with the datasets
        LineData data = new LineData(xVals, set1);
//        data.setValueTypeface(tf);
        data.setValueTextSize(9f);
        data.setDrawValues(false);

        // set data
        mChart.setData(data);
    }

    private void displayWave() {
        ArrayList<String> xVals = new ArrayList<String>();

        for (int i = 0; i < mWaveVal.length; i++) {
            xVals.add(mWaveTime[i]);

//            xVals.add((1990 +i) + "");

            if ((i == mWaveValIndex) || (i == (mWaveValIndex - 1)) || (i == (mWaveValIndex + 1))) {
                mWaveColor[i] = android.R.color.background_light;
            } else {
                mWaveColor[i] = Color.rgb(50, 145, 239);
            }
        }

        ArrayList<Entry> vals1 = new ArrayList<>();

        for (int i = 0; i < mWaveVal.length; i++) {
            vals1.add(new Entry(mWaveVal[i], i));
        }

        // create a dataset and give it a type
        LineDataSet set1 = new LineDataSet(vals1, "DataSet 1");
//        set1.setDrawCubic(true);
//        set1.setCubicIntensity(0.2f);
        //set1.setDrawFilled(true);
        set1.setDrawCircles(false);
        set1.setLineWidth(2f);
//        set1.setCircleSize(5f);
//        set1.setHighLightColor(Color.rgb(244, 117, 117));
//        set1.setColor(Color.rgb(50, 145, 239));
//        set1.setFillColor(ColorTemplate.getHoloBlue());
        set1.setColors(mWaveColor);

        // create a data object with the datasets
        LineData data = new LineData(xVals, set1);
//        data.setValueTypeface(tf);
        data.setValueTextSize(9f);
        data.setDrawValues(false);

        // set data
        mChart.setData(data);

        mChart.invalidate();


    }

    private void cmdGetRealtimeData(int sn) {
        byte[] val = {0, 0, 0, 0};

        val[2] = (byte) sn; //frame serial number
        val[3] = (byte) sn; //check sum

        BluetoothGattCharacteristic characteristic =
                mBluetoothLeService.getSpecialGattCharacteristic(
                        SampleGattAttributes.SIMPLE_PROFILE_SERVICE,
                        SampleGattAttributes.CUSTOM_DATA_TRANSFER);

        characteristic.setValue(val);

        mBluetoothLeService.writeCharacteristic(characteristic);

        mToggleDataTransfer = true;
    }

    private void cmdStopRealtimeData() {
        byte[] val = {2, 0, 0, 2};

        BluetoothGattCharacteristic characteristic =
                mBluetoothLeService.getSpecialGattCharacteristic(
                        SampleGattAttributes.SIMPLE_PROFILE_SERVICE,
                        SampleGattAttributes.CUSTOM_DATA_TRANSFER);

        characteristic.setValue(val);

        mBluetoothLeService.writeCharacteristic(characteristic);

        mToggleDataTransfer = false;
    }

    private static float byte2float(byte[] b, int index) {
        int l;
        l = b[index + 0];
        l &= 0xff;
        l |= ((long) b[index + 1] << 8);
        l &= 0xffff;
        l |= ((long) b[index + 2] << 16);
        l &= 0xffffff;
        l |= ((long) b[index + 3] << 24);
        return Float.intBitsToFloat(l);
    }

    private static byte[] float2byte(float f) {

        // 把float转换为byte[]
        int fbit = Float.floatToIntBits(f);

        byte[] b = new byte[4];
        for (int i = 0; i < 4; i++) {
            b[i] = (byte) (fbit >> (24 - i * 8));
        }

        // 翻转数组
        int len = b.length;
        // 建立一个与源数组元素类型相同的数组
        byte[] dest = new byte[len];
        // 为了防止修改源数组，将源数组拷贝一份副本
        System.arraycopy(b, 0, dest, 0, len);
        byte temp;
        // 将顺位第i个与倒数第i个交换
        for (int i = 0; i < len / 2; ++i) {
            temp = dest[i];
            dest[i] = dest[len - i - 1];
            dest[len - i - 1] = temp;
        }

        return dest;

    }

    private void dataDecode(Intent intent) {
        int checksum;

        byte[] src = intent.getByteArrayExtra(BluetoothLeService.EXTRA_DATA);
        int[] val = new int[src.length];

        //convert signed to unsigned
        for (byte i = 0; i < src.length; i++) {
            val[i] = src[i] & 0xff;
        }

        if ((0xfa == val[0]) && (0xf5 == val[1])) {

            checksum = 0;

            for (byte i = 0; i < (val[2] - 2); i++) {
                checksum += val[i];
            }

            if ((val[val[2] - 1] == (checksum & 0xff)) && (val[val[2] - 2] == (checksum >> 8))) {
                if (0x69 == val[3]) {
                    if (0x07 == val[2]) {
                        switch (val[4]) {
                            case 0x50:
                                Boast.makeText(this, this.getString(R.string.hint_paraTempSuccess), Toast.LENGTH_SHORT).show();
                                break;

                            case 0x51:
                                Boast.makeText(this, this.getString(R.string.hint_paraSmallResSuccess), Toast.LENGTH_SHORT).show();
                                break;

                            case 0x52:
                                Boast.makeText(this, this.getString(R.string.hint_paraLargeResSuccess), Toast.LENGTH_SHORT).show();
                                break;

                            case 0x10:
                                Boast.makeText(this, this.getString(R.string.hint_paraTempTooSmall), Toast.LENGTH_SHORT).show();
                                break;

                            case 0x11:
                                Boast.makeText(this, this.getString(R.string.hint_paraResTooSmall), Toast.LENGTH_SHORT).show();
                                break;

                            case 0x12:
                                Boast.makeText(this, this.getString(R.string.hint_paraResTooSmall), Toast.LENGTH_SHORT).show();
                                break;

                            case 0x20:
                                Boast.makeText(this, this.getString(R.string.hint_paraTempTooLarge), Toast.LENGTH_SHORT).show();
                                break;

                            case 0x21:
                                Boast.makeText(this, this.getString(R.string.hint_paraResTooLarge), Toast.LENGTH_SHORT).show();
                                break;

                            case 0x22:
                                Boast.makeText(this, this.getString(R.string.hint_paraResTooLarge), Toast.LENGTH_SHORT).show();
                                break;

                            case 0x03:
                                Boast.makeText(this, this.getString(R.string.hint_paraLargeResNotAvailable), Toast.LENGTH_SHORT).show();
                                break;

                            case 0x04:
                                Boast.makeText(this, this.getString(R.string.hint_paraTempNotAvailable), Toast.LENGTH_SHORT).show();
                                break;

                            default:
                                break;
                        }
                    } else if (0x0E == val[2]) {
                        byte option = (byte) (val[7] & 0x03);
                        float tmp;
                        BigDecimal bd;

                        try {
                            tmp = byte2float(src, 8);
                            bd = new BigDecimal(tmp);
                            bd = bd.setScale(2, BigDecimal.ROUND_HALF_UP);

                            CalibrationDialog.option = option;
                            CalibrationDialog.value = tmp;

                            switch (option) {
                                case 0x00:
                                    Boast.makeText(this, this.getString(R.string.hint_paraAlreadyCali) +
                                            this.getString(R.string.label_paraTemp) +
                                            bd.toString() + " \u2103", Toast.LENGTH_LONG).show();
                                    break;

                                case 0x01:
                                    Boast.makeText(this, this.getString(R.string.hint_paraAlreadyCali) +
                                            this.getString(R.string.label_paraSmallRes) +
                                            bd.toString() + " \u03A9", Toast.LENGTH_LONG).show();
                                    break;

                                case 0x02:
                                    Boast.makeText(this, this.getString(R.string.hint_paraAlreadyCali) +
                                            this.getString(R.string.label_paraLargeRes) +
                                            bd.toString() + " \u03A9", Toast.LENGTH_LONG).show();
                                    break;

                                case 0x03:
                                    break;

                                default:
                                    break;
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                } else if (0x68 == val[3]) {
                    if (0x10 == val[2]) {
                        float temp;
                        float touchStatus;

                        temp = byte2float(src, 4);

                        displayTemp(temp);

                        touchStatus = byte2float(src, 8);

                        displayTouchStatus(touchStatus);

                    }
                }
            }
        }
    }

    private void SendValToBluetooth(byte[] val) {
        BluetoothGattCharacteristic characteristic =
                mBluetoothLeService.getSpecialGattCharacteristic(
                        SampleGattAttributes.SIMPLE_PROFILE_SERVICE,
                        SampleGattAttributes.CUSTOM_DATA_TRANSFER);

        characteristic.setValue(val);

        mBluetoothLeService.writeCharacteristic(characteristic);
    }

    // Demonstrates how to iterate through the supported GATT Services/Characteristics.
    // In this sample, we populate the data structure that is bound to the ExpandableListView
    // on the UI.
    private void displayGattServices(List<BluetoothGattService> gattServices) {
        if (gattServices == null) return;
        String uuid = null;
        String unknownServiceString = getResources().getString(R.string.unknown_service);
        String unknownCharaString = getResources().getString(R.string.unknown_characteristic);
        ArrayList<HashMap<String, String>> gattServiceData = new ArrayList<HashMap<String, String>>();
        ArrayList<ArrayList<HashMap<String, String>>> gattCharacteristicData
                = new ArrayList<ArrayList<HashMap<String, String>>>();
        mGattCharacteristics = new ArrayList<ArrayList<BluetoothGattCharacteristic>>();

        // Loops through available GATT Services.
        for (BluetoothGattService gattService : gattServices) {
            HashMap<String, String> currentServiceData = new HashMap<String, String>();
            uuid = gattService.getUuid().toString();
            currentServiceData.put(
                    LIST_NAME, SampleGattAttributes.lookup(uuid, unknownServiceString));
            currentServiceData.put(LIST_UUID, uuid);
            gattServiceData.add(currentServiceData);

            ArrayList<HashMap<String, String>> gattCharacteristicGroupData =
                    new ArrayList<HashMap<String, String>>();
            List<BluetoothGattCharacteristic> gattCharacteristics =
                    gattService.getCharacteristics();
            ArrayList<BluetoothGattCharacteristic> charas =
                    new ArrayList<BluetoothGattCharacteristic>();

            // Loops through available Characteristics.
            for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                charas.add(gattCharacteristic);
                HashMap<String, String> currentCharaData = new HashMap<String, String>();
                uuid = gattCharacteristic.getUuid().toString();
                currentCharaData.put(
                        LIST_NAME, SampleGattAttributes.lookup(uuid, unknownCharaString));
                currentCharaData.put(LIST_UUID, uuid);
                gattCharacteristicGroupData.add(currentCharaData);

                if (gattCharacteristic.getUuid().toString().equals(SampleGattAttributes.CUSTOM_NOTIFICATION)) {
                    mBluetoothLeService.setCharacteristicNotification(
                            gattCharacteristic, true);
                }
            }
            mGattCharacteristics.add(charas);
            gattCharacteristicData.add(gattCharacteristicGroupData);
        }

        SimpleExpandableListAdapter gattServiceAdapter = new SimpleExpandableListAdapter(
                this,
                gattServiceData,
                android.R.layout.simple_expandable_list_item_2,
                new String[]{LIST_NAME, LIST_UUID},
                new int[]{android.R.id.text1, android.R.id.text2},
                gattCharacteristicData,
                android.R.layout.simple_expandable_list_item_2,
                new String[]{LIST_NAME, LIST_UUID},
                new int[]{android.R.id.text1, android.R.id.text2}
        );
//        mGattServicesList.setAdapter(gattServiceAdapter);


//        BluetoothGattCharacteristic characteristic =
//                mBluetoothLeService.getSpecialGattCharacteristic(
//                        SampleGattAttributes.SIMPLE_PROFILE_SERVICE,
//                        SampleGattAttributes.CUSTOM_NOTIFICATION);
//
//        mBluetoothLeService.setCharacteristicNotification(
//                characteristic, true);

        mGetCharacteristicSuccess = true;

        invalidateOptionsMenu();
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_NOTIFICATION);
        return intentFilter;
    }


}
