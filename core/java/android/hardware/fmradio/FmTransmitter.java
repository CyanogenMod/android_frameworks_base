/*
 * Copyright (c) 2009, Code Aurora Forum. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *    * Redistributions of source code must retain the above copyright
 *      notice, this list of conditions and the following disclaimer.
 *    * Redistributions in binary form must reproduce the above copyright
 *      notice, this list of conditions and the following disclaimer in the
 *      documentation and/or other materials provided with the distribution.
 *    * Neither the name of Code Aurora nor
 *      the names of its contributors may be used to endorse or promote
 *      products derived from this software without specific prior written
 *      permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NON-INFRINGEMENT ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package android.hardware.fmradio;
/**
 * This class contains all interfaces and types needed to control the FM transmitter.
 * @hide
 */
public class FmTransmitter extends FmTransceiver
{
   /**
    *  RAW RDS Groups Transmit Control : PAUSE
    *
    *  @see #RDS_GRPS_TX_RESUME
    *  @see #RDS_GRPS_TX_STOP
    *  @see #transmitRdsGroupControl
    */
   public static final int RDS_GRPS_TX_PAUSE=0;

   /**
    *  RAW RDS Groups Transmit Control : RESUME
    *
    *  @see #RDS_GRPS_TX_RESUME
    *  @see #RDS_GRPS_TX_STOP
    *  @see #transmitRdsGroupControl
    */
   public static final int RDS_GRPS_TX_RESUME=1;

   /**
    *  RAW RDS Groups Transmit Control : STOP Stop will also clear
    *  the buffer in the driver.
    *
    *  @see #RDS_GRPS_TX_RESUME
    *  @see #RDS_GRPS_TX_STOP
    *  @see #transmitRdsGroupControl
    */
   public static final int RDS_GRPS_TX_STOP=2;

   /**
    *  Creates a transmitter object
    */
   public FmTransmitter(){
      mControl = new FmRxControls();
      mRdsData = new FmRxRdsData(sFd);
      mRxEvents = new FmRxEventListner();
   }

   /**
    *  Constructor for the transmitter class that takes path to
    *  radio and event callback adapter
    */
   public FmTransmitter(String path, FmRxEvCallbacksAdaptor callbacks){
   //TODO: Replace this prototype with the correct "Adaptor" when implemented.
   //  public FmTransmitter(String path, FMTransmitterCallbacksAdaptor adaptor){
      acquire(path);
      registerTransmitClient(callbacks);
      mControl = new FmRxControls();
      mRdsData = new FmRxRdsData(sFd);
      mRxEvents = new FmRxEventListner();
   }

   /*==============================================================
   FUNCTION:  enable
   ==============================================================*/
   /**
    *  Enables the FM device in Transmit Mode.
    *  <p>
    *  This is a synchronous method used to initialize the FM
    *  receiver. If already initialized this function will
    *  intialize the receiver with default settings. Only after
    *  successfully calling this function can many of the FM device
    *  interfaces be used.
    *  <p>
    *  When enabling the receiver, the application must also
    *  provide the regional settings in which the transmitter will
    *  operate. These settings (included in argument
    *  configSettings) are typically used for setting up the FM
    *  Transmitter for operating in a particular geographical
    *  region. These settings can be changed after the FM driver
    *  is enabled through the use of the function {@link
    *  #configure}.
    *  <p>
    *  This command can only be issued by the owner of an FM
    *  receiver.  To issue this command, the application must
    *  first successfully call {@link #acquire}.
    *  <p>
    *  @param configSettings  the settings to be applied when
    *                           turning on the radio
    *  @return true if Initialization succeeded, false if
    *          Initialization failed.
    *  <p>
    *  @see #enable
    *  @see #registerTransmitClient
    *  @see #disable
    *
    */
   public boolean enable (FmConfig configSettings){
      boolean status;

      /* Enable the Transceiver common for both
         receiver and transmitter
         */
      status = super.enable(configSettings, FmTransceiver.FM_TX);

      /* Do transmitter Specific Enable Stuff here.*/

      return true;
   }

   /*==============================================================
   FUNCTION:  disable
   ==============================================================*/
   /**
    *  Disables the FM Transmitter Device.
    *  <p>
    *  This is a synchronous command used to disable the FM
    *  device. This function is expected to be used when the
    *  application no longer requires use of the FM device. Once
    *  called, most functionality offered by the FM device will be
    *  disabled until the application re-enables the device again
    *  via {@link #enable}.
    *
    *  <p>
    *  @return true if disabling succeeded, false if disabling
    *          failed.
    *
    *  @see #enable
    *  @see #registerTransmitClient
    */
   public boolean disable(){
      boolean status;
      status = super.disable();
      return true;
   }

   /*==============================================================
   FUNCTION:  getPSFeatures
   ==============================================================*/
   /**
    *  This function returns the features supported by the FM
    *  driver when using {@link #setPSInfo}.
    *  <p>
    *  This function is used to get the features the FM driver
    *  supports when transmitting Program Service information.
    *  Included in the returned features is the number of Program
    *  Service (PS) characters which can be transmitted using
    *  {@link #setPSInfo}. If the driver supports continuous
    *  transmission of Program Service Information, this function
    *  will return a value greater than 0 for
    *  FmPSFeatures.maxPSCharacters. Although the RDS/RBDS
    *  standard defines each Program Service (PS) string as eight
    *  characters in length, the FM driver may have the ability to
    *  accept a string that is greater than eight character. This
    *  extended string will thenbe broken up into multiple strings
    *  of length eight and transmitted continuously.
    *  <p>
    *  When transmitting more than one string, the application may
    *  want to control the timing of how long each string is
    *  transmitted. Included in the features returned from this
    *  function is the maximum Program Service string repeat count
    *  (FmPSFeatures.maxPSStringRepeatCount). When using the
    *  function {@link #setPSInfo}, the application can specify how
    *  many times each string is repeated before the next string is
    *  transmitted.
    *
    *  @return the Program service maximum characters and repeat
    *          count
    *
    *  @see #setPSInfo
    *
    */
   public FmPSFeatures getPSFeatures(){
      FmPSFeatures psFeatures = new FmPSFeatures();
      psFeatures.maxPSCharacters = 0;
      psFeatures.maxPSStringRepeatCount = 0;
      return psFeatures;
   }

   /*==============================================================
   FUNCTION:  setPSInfo
   ==============================================================*/
   /**
    *  Continuously transmit RDS/RBDS Program Service information
    *  over an already tuned station.
    *  <p>
    *  This is a function used to continuously transmit Program
    *  Service information over an already tuned station. While
    *  Program Service information can be transmitted using {@link
    *  #transmitRdsGroups} and 0A/0B groups, this function makes
    *  the same output possible with limited input needed from the
    *  application.
    *  <p>
    *  Included in the Program Service information is an RDS/RBDS
    *  program type (PTY), and one or more Program Service
    *  strings. The program type (PTY) is used to describe the
    *  content being transmitted and follows the RDS/RBDS program
    *  types described in the RDS/RBDS specifications.
    *  <p>
    *  Program Service information also includes an eight
    *  character string. This string can be used to display any
    *  information, but is typically used to display information
    *  about the audio being transmitted. Although the RDS/RBDS
    *  standard defines a Program Service (PS) string as eight
    *  characters in length, the FM driver may have the ability to
    *  accept a string that is greater than eight characters. This
    *  extended string will then be broken up into multiple eight
    *  character strings which will be transmitted continuously.
    *  All strings passed to this function must be terminated by a
    *  null character (0x00).
    *  <p>
    *  When transmitting more than one string, the application may
    *  want to control the timing of how long each string is
    *  transmitted. To control this timing and to ensure that the FM
    *  receiver receives each string, the application can specify
    *  how many times each string is repeated before the next string
    *  is transmitted. This command can only be issued by the owner
    *  of an FM transmitter.
    *
    *  <p>
    *  Use {@link #setPSRTProgramType} to update the ProgramType to
    *  be used as part of the PS Information.
    *
    *  Note: psStr should contain strings each of 8 characters or
    *        less. If the string is greater than 8 characters, the
    *        string will be truncated.
    *
    *  @param psStr the program service strings to transmit
    *  @param pty the program type to use in the program Service
    *             information.
    *  @param repeatCount the number of times each 8 char string is
    *                     repeated before next string
    *
    *  @return true if PS information was successfully sent to the
    *             driver, false if PS information could not be sent
    *             to the driver.
    *
    *  @see #getPSFeatures
    *  @see #setPSRTProgramType
    *  @see #stopPSInfo
    */
   public boolean setPSInfo(String[] psStr, int pty, long repeatCount){
       boolean bStatus = false;

       return bStatus;
   }

   /*==============================================================
   FUNCTION:  stopPSInfo
   ==============================================================*/
   /**
    *  Stop an active Program Service transmission.
    *
    *  <p>
    *  This is a function used to stop an active Program Service transmission
    *  started by {@link #setPSInfo}.
    *
    *  @return true if Stop PS information was successfully sent to
    *             the driver, false if Stop PS information could not
    *             be sent to the driver.
    *
    *  @see #getPSFeatures
    *  @see #setPSInfo
    *
    */
   public boolean stopPSInfo(){
      boolean bStatus = false;

      return bStatus;
   }

   /*==============================================================
   FUNCTION:  setPSInfo
   ==============================================================*/
   /**
    *  Continuously transmit RDS/RBDS RadioText information over an
    *  already tuned station.
    *
    *  <p>
    *  This is a function used to continuously transmit RadioText
    *  information over an already tuned station. While RadioText
    *  information can be transmitted using
    *  {@link #transmitRdsGroups} and 2A/2B groups, this function
    *  makes the same output possible with limited input needed from
    *  the application.
    *  <p>
    *  Included in the RadioText information is an RDS/RBDS program type (PTY),
    *  and a single string of up to 64 characters. The program type (PTY) is used
    *  to describe the content being transmitted and follows the RDS/RBDS program
    *  types described in the RDS/RBDS specifications.
    *  <p>
    *  RadioText information also includes a string that consists of up to 64
    *  characters. This string can be used to display any information, but is
    *  typically used to display information about the audio being transmitted.
    *  This RadioText string is expected to be at 64 characters in length, or less
    *  than 64 characters and terminated by a return carriage (0x0D). All strings
    *  passed to this function must be terminated by a null character (0x00).
    *  <p>
    *  Use {@link #setPSRTProgramType} to update the ProgramType to
    *  be used as part of the RT Information.
    *
    *  Note: rtStr should contain a maximum of 64 characters.
    *        If the string is greater than 64 characters, the string
    *        will be truncated to 64 characters.
    *
    *  @param rtStr the Radio Text string to transmit
    *
    *  @return true if RT information String was successfully sent
    *             to the driver, false if RT information string
    *             could not be sent to the driver.
    *
    *  @see #setPSRTProgramType
    *  @see #stopRTInfo
    */
   public boolean setRTInfo(String rtStr){
      boolean bStatus = false;

      return bStatus;
   }

   /*==============================================================
   FUNCTION:  stopRTInfo
   ==============================================================*/
   /**
    *  Stop an active Radio Text information transmission.
    *
    *  <p>
    *  This is a function used to stop an active Radio Text
    *  transmission started by {@link #setRTInfo}.
    *
    *  @return true if Stop RT information was successfully sent to
    *             the driver, false if Stop RT information could not
    *             be sent to the driver.
    *
    *  @see #setRTInfo
    *
    */
   public boolean stopRTInfo(){
      boolean bStatus = false;

      return bStatus;
   }

   /*==============================================================
   FUNCTION:  setPSRTProgramType
   ==============================================================*/
   /**
    *  Set the Program Type to be used for Continuously transmit
    *  RDS/RBDS Program Service information and RDS/RBDS RadioText
    *  information.
    *  <p>
    *  This is a function to set the Program Type to be used while
    *  transmitting Program Service and Radio Text Information.
    *  <p>
    *  Included in the Program Service information is an RDS/RBDS program type (PTY), and
    *  one or more Program Service strings. The program type (PTY)
    *  is used to describe the content being transmitted and follows
    *  the RDS/RBDS program types described in the RDS/RBDS
    *  specifications.
    *  <p>
    *  Included in the RadioText information is an RDS/RBDS program type (PTY),
    *  and a single string of up to 64 characters. The program type (PTY) is used
    *  to describe the content being transmitted and follows the RDS/RBDS program
    *  types described in the RDS/RBDS specifications.
    *
    *  Note: If the PS or RT transmission is currently active, the
    *  PTY change will take effect when the current transmission is
    *  complete. Typically  setPSRTProgramType should be called
    *  before {@link #setPSInfo} or {@link #setRTInfo} is started.
    *
    *  @param pty the program type to use in the program Service
    *             information and Radio Text information.
    *
    *  @return true if the driver was updated to use the new
    *             Program Type, false if the driver could not be
    *             updated.
    *
    *  @see #setPSInfo
    *  @see #setRTInfo
    *
    */
   public boolean setPSRTProgramType(int pty){
      boolean bStatus = false;

      return bStatus;
   }


   /*==============================================================
   FUNCTION:  getRdsGroupBufSize
   ==============================================================*/
   /**
    *  Get the maximum number of RDS/RBDS groups which can be passed
    *  to the FM driver.
    *  <p>
    *  This is a function used to determine the maximum RDS/RBDS
    *  buffer size for use when calling {@link #transmitRdsGroups}
    *
    *  @return the maximum number of RDS/RBDS groups which can be
    *  passed to the FM driver at any one time.
    *
    */
   public int getRdsGroupBufSize(){
      return 0;
   }


   /*==============================================================
   FUNCTION:  transmitRdsGroups
   ==============================================================*/
   /**
    *  This function will transmit RDS/RBDS groups over an already tuned station.
    *  <p>
    *  This function accepts a buffer (rdsGroups) containing one or
    *  more RDS groups. When sending this buffer, the application
    *  must also indicate how many groups should be taken from this
    *  buffer (numGroupsToTransmit). It may be possible that the FM
    *  driver can not accept the number of group contained in the
    *  buffer and will indicate how many group were actually
    *  accepted through the return value.
    *
    *  <p>
    *  The FM driver will indicate to the application when it is
    *  ready to accept more data via both the
    *  "onRDSGroupsAvailable()" and "onRDSGroupsComplete()" events
    *  callbacks. The "onRDSGroupsAvailable()" callback will
    *  indicate to the application that the FM driver can accept
    *  additional groups even thoughall groups may not have been
    *  passed to the FM transmitter. The onRDSGroupsComplete()
    *  callback will indicate when the FM driver has a complete
    *  buffer to transmit RDS data. In many cases all data passed to
    *  the FM driver will be passed to the FM hardware and only a
    *  onRDSGroupsComplete() event will be generated by the
    *  FM driver.
    *  <p> If the application attempts to send more groups than the
    *  FM driver can handle, the application must wait until it
    *  receives a onRDSGroupsAvailable or a onRDSGroupsComplete
    *  event before attempting to transmit more groups. Failure to
    *  do so may result in no group being consumed by the FM driver.
    *  <p> It is important to note that switching between continuous
    *  and non-continuous transmission of RDS groups can only happen
    *  when no RDS/RBDS group transmission is underway. If an
    *  RDS/RBDS group transmission is already underway, the
    *  application must wait for a onRDSGroupsComplete. If the application
    *  wishes to switch from continuous to non-continuous (or
    *  vice-versa) without waiting for the current transmission to
    *  complete, the application can clear all remaining groups
    *  using the {@link #transmitRdsGroupControl} command.
    *  <p>
    *  Once completed, this command will generate a
    *  onRDSGroupsComplete event to all registered applications.
    *
    *  @param rdsGroups The RDS/RBDS groups buffer to transmit.
    *  @param numGroupsToTransmit The number of groups in the buffer
    *                             to transmit.
    *
    *  @return The number of groups the FM driver actually accepted.
    *          A value >0 indicates the command was successfully
    *          accepted and a return value of "-1" indicates error.
    *
    *  @see #transmitRdsGroupControl
    */

   public int transmitRdsGroups(byte[] rdsGroups, long numGroupsToTransmit){
      return -1;
   }

   /*==============================================================
   FUNCTION:  transmitRdsGroupControl
   ==============================================================*/
   /**
    *  Pause/Resume RDS/RBDS group transmission, or stop and clear
    *  all RDS groups.
    *  <p>
    *  This is a function used to used to pause/resume RDS/RBDS
    *  group transmission, or stop and clear all RDS groups. This
    *  function can be used with to control continuous and
    *  non-continuous RDS/RBDS group transmissions.
    *  <p>
    *  @param ctrlCmd The Tx RDS group control.
    *
    *  @return true if RDS Group Control command was
    *             successfully sent to the driver, false if RDS
    *             Group Control command could not be sent to the
    *             driver.
    *
    *  @see #transmitRdsGroups
    */
   public boolean transmitRdsGroupControl(int ctrlCmd){
      boolean bStatus = false;

      return bStatus;
   }

   /*==============================================================
   FUNCTION:  isAntennaAvailable
   ==============================================================*/
   /**
    *  Read if an internal Antenna is available for the FM
    *  device.
    *  <p>
    *
    *  This method is available to find that if an internal
    *  Antenna is present or not
    *
    *
    *  @return true if an internal Antenna is available, false if
    *          if an internal antenna is not available and an
    *          external Antenna has to be used.
    *
    */
   boolean isAntennaAvailable()
   {
      return true;
   }


   /**
    *  An object that contains the PS Features as configured using
    *  {@link #setPSInfo}.
    *
    *  @see #setPSInfo
    *  @see #getPSFeatures
    */
   public class FmPSFeatures
   {
      public int maxPSCharacters;
      public int maxPSStringRepeatCount;
   };

   /**
    *  The interface that provides the applications to get callback
    *  for asynchronous events.
    *  An Adapter that implements this interface needs to be used to
    *  register to receive the callbacks using {@link
    *  #registerTransmitClient}
    *
    *  @see #registerTransmitClient
    */
   public interface TransmitterCallbacks
   {
      /**
       *  The callback indicates that the transmitter is tuned to a
       *  new frequency Typically received after setStation.
       */
      public void onTuneFrequencyChange(int freq);

      /**
       * The callback to indicate to the application that the FM
       * driver can accept additional groups even though all groups
       * may not have been passed to the FM transmitter.
       */
      public void onRDSGroupsAvailable();
      /**
       *  The callback will indicate when the FM driver has a complete
       *  buffer to transmit RDS data.
       */
      public void onRDSGroupsComplete();
   };
}
