package com.vanir.util;

import android.os.AsyncTask;

import com.vanir.objects.EasyPair;
import com.vanir.util.CMDProcessor;
import com.vanir.util.Helpers;

/**
 * An abstract implentation of AsyncTask
 *
 * since our needs are simple send a command, perform a task when we finish
 * this implentation requires you send the command as String...
 * in the .execute(String) so you can send String[] of commands if needed
 *
 * This class is not for you if...
 *     1) You do not need to perform any action after command execution
 *        you want a Thread not this.
 *     2) You need to perform more complex tasks in doInBackground
 *        than simple script/command sequence of commands
 *        you want your own AsyncTask not this.
 *
 * This class is for you if...
 *     1) You need to run a command/script/sequence of commands without
 *        blocking the UI thread and you must perform actions after the
 *        task completes.
 *     2) see #1.
 */
public abstract class AbstractAsyncSuCMDProcessor extends AsyncTask<String, Void, String> {
    // if /system needs to be mounted before command
    private boolean mMountSystem;
    // su terminal we execute on
    private CMDProcessor mTerm;
    // return if we recieve a null command or empty command
    public final String FAILURE = "failed_no_command";

    /**
     * Constructor that allows mounting/dismounting
     * of /system partition while in background thread
     */
    public AbstractAsyncSuCMDProcessor(boolean mountSystem) {
         this.mMountSystem = mountSystem;
    }

    /**
     * Constructor that assumes /system should not be mounted
     */
    public AbstractAsyncSuCMDProcessor() {
         this.mMountSystem = false;
    }

    /**
     * DO NOT override this method you should simply send your commands off
     * as params and expect to handle results in {@link #onPostExecute}
     *
     * if you find a need to @Override this method then you should
     * consider using a new AsyncTask implentation instead
     *
     * @param params The parameters of the task.
     *
     * @return A result, defined by the subclass of this task.
     */
    @Override
    protected String doInBackground(String... params) {
        // don't bother if we don't get a command
        if (params[0] == null || params[0].trim().equals(""))
            return FAILURE;

        mTerm = new CMDProcessor();
        EasyPair<String, String> pairedOutput = new EasyPair<String, String>(FAILURE, FAILURE);

        // conditionally enforce mounting
        if (mMountSystem) {
            Helpers.getMount("rw");
        }
        try {
            // process all commands ***DO NOT SEND null OR ""; you have been warned***
            for (int i = 0; params.length > i; i++) {
                // always watch for null and empty strings, lazy devs :/
                if (params[i] != null && !params[i].trim().equals(""))
                    pairedOutput = mTerm.su.runWaitFor(params[i]).getOutput();
                else
                    // bail because of careless devs
                    return FAILURE;
            }
        // always unmount
        } finally {
            if (mMountSystem)
                Helpers.getMount("ro");
        }
        // return the last commmand result output EasyPair<stdout, stderr>
        return pairedOutput.getFirst();
    }

    /**
     * <p>Runs on the UI thread after {@link #doInBackground}. The
     * specified result is the value returned by {@link #doInBackground}.</p>
     *
     * <p>This method won't be invoked if the task was cancelled.</p>
     *
     * You MUST @Override this method if you don't need the result
     * then you should consider using a new Thread implentation instead
     *
     * @param result The result of the operation computed by {@link #doInBackground}.
     */
    @Override
    protected abstract void onPostExecute(String result);
}
