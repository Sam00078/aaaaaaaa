package cn.justec.www.temperatureapp;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.format.Time;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

/**
 * Created by LEO LIN on 2015/10/15.
 * Calibration Dialog
 */
public class CalibrationDialog extends Dialog {

    private Context mContext;
    private static CalibrationDialog mDialog;
    private static SmallResCaliListener mSmallResCaliListener;
    private static LargeResCaliListener mLargeResCaliListener;
    private static TempCaliListener mTempCaliListener;
    private static DialogListener mDialogListener;

    private Handler handler;
    private Thread thread;

    public static byte option;
    public static float value;

    public CalibrationDialog(Context context, DialogListener dialogListener) {
        super(context);
        mContext = context;
        mDialog = this;

        mSmallResCaliListener = new SmallResCaliListener();
        mLargeResCaliListener = new LargeResCaliListener();
        mTempCaliListener = new TempCaliListener();

        mDialogListener = dialogListener;
    }

    public interface DialogListener {
        void refreshActivity(byte[] val);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_calibration);

        final Button smallResCaliButton = (Button) findViewById(R.id.button_paraSmallResCali);
        final Button largeResCaliButton = (Button) findViewById(R.id.button_paraLargeResCali);
        final Button tempCaliButton = (Button) findViewById(R.id.button_paraTempCali);

        smallResCaliButton.setOnClickListener(mSmallResCaliListener);
        largeResCaliButton.setOnClickListener(mLargeResCaliListener);
        tempCaliButton.setOnClickListener(mTempCaliListener);

        handler = new Handler(new Handler.Callback() {
            @Override
            public boolean handleMessage(Message msg) {

                byte[] val;

                EditText mEdtSmallRes = (EditText) findViewById(R.id.SmallResVal);
                String strSmallRes = mEdtSmallRes.getText().toString();
                float flSmallRes;

                EditText mEdtLargeRes = (EditText) findViewById(R.id.LargeResVal);
                String strLargeRes = mEdtLargeRes.getText().toString();
                float flLargeRes;

                EditText mEdtTemp = (EditText) findViewById(R.id.TempVal);
                String strTemp = mEdtTemp.getText().toString();
                float flTemp;

                switch (msg.what) {
                    case 0x00:
                        Boast.makeText(mContext, mContext.getString(R.string.hint_paraTempTimeout), Toast.LENGTH_SHORT).show();
                        return true;

                    case 0x01:
                        Boast.makeText(mContext, mContext.getString(R.string.hint_paraSmallResTimeout), Toast.LENGTH_SHORT).show();
                        return true;

                    case 0x02:
                        Boast.makeText(mContext, mContext.getString(R.string.hint_paraLargeResTimeout), Toast.LENGTH_SHORT).show();
                        return true;

                    case 0x10:
                        val = new byte[9];

                        if (!strTemp.equals("")) {
                            flTemp = Float.valueOf(strTemp);

                            if ((25 <= flTemp) && (42 >= flTemp)) {
                                val[0] = (byte) 0x69;

                                TimeOption2Bytes((byte) 0, val, (byte) 1);

                                Float2Byte(flTemp, val, 5);

                                val = PackData(val);

                                mDialogListener.refreshActivity(val);
                            } else if (25 > flTemp) {
                                Boast.makeText(mContext, mContext.getString(R.string.hint_paraTempTooSmall), Toast.LENGTH_SHORT).show();
                            } else {
                                Boast.makeText(mContext, mContext.getString(R.string.hint_paraTempTooLarge), Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Boast.makeText(mContext, mContext.getString(R.string.hint_paraEmpty), Toast.LENGTH_SHORT).show();
                        }

                        return true;

                    case 0x11:
                        val = new byte[9];

                        if (!strSmallRes.equals("")) {
                            flSmallRes = Float.valueOf(strSmallRes);

                            if ((1000 < flSmallRes) && (2500 > flSmallRes)) {
                                val[0] = (byte) 0x69;

                                TimeOption2Bytes((byte) 1, val, (byte) 1);

                                Float2Byte(flSmallRes, val, 5);

                                val = PackData(val);

                                mDialogListener.refreshActivity(val);

                            } else if (1000 > flSmallRes) {
                                Boast.makeText(mContext, mContext.getString(R.string.hint_paraResTooSmall), Toast.LENGTH_SHORT).show();
                            } else {
                                Boast.makeText(mContext, mContext.getString(R.string.hint_paraResTooLarge), Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Boast.makeText(mContext, mContext.getString(R.string.hint_paraEmpty), Toast.LENGTH_SHORT).show();
                        }

                        return true;
                    case 0x12:
                        val = new byte[9];

                        if (!strLargeRes.equals("")) {
                            flLargeRes = Float.valueOf(strLargeRes);

                            if ((1000 < flLargeRes) && (2500 > flLargeRes)) {
                                val[0] = (byte) 0x69;

                                TimeOption2Bytes((byte) 2, val, (byte) 1);

                                Float2Byte(flLargeRes, val, 5);

                                val = PackData(val);

                                mDialogListener.refreshActivity(val);

                            } else if (1000 > flLargeRes) {
                                Boast.makeText(mContext, mContext.getString(R.string.hint_paraResTooSmall), Toast.LENGTH_SHORT).show();
                            } else {
                                Boast.makeText(mContext, mContext.getString(R.string.hint_paraResTooLarge), Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Boast.makeText(mContext, mContext.getString(R.string.hint_paraEmpty), Toast.LENGTH_SHORT).show();
                        }

                        return true;

                    case 0x20:
                        tempCaliButton.setText(R.string.button_Recali);
                        return true;

                    case 0x21:
                        smallResCaliButton.setText(R.string.button_Recali);
                        return true;

                    case 0x22:
                        largeResCaliButton.setText(R.string.button_Recali);
                        return true;

                    case 0x30:
                        Boast.makeText(mContext, mContext.getString(R.string.hint_paraTempNotAvailable), Toast.LENGTH_SHORT).show();
                        return true;

                    case 0x32:
                        Boast.makeText(mContext, mContext.getString(R.string.hint_paraLargeResNotAvailable), Toast.LENGTH_SHORT).show();
                        return true;
                }

                return false;
            }
        });

        thread = null;
    }

    /**
     * @param option 选锟斤拷值
     * @param val    锟斤拷锟介缓锟斤拷
     * @param index  时锟斤拷选锟斤拷锟斤拷息锟斤拷锟斤拷锟斤拷锟叫碉拷位锟斤拷
     */

    private void TimeOption2Bytes(byte option, byte[] val, byte index) {
        int timeOption = 0;

        Time now = new Time();
        now.setToNow();

        /* year: 28-31 */
        timeOption |= (2015 > now.year) ? 0 : ((now.year - 2015) << 28);

        /* month: 24-27 */
        timeOption |= (now.month + 1) << 24;

        /* monthDay: 19-23 */
        timeOption |= now.monthDay << 19;

        /* hour: 14-18 */
        timeOption |= now.hour << 14;

        /* min: 8-13 */
        timeOption |= now.minute << 8;

        /* second: 2-7 */
        timeOption |= now.second << 2;

        /* option: 0-1 */
        timeOption |= (3 > option) ? option : 0;

        for (byte i = 0; i < 4; i++) {
            val[i + index] = (byte) (timeOption >> (24 - (i << 3)));
        }
    }

    private byte[] PackData(byte[] buf) {
        byte[] packbuf;

        packbuf = new byte[buf.length + 5];

        packbuf[0] = (byte) 0xfa;
        packbuf[1] = (byte) 0xf5;
        packbuf[2] = (byte) (packbuf.length & 0xff);

        System.arraycopy(buf, 0, packbuf, 3, buf.length);

        Factory factory = new Factory(packbuf, packbuf.length - 2, 0);

        Checksum checksum = factory.CreateChecksum(Factory.CHECKSUM_JUSTEC);

        checksum.GetResult(packbuf, 2, packbuf.length - 2);

        return packbuf;
    }

    private void Float2Byte(float f, byte[] buf, int index) {
        int fBits = Float.floatToIntBits(f);

        for (int i = 0; i < 4; i++) {
            buf[index + i] = (byte) (fBits >> (i << 3));
        }
    }

    private class SmallResCaliListener implements android.view.View.OnClickListener {
        @Override
        public void onClick(View v) {
            EditText mEdtSmallRes = (EditText) findViewById(R.id.SmallResVal);
            String strSmallRes = mEdtSmallRes.getText().toString();
            float flSmallRes;
            byte[] val;

            Button smallResCaliButton = (Button) findViewById(R.id.button_paraSmallResCali);

            if (smallResCaliButton.getText().equals(mContext.getString(R.string.button_Recali))) {
                smallResCaliButton.setText(mContext.getString(R.string.button_Cali));

                val = new byte[9];

                if (!strSmallRes.equals("")) {
                    flSmallRes = Float.valueOf(strSmallRes);

                    if ((1000 < flSmallRes) && (2500 > flSmallRes)) {
                        val[0] = (byte) 0x69;

                        TimeOption2Bytes((byte) 1, val, (byte) 1);

                        Float2Byte(flSmallRes, val, 5);

                        val = PackData(val);

                        mDialogListener.refreshActivity(val);

                    } else if (1000 > flSmallRes) {
                        Boast.makeText(mContext, mContext.getString(R.string.hint_paraResTooSmall), Toast.LENGTH_SHORT).show();
                    } else {
                        Boast.makeText(mContext, mContext.getString(R.string.hint_paraResTooLarge), Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Boast.makeText(mContext, mContext.getString(R.string.hint_paraEmpty), Toast.LENGTH_SHORT).show();
                }
            } else {
                val = new byte[2];

                //Read para
                val[0] = (byte) 0x69;
                val[1] = (byte) 0x11;

                val = PackData(val);

                mDialogListener.refreshActivity(val);

                if (null == thread) {
                    thread = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                byte timeout = 10;
                                Message message = new Message();

                                option = (byte) 0xff;

                                while ((0 != timeout) && ((byte) 0xff == option)) {
                                    timeout--;
                                    Thread.sleep(20);
                                }

                                if (0 == timeout) {
                                    message.obj = timeout;
                                    message.what = 0x01;
                                } else {
                                    if (3 == option) {
                                        message.what = 0x11;

                                    } else {
                                        message.what = 0x21;

                                    }
                                }

                                handler.sendMessage(message);

                                thread.interrupt();
                                thread = null;

                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    });

                    thread.start();
                }
            }
        }
    }

    private class LargeResCaliListener implements android.view.View.OnClickListener {
        @Override
        public void onClick(View v) {
            EditText mEdtLargeRes = (EditText) findViewById(R.id.LargeResVal);
            String strLargeRes = mEdtLargeRes.getText().toString();
            float flLargeRes;
            byte[] val;

            Button largeResCaliButton = (Button) findViewById(R.id.button_paraLargeResCali);

            if (largeResCaliButton.getText().equals(mContext.getString(R.string.button_Recali))) {
                largeResCaliButton.setText(mContext.getString(R.string.button_Cali));

                val = new byte[9];

                if (!strLargeRes.equals("")) {
                    flLargeRes = Float.valueOf(strLargeRes);

                    if ((1000 < flLargeRes) && (2500 > flLargeRes)) {
                        val[0] = (byte) 0x69;

                        TimeOption2Bytes((byte) 2, val, (byte) 1);

                        Float2Byte(flLargeRes, val, 5);

                        val = PackData(val);

                        mDialogListener.refreshActivity(val);

                    } else if (1000 > flLargeRes) {
                        Boast.makeText(mContext, mContext.getString(R.string.hint_paraResTooSmall), Toast.LENGTH_SHORT).show();
                    } else {
                        Boast.makeText(mContext, mContext.getString(R.string.hint_paraResTooLarge), Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Boast.makeText(mContext, mContext.getString(R.string.hint_paraEmpty), Toast.LENGTH_SHORT).show();
                }

            } else {
                val = new byte[2];

                //Read para
                val[0] = (byte) 0x69;
                val[1] = (byte) 0x12;

                val = PackData(val);

                mDialogListener.refreshActivity(val);

                if (null == thread) {
                    thread = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                byte timeout = 10;
                                Message message = new Message();

                                option = (byte) 0xff;

                                while ((0 != timeout) && ((byte) 0xff == option)) {
                                    timeout--;
                                    Thread.sleep(20);
                                }

                                if (0 == timeout) {
                                    message.obj = timeout;
                                    message.what = 0x02;
                                } else {
                                    if (3 == option) {
                                        if (0 == value) {
                                            message.what = 0x32;
                                        } else if (1 == value) {
                                            message.what = 0x12;
                                        } else {
                                            message.what = 0x22;
                                        }
                                    } else {
                                        message.what = 0x22;

                                    }
                                }

                                handler.sendMessage(message);

                                thread.interrupt();
                                thread = null;

                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    });

                    thread.start();
                }
            }
        }
    }

    private class TempCaliListener implements android.view.View.OnClickListener {
        @Override
        public void onClick(View v) {
            EditText mEdtTemp = (EditText) findViewById(R.id.TempVal);
            String strTemp = mEdtTemp.getText().toString();
            float flTemp;
            byte[] val;

            Button tempCaliButton = (Button) findViewById(R.id.button_paraTempCali);

            if (tempCaliButton.getText().equals(mContext.getString(R.string.button_Recali))) {
                tempCaliButton.setText(mContext.getString(R.string.button_Cali));

                val = new byte[9];

                if (!strTemp.equals("")) {
                    flTemp = Float.valueOf(strTemp);

                    if ((25 <= flTemp) && (42 >= flTemp)) {
                        val[0] = (byte) 0x69;

                        TimeOption2Bytes((byte) 0, val, (byte) 1);

                        Float2Byte(flTemp, val, 5);

                        val = PackData(val);

                        mDialogListener.refreshActivity(val);
                    } else if (25 > flTemp) {
                        Boast.makeText(mContext, mContext.getString(R.string.hint_paraTempTooSmall), Toast.LENGTH_SHORT).show();
                    } else {
                        Boast.makeText(mContext, mContext.getString(R.string.hint_paraTempTooLarge), Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Boast.makeText(mContext, mContext.getString(R.string.hint_paraEmpty), Toast.LENGTH_SHORT).show();
                }
            } else {
                val = new byte[2];

                //Read para
                val[0] = (byte) 0x69;
                val[1] = (byte) 0x10;

                val = PackData(val);

                mDialogListener.refreshActivity(val);

                if (null == thread) {
                    thread = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                byte timeout = 10;
                                Message message = new Message();

                                option = (byte) 0xff;

                                while ((0 != timeout) && ((byte) 0xff == option)) {
                                    timeout--;
                                    Thread.sleep(20);
                                }

                                if (0 == timeout) {
                                    message.obj = timeout;
                                    message.what = 0x00;
                                } else {
                                    if (3 == option) {
                                        if ((0 == value) || (1 == value)) {
                                            message.what = 0x30;
                                        } else if (2 == value) {
                                            message.what = 0x10;
                                        } else {
                                            message.what = 0x20;
                                        }
                                    } else {
                                        message.what = 0x20;

                                    }
                                }

                                handler.sendMessage(message);

                                thread.interrupt();
                                thread = null;

                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    });

                    thread.start();
                }
            }
        }
    }
}

