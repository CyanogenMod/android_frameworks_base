/*IrdaManagerService.java */
package com.android.server;
import android.content.Context;
import android.os.Handler;
import android.hardware.IIrdaManager;
import android.os.Looper;
import android.os.Bundle;
import android.os.Message;
import android.os.Process;
import android.util.Log;
import java.io.FileWriter;

public class IrdaManagerService extends IIrdaManager.Stub {
    private static final String TAG = "IrdaManagerService";
    private IrdaWorkerThread mWorker;
    private IrdaWorkerHandler mHandler;
    private Context mContext;
    private int mNativePointer;
    public IrdaManagerService(Context context) {
        super();
        mContext = context;
        mNativePointer = init_native();
        mWorker = new IrdaWorkerThread("IrdaServiceWorker");
        mWorker.start();
    }

    public void write_irsend(String irCode) {
        Bundle irData = new Bundle();
        irData.putString("irCode", irCode);
        Message msg = Message.obtain();
        msg.what = IrdaWorkerHandler.MESSAGE_SET;
        msg.setData(irData);
        mHandler.sendMessage(msg);
    }

    private class IrdaWorkerThread extends Thread {
        public IrdaWorkerThread(String name) {
            super(name);
        }
        public void run() {
            Looper.prepare();
            mHandler = new IrdaWorkerHandler();
            Looper.loop();
        }
    }

    private class IrdaWorkerHandler extends Handler {
        private static final int MESSAGE_SET = 0;
        @Override
        public void handleMessage(Message msg) {
            try {
                if (msg.what == MESSAGE_SET) {
                    Bundle irData = msg.getData();
                    String irCode = irData.getString("irCode");
                    Log.d(TAG, "Sending IR code: " + irCode);
                    /*try{
                        FileWriter ir_send = new FileWriter("/sys/class/sec/sec_ir/ir_send");
                        ir_send.write(irCode);
                       //Close the output stream
                       ir_send.close();
                    } catch (Exception e){
                        //Catch exception if any
                        Log.e(TAG, "Failed to send IR Code");
                    }*/
                    byte[] buffer = irCode.getBytes();
                    write_native(mNativePointer, buffer);
                }
            } catch (Exception e) {
                // Log, don't crash!
                Log.e(TAG, "Exception in IrdaWorkerHandler.handleMessage:", e);
            }
        }
    }

    private static native int init_native();
    private static native void finalize_native(int ptr);
    private static native void write_native(int ptr, byte[] buffer);
}
