package com.android.server.job;


import android.content.ComponentName;
import android.content.Context;
import android.app.job.JobInfo;
import android.app.job.JobInfo.Builder;
import android.os.PersistableBundle;
import android.os.SystemClock;
import android.test.AndroidTestCase;
import android.test.RenamingDelegatingContext;
import android.util.Log;
import android.util.ArraySet;

import com.android.server.job.controllers.JobStatus;

import java.util.Iterator;

/**
 * Test reading and writing correctly from file.
 */
public class JobStoreTest extends AndroidTestCase {
    private static final String TAG = "TaskStoreTest";
    private static final String TEST_PREFIX = "_test_";

    private static final int SOME_UID = 34234;
    private ComponentName mComponent;
    private static final long IO_WAIT = 1000L;

    JobStore mTaskStoreUnderTest;
    Context mTestContext;

    @Override
    public void setUp() throws Exception {
        mTestContext = new RenamingDelegatingContext(getContext(), TEST_PREFIX);
        Log.d(TAG, "Saving tasks to '" + mTestContext.getFilesDir() + "'");
        mTaskStoreUnderTest =
                JobStore.initAndGetForTesting(mTestContext, mTestContext.getFilesDir());
        mComponent = new ComponentName(getContext().getPackageName(), StubClass.class.getName());
    }

    @Override
    public void tearDown() throws Exception {
        mTaskStoreUnderTest.clear();
    }

    public void testMaybeWriteStatusToDisk() throws Exception {
        int taskId = 5;
        long runByMillis = 20000L; // 20s
        long runFromMillis = 2000L; // 2s
        long initialBackoff = 10000L; // 10s

        final JobInfo task = new Builder(taskId, mComponent)
                .setRequiresCharging(true)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setBackoffCriteria(initialBackoff, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
                .setOverrideDeadline(runByMillis)
                .setMinimumLatency(runFromMillis)
                .setPersisted(true)
                .build();
        final JobStatus ts = new JobStatus(task, SOME_UID);
        mTaskStoreUnderTest.add(ts);
        Thread.sleep(IO_WAIT);
        // Manually load tasks from xml file.
        final ArraySet<JobStatus> jobStatusSet = new ArraySet<JobStatus>();
        mTaskStoreUnderTest.readJobMapFromDisk(jobStatusSet);

        assertEquals("Didn't get expected number of persisted tasks.", 1, jobStatusSet.size());
        final JobStatus loadedTaskStatus = jobStatusSet.iterator().next();
        assertTasksEqual(task, loadedTaskStatus.getJob());
        assertTrue("JobStore#contains invalid.", mTaskStoreUnderTest.containsJob(ts));
        assertEquals("Different uids.", SOME_UID, loadedTaskStatus.getUid());
        compareTimestampsSubjectToIoLatency("Early run-times not the same after read.",
                ts.getEarliestRunTime(), loadedTaskStatus.getEarliestRunTime());
        compareTimestampsSubjectToIoLatency("Late run-times not the same after read.",
                ts.getLatestRunTimeElapsed(), loadedTaskStatus.getLatestRunTimeElapsed());

    }

    public void testWritingTwoFilesToDisk() throws Exception {
        final JobInfo task1 = new Builder(8, mComponent)
                .setRequiresDeviceIdle(true)
                .setPeriodic(10000L)
                .setRequiresCharging(true)
                .setPersisted(true)
                .build();
        final JobInfo task2 = new Builder(12, mComponent)
                .setMinimumLatency(5000L)
                .setBackoffCriteria(15000L, JobInfo.BACKOFF_POLICY_LINEAR)
                .setOverrideDeadline(30000L)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
                .setPersisted(true)
                .build();
        final JobStatus taskStatus1 = new JobStatus(task1, SOME_UID);
        final JobStatus taskStatus2 = new JobStatus(task2, SOME_UID);
        mTaskStoreUnderTest.add(taskStatus1);
        mTaskStoreUnderTest.add(taskStatus2);
        Thread.sleep(IO_WAIT);

        final ArraySet<JobStatus> jobStatusSet = new ArraySet<JobStatus>();
        mTaskStoreUnderTest.readJobMapFromDisk(jobStatusSet);
        assertEquals("Incorrect # of persisted tasks.", 2, jobStatusSet.size());
        Iterator<JobStatus> it = jobStatusSet.iterator();
        JobStatus loaded1 = it.next();
        JobStatus loaded2 = it.next();

        // Reverse them so we know which comparison to make.
        if (loaded1.getJobId() != 8) {
            JobStatus tmp = loaded1;
            loaded1 = loaded2;
            loaded2 = tmp;
        }

        assertTasksEqual(task1, loaded1.getJob());
        assertTasksEqual(task2, loaded2.getJob());
        assertTrue("JobStore#contains invalid.", mTaskStoreUnderTest.containsJob(taskStatus1));
        assertTrue("JobStore#contains invalid.", mTaskStoreUnderTest.containsJob(taskStatus2));
        // Check that the loaded task has the correct runtimes.
        compareTimestampsSubjectToIoLatency("Early run-times not the same after read.",
                taskStatus1.getEarliestRunTime(), loaded1.getEarliestRunTime());
        compareTimestampsSubjectToIoLatency("Late run-times not the same after read.",
                taskStatus1.getLatestRunTimeElapsed(), loaded1.getLatestRunTimeElapsed());
        compareTimestampsSubjectToIoLatency("Early run-times not the same after read.",
                taskStatus2.getEarliestRunTime(), loaded2.getEarliestRunTime());
        compareTimestampsSubjectToIoLatency("Late run-times not the same after read.",
                taskStatus2.getLatestRunTimeElapsed(), loaded2.getLatestRunTimeElapsed());

    }

    public void testWritingTaskWithExtras() throws Exception {
        JobInfo.Builder b = new Builder(8, mComponent)
                .setRequiresDeviceIdle(true)
                .setPeriodic(10000L)
                .setRequiresCharging(true)
                .setPersisted(true);

        PersistableBundle extras = new PersistableBundle();
        extras.putDouble("hello", 3.2);
        extras.putString("hi", "there");
        extras.putInt("into", 3);
        b.setExtras(extras);
        final JobInfo task = b.build();
        JobStatus taskStatus = new JobStatus(task, SOME_UID);

        mTaskStoreUnderTest.add(taskStatus);
        Thread.sleep(IO_WAIT);

        final ArraySet<JobStatus> jobStatusSet = new ArraySet<JobStatus>();
        mTaskStoreUnderTest.readJobMapFromDisk(jobStatusSet);
        assertEquals("Incorrect # of persisted tasks.", 1, jobStatusSet.size());
        JobStatus loaded = jobStatusSet.iterator().next();
        assertTasksEqual(task, loaded.getJob());
    }

    public void testMassivePeriodClampedOnRead() throws Exception {
        final long TEN_SECONDS = 10000L;
        JobInfo.Builder b = new Builder(8, mComponent)
                .setPeriodic(TEN_SECONDS)
                .setPersisted(true);
        final long invalidLateRuntimeElapsedMillis =
                SystemClock.elapsedRealtime() + (TEN_SECONDS * 2) + 5000;  // >2P from now.
        final long invalidEarlyRuntimeElapsedMillis =
                invalidLateRuntimeElapsedMillis - TEN_SECONDS;  // Early is (late - period).
        final JobStatus js = new JobStatus(b.build(), SOME_UID,
                invalidEarlyRuntimeElapsedMillis, invalidLateRuntimeElapsedMillis);

        mTaskStoreUnderTest.add(js);
        Thread.sleep(IO_WAIT);

        final ArraySet<JobStatus> jobStatusSet = new ArraySet<JobStatus>();
        mTaskStoreUnderTest.readJobMapFromDisk(jobStatusSet);
        assertEquals("Incorrect # of persisted tasks.", 1, jobStatusSet.size());
        JobStatus loaded = jobStatusSet.iterator().next();

        // Assert early runtime was clamped to be under now + period. We can do <= here b/c we'll
        // call SystemClock.elapsedRealtime after doing the disk i/o.
        final long newNowElapsed = SystemClock.elapsedRealtime();
        assertTrue("Early runtime wasn't correctly clamped.",
                loaded.getEarliestRunTime() <= newNowElapsed + TEN_SECONDS);
        // Assert late runtime was clamped to be now + period*2.
        assertTrue("Early runtime wasn't correctly clamped.",
                loaded.getEarliestRunTime() <= newNowElapsed + TEN_SECONDS*2);
    }

    /**
     * Helper function to throw an error if the provided task and TaskStatus objects are not equal.
     */
    private void assertTasksEqual(JobInfo first, JobInfo second) {
        assertEquals("Different task ids.", first.getId(), second.getId());
        assertEquals("Different components.", first.getService(), second.getService());
        assertEquals("Different periodic status.", first.isPeriodic(), second.isPeriodic());
        assertEquals("Different period.", first.getIntervalMillis(), second.getIntervalMillis());
        assertEquals("Different inital backoff.", first.getInitialBackoffMillis(),
                second.getInitialBackoffMillis());
        assertEquals("Different backoff policy.", first.getBackoffPolicy(),
                second.getBackoffPolicy());

        assertEquals("Invalid charging constraint.", first.isRequireCharging(),
                second.isRequireCharging());
        assertEquals("Invalid idle constraint.", first.isRequireDeviceIdle(),
                second.isRequireDeviceIdle());
        assertEquals("Invalid unmetered constraint.",
                first.getNetworkType() == JobInfo.NETWORK_TYPE_UNMETERED,
                second.getNetworkType() == JobInfo.NETWORK_TYPE_UNMETERED);
        assertEquals("Invalid connectivity constraint.",
                first.getNetworkType() == JobInfo.NETWORK_TYPE_ANY,
                second.getNetworkType() == JobInfo.NETWORK_TYPE_ANY);
        assertEquals("Invalid deadline constraint.",
                first.hasLateConstraint(),
                second.hasLateConstraint());
        assertEquals("Invalid delay constraint.",
                first.hasEarlyConstraint(),
                second.hasEarlyConstraint());
        assertEquals("Extras don't match",
                first.getExtras().toString(), second.getExtras().toString());
    }

    /**
     * When comparing timestamps before and after DB read/writes (to make sure we're saving/loading
     * the correct values), there is some latency involved that terrorises a naive assertEquals().
     * We define a <code>DELTA_MILLIS</code> as a function variable here to make this comparision
     * more reasonable.
     */
    private void compareTimestampsSubjectToIoLatency(String error, long ts1, long ts2) {
        final long DELTA_MILLIS = 700L;  // We allow up to 700ms of latency for IO read/writes.
        assertTrue(error, Math.abs(ts1 - ts2) < DELTA_MILLIS + IO_WAIT);
    }

    private static class StubClass {}

}
