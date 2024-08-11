package com.dayani.m.roboplatform.utils.interfaces;

import static android.os.Build.VERSION.SDK_INT;

import com.dayani.m.roboplatform.drivers.MyDrvUsb;
import com.dayani.m.roboplatform.managers.MySensorManager;
import com.dayani.m.roboplatform.managers.MyLocationManager;

import android.hardware.SensorEvent;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureResult;
import android.hardware.usb.UsbConstants;
import android.location.GnssMeasurement;
import android.location.GnssNavigationMessage;
import android.location.Location;
import android.media.Image;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.SystemClock;

import androidx.annotation.NonNull;

import com.dayani.m.thirdparty.google.gnsslogger.MeasurementProvider;

import com.dayani.m.roboplatform.utils.interfaces.MyChannels.ChannelType;

import java.util.List;
import java.util.Locale;

public interface MyMessages {

    class MyMessage implements Parcelable {

        // the receiver's tag, null: means it's a broadcast message
        protected String mChTag;
        protected final ChannelType mChType;
        // specifies the goal (e.g. write to which file for the same channel?)
        protected int mTargetId;

        protected String mStringMsg;

        public MyMessage(ChannelType chType) {

            mChType = chType;
            setChTag(null);
            setTargetId(-1);
            setStringMessage("");
        }

        public MyMessage(ChannelType chType, String chTag) {

            this(chType);
            setChTag(chTag);
        }

        public MyMessage(ChannelType chType, String chTag, String msg) {

            this(chType, chTag);
            setStringMessage(msg);
        }

        public MyMessage(ChannelType chType, String chTag, int targetId, String msg) {

            this(chType, chTag, msg);
            setTargetId(targetId);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        private MyMessage(Parcel in) {

            setChTag(in.readString());
            mChType = ChannelType.valueOf(in.readString());
            setTargetId(in.readInt());
            setStringMessage(in.readString());
        }

        @Override
        public void writeToParcel(Parcel parcel, int i) {

            parcel.writeString(getChTag());
            parcel.writeString(mChType.name());
            parcel.writeInt(getTargetId());
            parcel.writeString(toString());
        }

        public static final Parcelable.Creator<MyMessage> CREATOR
                = new Parcelable.Creator<MyMessage>() {
            public MyMessage createFromParcel(Parcel in) {
                return new MyMessage(in);
            }

            public MyMessage[] newArray(int size) {
                return new MyMessage[size];
            }
        };

        @NonNull
        @Override
        public String toString() {
            return mStringMsg;
        }
        public void setStringMessage(String msg) { mStringMsg = msg; }

        public String getChTag() {
            return mChTag;
        }
        public void setChTag(String mTag) {
            this.mChTag = mTag;
        }

        public boolean isChType(ChannelType chType) { return mChType == chType; }

        public int getTargetId() {
            return mTargetId;
        }

        public void setTargetId(int targetId) {
            mTargetId = targetId;
        }
    }

    class MsgConfig extends MyMessage {

        public enum ConfigAction {
            OPEN,
            CLOSE,
            RESET,
            RESPOND,
            GET_STATE
        }

        protected final ConfigAction mConfigAction;
        protected final String mSender;

        public MsgConfig(ConfigAction config, String sender) {

            super(ChannelType.CONFIGURATION, null);
            mConfigAction = config;
            mSender = sender;
        }

        public MsgConfig(ConfigAction config, String sender, String chTag) {

            this(config, sender);
            setChTag(chTag);
        }

        public ConfigAction getConfigAction() {
            return mConfigAction;
        }

        public boolean isConfigurationAction(ConfigAction config) {

            return mConfigAction == config;
        }

        public String getSender() {
            return mSender;
        }
    }

    class StorageInfo {

        public enum StreamType {

            STREAM_STRING,          // open text stream
            STREAM_STRING_APPEND,   // open an existing stream and append to it

            TRAIN_STRING,           // for one-shot files
            TRAIN_BYTE              // for images
        }

        private final List<String> mlFolders;
        private final String mFileName;

        private final StreamType mStreamType;

        private boolean mbAppendDsRoot;

        public StorageInfo(List<String> folders, String fileName, StreamType streamType) {

            mlFolders = folders;
            mFileName = fileName;
            mStreamType = streamType;

            mbAppendDsRoot = true;
        }

        public List<String> getFolders() {
            return mlFolders;
        }

        public String getFileName() {
            return mFileName;
        }

        public boolean isAppendDsRoot() {
            return mbAppendDsRoot;
        }

        public void setAppendDsRoot(boolean state) {
            mbAppendDsRoot = state;
        }

        public boolean isStream() {
            return mStreamType == StreamType.STREAM_STRING || mStreamType == StreamType.STREAM_STRING_APPEND;
        }

        public boolean isTrain() {
            return mStreamType == StreamType.TRAIN_STRING || mStreamType == StreamType.TRAIN_BYTE;
        }

        public boolean isStreamType(StreamType type) {
            return mStreamType == type;
        }

        public StreamType getStreamType() { return mStreamType; }
    }

    class StorageConfig extends MsgConfig {

        private final StorageInfo mStorageInfo;

        public StorageConfig(ConfigAction config, String sender, StorageInfo storageInfo) {

            super(config, sender);
            mStorageInfo = storageInfo;
        }

        public StorageConfig(ConfigAction config, String sender, String chTag, StorageInfo info) {

            this(config, sender, info);
            setChTag(chTag);
        }

        public StorageInfo getStorageInfo() { return mStorageInfo; }
    }

    class MsgLogging extends MyMessage {

        public MsgLogging(String msg, String chTag) {

            super(ChannelType.LOGGING, chTag, msg);
        }

        public MsgLogging(String msg, String chTag, int targetId) {

            super(ChannelType.LOGGING, chTag, targetId, msg);
        }
    }

    // regular text data
    class MsgStorage extends MyMessage {

        private String mFileName;

        public MsgStorage(String msg, String fileName, int targetId) {

            super(ChannelType.STORAGE, null, targetId, msg);
            mFileName = fileName;
        }

        public String getFileName() {
            return mFileName;
        }

        public void setFileName(String fileName) {
            this.mFileName = fileName;
        }
    }

    class MsgSensor extends MyMessage {

        private SensorEvent mSensorEvent;

        public MsgSensor(SensorEvent event, int targetId) {

            super(ChannelType.DATA, null, targetId, toString(event));
            mSensorEvent = event;
        }

        public static String toString(SensorEvent mSensorEvent) {

            StringBuilder res = new StringBuilder(String.format(Locale.US, "%d", mSensorEvent.timestamp));

            for (int i = 0; i < mSensorEvent.values.length; i++) {
                res.append(", ").append(mSensorEvent.values[i]);
            }

            if (SDK_INT >= MySensorManager.ANDROID_VERSION_ACQ_MODE) {
                res.append(", ").append(mSensorEvent.sensor.getId());
            }

            res.append('\n');

            return res.toString();
        }

        public SensorEvent getSensorEvent() {
            return mSensorEvent;
        }

        public void setSensorEvent(SensorEvent sensorEvent) {
            this.mSensorEvent = sensorEvent;
        }
    }

    class MsgLocation extends MyMessage {

        private Location mLocEvent;

        public MsgLocation(Location locEvent, int targetId) {

            super(ChannelType.DATA, null, targetId, toString(locEvent));
            mLocEvent = locEvent;
        }

        /**
         * @param loc new location event
         * @return String("timestamp, latitude, longitude, altitude, velocity, bearing")
         */
        public static String toString(Location loc) {
            return loc.getElapsedRealtimeNanos() + ", " + loc.getLatitude() + ", " + loc.getLongitude() +
                    ", " + loc.getAltitude() + ", " + loc.getSpeed() + ", " + loc.getBearing() + '\n';
        }

        public static String getHeaderMessage() {
            return "# NOTE: timestamp is the elapsed realtime clock since last boot\n" +
                "# timestamp_ns, latitude_deg, longitude_deg, altitude_m, velocity_mps, bearing\n";
        }

        public Location getLocEvent() {
            return mLocEvent;
        }

        public void setLocEvent(Location locEvent) {
            this.mLocEvent = locEvent;
        }
    }

    class MsgGnssMeasurement extends MyMessage {

        private GnssMeasurement mMeasurement;

        public MsgGnssMeasurement(GnssMeasurement measurement, int targetId) {

            super(ChannelType.DATA, null, targetId, toString(measurement));
            mMeasurement = measurement;
        }

        public static String toString(GnssMeasurement mea) {

            if (SDK_INT < MeasurementProvider.ANDROID_GNSS_API_VERSION) {
                return "";
            }

            String biasInterSig = "";
            if (SDK_INT >= MyLocationManager.ANDROID_GNSS_INTER_SIG_BIAS_VERSION) {
                biasInterSig = ", " + mea.getSatelliteInterSignalBiasNanos();
            }

            String typeCode = "";
            if (SDK_INT >= MyLocationManager.ANDROID_GNSS_TYPE_CODE_VERSION) {
                typeCode = ", " + mea.getCodeType();
            }

            return SystemClock.elapsedRealtimeNanos() + ", " + mea.getTimeOffsetNanos() + ", " +
                    mea.getReceivedSvTimeNanos() + ", " + mea.getAccumulatedDeltaRangeMeters() + ", " +
                    mea.getPseudorangeRateMetersPerSecond() + ", " + mea.getCn0DbHz() + ", " +
                    mea.getSnrInDb() + ", " + mea.getCarrierFrequencyHz() + ", " +
                    mea.getCarrierCycles() + ", " + mea.getCarrierPhase() + ", " +
                    mea.getSvid() + ", " + mea.getConstellationType() + biasInterSig + typeCode + "\n";
        }

        public static String getHeaderMessage() {

            return "# timestamp_ns, time_offset_ns, rx_sv_time_ns, acc_delta_range_m," +
                    " ps_range_rate_mps, cn0_DbHz, snr_db, cr_freq_hz, cr_cycles, cr_phase, sv_id," +
                    " const_type, [bias_inter_signal_ns], [type_code]\n";
        }

        public GnssMeasurement getGnssMeasurement() {
            return mMeasurement;
        }

        public void setGnssMeasurement(GnssMeasurement mMeasurement) {
            this.mMeasurement = mMeasurement;
        }
    }

    class MsgGnssNavigation extends MyMessage {

        private GnssNavigationMessage mNavMessage;

        public MsgGnssNavigation(GnssNavigationMessage message, int targetId) {

            super(ChannelType.DATA, null, targetId, toString(message));
            mNavMessage = message;
        }

        public static String toString(GnssNavigationMessage nav) {

            if (SDK_INT < MeasurementProvider.ANDROID_GNSS_API_VERSION) {
                return "";
            }

            return SystemClock.elapsedRealtimeNanos() + ", " + nav.getSvid() + ", " +
                    nav.getType() + ", " + nav.getMessageId() + ", " +
                    nav.getSubmessageId() + ", " + bytesToHex(nav.getData()) + "\n";
        }

        public static String getHeaderMessage() {

            return "# timestamp_ns, sv_id, nav_type, msg_id, sub_msg_id, data_bytes_hex\n";
        }

        public GnssNavigationMessage getNavMessage() {
            return mNavMessage;
        }

        public void setNavMessage(GnssNavigationMessage mNavMessage) {
            this.mNavMessage = mNavMessage;
        }

        private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();
        public static String bytesToHex(byte[] bytes) {
            char[] hexChars = new char[bytes.length * 2];
            for (int j = 0; j < bytes.length; j++) {
                int v = bytes[j] & 0xFF;
                hexChars[j * 2] = HEX_ARRAY[v >>> 4];
                hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
            }
            return new String(hexChars);
        }
    }

    // acts as both string file (<ts, image> pairs) and image
    class MsgImage extends MyMessage {

        private final Image mImage;
        private String mFileName;

        private byte[] mData;
        private CaptureResult mCaptureResult;
        private CameraCharacteristics mCharacteristics;

        public MsgImage(Image image, String fileName, int targetId) {

            super(ChannelType.DATA, null, targetId, toString(image, fileName));
            mImage = image;
            mFileName = fileName;
        }

        private static String toString(Image image, String fileName) {

            return image.getTimestamp() + ", " + fileName + "\n";
        }

        public static String getHeaderMessage() {

            return "# timestamp_ns, image_file_name\n";
        }

        public String getFileName() {
            return mFileName;
        }

        public void setFileName(String fileName) {
            this.mFileName = fileName;
        }

        public Image getImage() { return mImage; }

        public byte[] getData() { return mData; }

        public void setData(byte[] data) { mData = data; }

        public CaptureResult getCaptureResult() {
            return mCaptureResult;
        }

        public void setCaptureResult(CaptureResult captureResult) {
            this.mCaptureResult = captureResult;
        }

        public CameraCharacteristics getCharacteristics() {
            return mCharacteristics;
        }

        public void setCharacteristics(CameraCharacteristics characteristics) {
            this.mCharacteristics = characteristics;
        }
    }

    class MsgUsb extends MyMessage {

        public enum UsbDataType {
            TYPE_ADC,
            TYPE_INFO
        }

        // groups of actions (command flag)
        public enum UsbCommand {
            CMD_BROADCAST,
            CMD_UPDATE_OUTPUT,
            CMD_GET_SENSOR_INFO,
            CMD_GET_CMD_RES,
            CMD_ADC_START,
            CMD_ADC_READ,
            CMD_ADC_STOP,
            // can even define 2 cmds for test request and response
            CMD_RUN_TEST,
        };

        // true: command, false: data
        private boolean mbIsCommand;

        // raw in/out buffer
        private byte[] mRawBuffer;

        // all commands have two parts: flag and data (rawBuffer)
        private UsbCommand mCmdFlag;
        private int mCmdFlagOverride = 0;

        private UsbDataType mDataType;

        private long mTimestamp;
        private int[] mAdcData;

        private MyUsbInfo mUsbInfo;

        public static class MyControlTransferInfo {

            public static final int DEF_CTRL_TRANS_TIMEOUT = 5000;

            // true: out message, false in message
            public int mCtrlTransDir;
            public int mCtrlMsgIndex = 0;
            public int mCtrlMsgValue = 0;
            public int mCtrlTransType; // vendor, standard, ...
            public int mCtrlTransTimeout = DEF_CTRL_TRANS_TIMEOUT;
        }

        private MyControlTransferInfo mCtrlTransInfo;

        public MsgUsb() {

            super(ChannelType.DATA);
        }

        public MsgUsb(int targetId) {

            super(ChannelType.DATA, null, targetId, "");
        }

        public MyControlTransferInfo getCtrlTransInfo() {
            return mCtrlTransInfo;
        }

        public void setCtrlTransInfo(MyControlTransferInfo ctrlTransInfo) {
            this.mCtrlTransInfo = ctrlTransInfo;
        }

        public boolean isIsCommand() {
            return mbIsCommand;
        }

        public void setIsCommand(boolean state) {
            this.mbIsCommand = state;
        }

        public byte[] getRawBuffer() {
            return mRawBuffer;
        }

        public void setRawBuffer(byte[] rawBuffer) {
            this.mRawBuffer = rawBuffer;
        }

        public UsbCommand getCmd() {
            return mCmdFlag;
        }
        public int getCmdFlag() {
            if (mCmdFlag != null) {
                return mCmdFlag.ordinal();
            }
            return mCmdFlagOverride;
        }

        public void setCmdFlag(int cmdFlagOverride) {
            this.mCmdFlagOverride = cmdFlagOverride;
        }
        public void setCmd(UsbCommand cmd) {
            this.mCmdFlag = cmd;
        }

        public UsbDataType getDataType() {
            return mDataType;
        }

        public void setDataType(UsbDataType usbDataType) {
            this.mDataType = usbDataType;
        }

        public long getTimestamp() {
            return mTimestamp;
        }

        public void setTimestamp(long timestamp) {
            this.mTimestamp = timestamp;
        }

        public int[] getAdcData() {
            return mAdcData;
        }

        public void setAdcData(int[] adcData) {
            this.mAdcData = adcData;
        }

        public MyUsbInfo getUsbInfo() {
            return mUsbInfo;
        }

        public void setUsbInfo(MyUsbInfo usbInfo) {
            this.mUsbInfo = usbInfo;
        }

        public String getAdcSensorString() {

            if (mAdcData == null) {
                return "";
            }

            StringBuilder sb = new StringBuilder();
            sb.append(mTimestamp);

            for (int adcVal : mAdcData) {
                sb.append(", ").append(adcVal);
            }
            sb.append("\n");

            return sb.toString();
        }
    }

    class MyUsbInfo {

        public boolean isAdcAvailable;
        public boolean isControlAvailable;
        public boolean isAdcStarted;

        public int adcResolution;
        public double adcSampleRate;
        public int numAdcChannels;

        // info extracted from descriptors
        public String manufacturerName;
        public String productName;
    }

    class MsgWireless extends MyMessage {

        public enum WirelessCommand {
            BROADCAST,
            CMD_DIR,
            CMD_CHAR,
            CMD_WORD,
            SENSOR,
            TEST,
            CHAT
        }

        private final WirelessCommand mCmd;

        public MsgWireless(WirelessCommand cmd, String msg) {

            super(ChannelType.DATA, null, msg);
            mCmd = cmd;
        }

        public WirelessCommand getCmd() { return mCmd; }
    }

}
