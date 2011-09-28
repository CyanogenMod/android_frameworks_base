package com.authentec;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;

import com.authentec.TrueSuiteMobile.RelayReceiverService;

public class GfxEngineRelayService extends Service {

    public interface Receiver
    {
        public void receiveCommand(String command, String args);
    }

    static Receiver mLocalReceiver = null;
    public static void setLocalReceiver(Receiver localReceiver) {
        // first send any queued commands to the activity
        while (!mCommandBuffer.isEmpty()) {
            Command storedCommand = mCommandBuffer.remove(0);
            if (null == localReceiver) continue;

            // send the command on to the receiver
            localReceiver.receiveCommand(
                    storedCommand.mCommand, storedCommand.mArguments);

        }

        // finally record who the receiver is
        mLocalReceiver = localReceiver;
    }

    static private ArrayList<Command> mCommandBuffer = new ArrayList<Command>();
    static private List<String> mEventBuffer = new ArrayList<String>();
    static private Semaphore mEventBufferSemaphore = null;

    private class Command {
        public String mCommand;
        public String mArguments;
        public Command(String command, String arguments) {
            mCommand = command;
            mArguments = arguments;
        }
    }

    static public void queueEvent(String event) {
        if (null == mEventBufferSemaphore) return;
        mEventBuffer.add(event);
        mEventBufferSemaphore.release();
    }

    @Override
    public IBinder onBind(Intent intent) {
        /* when we're bound to, we want to have an empty event buffer */
        mEventBuffer.clear();
        mEventBufferSemaphore = new Semaphore(0);
        return new RelayReceiverServiceImpl();
    }

    private class RelayReceiverServiceImpl extends RelayReceiverService.Stub
        implements RelayReceiverService {

        /* remote clients call sendCommand() when the GfxEngine has provided */
        /* a new command to apply to the UI                                  */
        public void sendCommand(String command, String args) throws RemoteException {

            /* if we've got a local receiver, pass the command to it */
            if (null != mLocalReceiver) {
                while (!mCommandBuffer.isEmpty()) {
                    // first pull items from the buffer. if anything is in here,
                    // it came in before the activity was ready to receive them.
                    Command storedCommand = mCommandBuffer.remove(0);
                    mLocalReceiver.receiveCommand(
                            storedCommand.mCommand, storedCommand.mArguments);
                }
                mLocalReceiver.receiveCommand(command, args);
            }
            else {
                // append it to a buffer to be delivered later
                mCommandBuffer.add(new Command(command, args));
            }
        }

        /* remote clients call receiveEvent() to get the next event from the */
        /* UI's event queue -- things like #cancel and #timeout              */
        public String receiveEvent() throws RemoteException {
            /* block until there's something in the event queue   */
            try {
                        // mEventBufferSemaphore.acquire();

                        // This method runs in the service's main thread (and there's no way
                        // to move it to a child thread, since it needs to return an answer
                        // to the GfxEngine), and when the keyguard blocks here, Android has
                        // problems. So it's better to add a timeout to the block waiting.
                        if (!mEventBufferSemaphore.tryAcquire(10, TimeUnit.SECONDS)) {

                            // The GfxEngine is not currently expecting this exception and it will
                            // try to use the null pointer. We should probably fix this in the GfxEngine,
                            // but a short term solution is to return "" instead of null.
                            return "";
                        }
            } catch (InterruptedException e) {
                        // return null;
                        return "";
            }

            /* remove the next event from the queue and return it */
            if (mEventBuffer.isEmpty()) {
                return "";
            }
            else{
                return mEventBuffer.remove(0);
            }
        }

        /* remote clients call receiveEvent() to release mEventBufferSemaphore */
        public void quit() throws RemoteException {
            mEventBufferSemaphore.release();
        }
    }
}
