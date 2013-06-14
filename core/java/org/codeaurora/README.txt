Copyright (c) 2011-2012, The Linux Foundation. All rights reserved.

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

==================================
  Description
==================================

The PerfLock APIs can be used in either the framework or in packaged applications.
The APIs toggle system performance optimizations based upon level requested.

==================================
  PerfLock APIs
==================================

The following two methods are the PerfLock APIs

1. perfLockAcquire(int... args)

    Toggle on all optimizations requested.

    Description: Call perfLockAcquire with the list of optimizations required.
                 perfLockAcquire accepts variable number of arguments, enter all
                 optimizations required at once.

                 See next section below for the optimizations supported.

    Limitations: Only the first five optimizations will be performed.
                 You are only allowed to choose one optimization from each of the
                 numbered sections in the table below.

                 Incorrect or unsupported optimizations will be ignored.

    Returns: REQUEST_SUCCEEDED or REQUEST_FAILED.

2. perfLockRelease()

    Toggle off all optimizations requested.

    Returns: REQUEST_SUCCEEDED or REQUEST_FAILED.

=============================
  Optimizations Supported
=============================

The following resource optimizations are supported for MSM8960:

 =============================================================================
|         |                         |                                         |
| Section | Optimization            | Description                             |
|         |                         |                                         |
 =============================================================================
|    1    | PWR_CLSP_A              | Disables all power collapse             |
|         |                         |                                         |
 =============================================================================
|    2    | HEAP_OPT_A              | Optimizes heap parameters               |
|         |                         |                                         |
 =============================================================================
|    3    | CPUS_ON_LVL_MAX         | Turn on all additional cores            |
|         |_________________________|_________________________________________|
|         | CPUS_ON_LVL_3           | Turn on three additional cores          |
|         |_________________________|_________________________________________|
|         | CPUS_ON_LVL_2           | Turn on two additional cores            |
|         |_________________________|_________________________________________|
|         | CPUS_ON_LVL_1           | Turn on one additional core             |
|         |                         |                                         |
 =============================================================================
|    4    | CPU0_FREQ_LVL_MAX       | Set CPU0 minimum frequency to MAX       |
|         |_________________________|_________________________________________|
|         | CPU0_FREQ_LVL_TURBO     | Set CPU0 minimum frequency to 1512 Mhz  |
|         |_________________________|_________________________________________|
|         |  CPU0_FREQ_LVL_NONTURBO | Set CPU0 minimum frequency to 1026 Mhz  |
|         |                         |                                         |
 =============================================================================
|    5    | CPU1_FREQ_LVL_MAX       | Set CPU1 minimum frequency to MAX       |
|         |_________________________|_________________________________________|
|         | CPU1_FREQ_LVL_TURBO     | Set CPU1 minimum frequency to 1512 Mhz  |
|         |_________________________|_________________________________________|
|         | CPU1_FREQ_LVL_NONTURBO  | Set CPU1 minimum frequency to 1026 Mhz  |
|         |                         |                                         |
 =============================================================================

=====================================
  PerfLock API usage in framework
=====================================

1. Add "import org.codeaurora.Performance;" in your Java source file
2. Create the Performance class object
3. Use "perfLockAcquire" to request the optmizations required
4. Use "perfLockRelease" to toggle the optimizations off
______________________________________________________________________
Example: Request PerfLock to bring up all additional cores and set the
         minimum frequency for CPU0 and CPU1 to 1026 Mhz.

   Performance mPerf = new Performance();
   mPerf.perfLockAcquire(mPerf.CPUS_ON_LVL_MAX, mPerf.CPU0_FREQ_LVL_NONTURBO, mPerf.CPU1_FREQ_LVL_NONTURBO);

   // Critical section requiring PerfLock

   mPerf.perfLockRelease();

=================================================
  PerfLock APIs usage in packaged applications
=================================================
1. Repeat above steps for using APIs in framework
2. Add "LOCAL_JAVA_LIBRARIES := org.codeaurora.Performance" to the application's Android.mk file
