Copyright (c) 2011-2014, The Linux Foundation. All rights reserved.

Redistribution and use in source form and compiled forms (SGML, HTML,
PDF, PostScript, RTF and so forth) with or without modification, are
permitted provided that the following conditions are met:

Redistributions in source form must retain the above copyright
notice, this list of conditions and the following disclaimer as the
first lines of this file unmodified.

Redistributions in compiled form (transformed to other DTDs,
converted to PDF, PostScript, RTF and other formats) must reproduce
the above copyright notice, this list of conditions and the following
disclaimer in the documentation and/or other materials provided with
the distribution.

THIS DOCUMENTATION IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE AND
NON-INFRINGEMENT ARE DISCLAIMED. IN NO EVENT SHALL THE FREEBSD
DOCUMENTATION PROJECT BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS DOCUMENTATION, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH
DAMAGE.

=========================================================================================
  Last update: 03/14/14
=========================================================================================
  Description
=========================================================================================

The PerfLock APIs can be used in either the framework or in packaged applications.
The APIs toggle system performance optimizations based upon level requested.
PerfLock will always run the highest level of optimization requested.

NOTE: Each instance of the Performance class object will serve one unique PerfLock request.
      Therefore, a new Performance class object will need to be created for every
      unique request for PerfLock.

Please read through the following carefully to understand the proper usage of this API.

=========================================================================================
  PerfLock APIs
=========================================================================================

The following two methods are the PerfLock APIs

1. perfLockAcquire(int duration, int... args)

    Description:

        Toggle on all optimizations requested.

    Arguments:

        duration: The maximum amount of time required to hold the lock.
                  Only a positive integer value in milliseconds will be accepted.
                  You may explicitly call perfLockRelease before the timer expires.

        args: Enter all optimizations required. Only the optimizations in the
              table below are supported. You can only choose one optimization
              from each of the numbered sections in the table. Incorrect or
              unsupported optimizations will be ignored.

              NOTE: Enter the optimizations required in the order they appear in the table.

    Returns: REQUEST_SUCCEEDED or REQUEST_FAILED.

2. perfLockRelease()

    Description:

        Toggle off all optimizations requested.
        Use this function if you want to release before the time duration ends.

    Arguments: None.

    Returns: REQUEST_SUCCEEDED or REQUEST_FAILED.

=========================================================================================
  Optimizations Supported
=========================================================================================

The following resource optimizations are supported:

 ===============================================================================================
|         |                                        |                                            |
| Section | Optimization                           | Description                                |
|         |                                        |                                            |
 ===============================================================================================
|    1    | ALL_CPUS_PWR_CLPS_DIS                  | Disables power collapse on all CPUs        |
|         |                                        |                                            |
 ===============================================================================================
|    2    | CPUS_ON_MAX                            | Minimum of all cores on                    |
|         |________________________________________|____________________________________________|
|         | CPUS_ON_3                              | Minimum of three cores on                  |
|         |________________________________________|____________________________________________|
|         | CPUS_ON_2                              | Minimum of two cores on                    |
|         |________________________________________|____________________________________________|
|         | CPUS_ON_LIMIT_1                        | Maximum of one core on                     |
|         |________________________________________|____________________________________________|
|         | CPUS_ON_LIMIT_2                        | Maximum of two cores on                    |
|         |________________________________________|____________________________________________|
|         | CPUS_ON_LIMIT_3                        | Maximum of three cores on                  |
|         |                                        |                                            |
 ===============================================================================================
| For the following CPU FREQ resources, please read carefully on the usage.                     |
| All frequencies available on the device are supported. In order to use an intermediate        |
| frequency not specified with an enum, you will need to pass in a valid hex value.             |
| The leftmost byte represents the CPU and the rightmost byte represents the frequency.         |
| The hex value used will be multiplied by 10^5 to calculate the minimum frequency requested.   |
| This calculated frequency or the next highest frequency available will be set.                |
|                                                                                               |
| Example: Set CPU0 frequency to a minimum of 700 Mhz                                           |
|          Use 0x207.                                                                           |
|                                                                                               |
| Example: Set CPU1 frequency to a maximum of 2.0 Ghz                                           |
|          Use 0x1614.                                                                           |
|                                                                                               |
 ===============================================================================================
|    3    | CPU0_FREQ_LVL_TURBO_MAX = 0x2FE        | Set CPU0 minimum frequency to device max   |
|         |________________________________________|____________________________________________|
|         | CPU0_FREQ_LVL_NONTURBO_MAX = 0x20A     | Set CPU0 minimum frequency to 1026 Mhz     |
|         |                                        |                                            |
 ===============================================================================================
|    4    | CPU1_FREQ_LVL_TURBO_MAX = 0x3FE        | Set CPU1 minimum frequency to device max   |
|         |________________________________________|____________________________________________|
|         | CPU1_FREQ_LVL_NONTURBO_MAX = 0x30A     | Set CPU1 minimum frequency to 1026 Mhz     |
|         |                                        |                                            |
 ===============================================================================================
|    5    | CPU2_FREQ_LVL_TURBO_MAX = 0x4FE        | Set CPU2 minimum frequency to device max   |
|         |________________________________________|____________________________________________|
|         | CPU2_FREQ_LVL_NONTURBO_MAX = 0x40A     | Set CPU2 minimum frequency to 1026 Mhz     |
|         |                                        |                                            |
 ===============================================================================================
|    6    | CPU3_FREQ_LVL_TURBO_MAX = 0x5FE        | Set CPU3 minimum frequency to device max   |
|         |________________________________________|____________________________________________|
|         | CPU3_FREQ_LVL_NONTURBO_MAX = 0x50A     | Set CPU3 minimum frequency to 1026 Mhz     |
|         |                                        |                                            |
 ===============================================================================================
|    7    | CPU0_MAX_FREQ_LVL_NONTURBO_MAX = 0x150A| Set CPU0 maximum frequency to 1026 Mhz     |
|         |                                        |                                            |
 ===============================================================================================
|    8    | CPU1_MAX_FREQ_LVL_NONTURBO_MAX = 0x160A| Set CPU1 maximum frequency to 1026 Mhz     |
|         |                                        |                                            |
 ===============================================================================================
|    9    | CPU2_MAX_FREQ_LVL_NONTURBO_MAX = 0x170A| Set CPU2 maximum frequency to 1026 Mhz     |
|         |                                        |                                            |
 ===============================================================================================
|   10    | CPU3_MAX_FREQ_LVL_NONTURBO_MAX = 0x180A| Set CPU3 maximum frequency to 1026 Mhz     |
|         |                                        |                                            |
 ===============================================================================================

=========================================================================================
  PerfLock API usage in framework
=========================================================================================

1. Add "import org.codeaurora.Performance;" in your Java source file

2. Create the Performance class object

3. Use "perfLockAcquire" to request the optmizations required
   and store the returned handle into an int variable.

4. Use "perfLockRelease" to toggle the optimizations off
   NOTE: perfLockRelease is optional but required if the duration
         of acquisition is unknown (ie. 0).
______________________________________________________________________
Example: Request PerfLock for minimum of two cores and set the
         minimum frequency for CPU0 and CPU1 to 1026 Mhz for three seconds.

   Performance mPerf = new Performance();

   mPerf.perfLockAcquire(3000, Performance.CPUS_ON_2, \
                       Performance.CPU0_FREQ_LVL_NONTURBO_MAX, Performance.CPU1_FREQ_LVL_NONTURBO_MAX);

   // Critical section requiring PerfLock

NOTE: perfLockRelease is not required since PerfLock will automatically
      release after three seconds.
______________________________________________________________________
Example: Request PerfLock for minimum of three cores in one section.
         Set duration for five seconds and release before that if possible.

         Request PerfLock for minimum of two cores in another section.
         Set duration for three seconds and release before that if possible.

   Performance mPerf = new Performance();
   Performance sPerf = new Performance();

   mPerf.perfLockAcquire(5000, Performance.CPUS_ON_3);

   // Critical section requiring PerfLock

   mPerf.perfLockRelease();

   // other code in between

   sPerf.perfLockAcquire(3000, Performance.CPUS_ON_2);

   // Critical section requiring PerfLock

   sPerf.perfLockRelease();

NOTE: perfLockRelease is recommended to ensure PerfLock is not held for longer
      than it needs to be.

=========================================================================================
  PerfLock APIs usage in packaged applications
=========================================================================================
1. Repeat above steps for using APIs in framework
2. Add "LOCAL_JAVA_LIBRARIES := org.codeaurora.Performance" to the application's Android.mk file
