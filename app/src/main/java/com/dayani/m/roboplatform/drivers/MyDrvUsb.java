package com.dayani.m.roboplatform.drivers;

import android.hardware.usb.UsbConstants;

import com.dayani.m.roboplatform.utils.interfaces.MyMessages.MsgUsb;
import com.dayani.m.roboplatform.utils.interfaces.MyMessages.MsgWireless;
import com.dayani.m.roboplatform.utils.interfaces.MyMessages.MsgWireless.WirelessCommand;
import com.dayani.m.roboplatform.utils.interfaces.MyMessages.MsgUsb.MyControlTransferInfo;
import com.dayani.m.roboplatform.utils.interfaces.MyMessages.MsgUsb.UsbCommand;
import com.dayani.m.roboplatform.utils.interfaces.MyMessages.MyUsbInfo;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;


public class MyDrvUsb {

    private static final int TARGET_STATE_BUFFER_BYTES = 8;

    public static final int STD_USB_REQUEST_GET_DESCRIPTOR = 0x06;
    // http://libusb.sourceforge.net/api-1.0/group__desc.html
    protected static final int LIB_USB_DT_STRING = 0x03;

    // output command message
    public static MsgUsb getCommandMessage(String command) {

        MsgUsb usbMsg = new MsgUsb();

        MyControlTransferInfo ctrlTransInfo = new MsgUsb.MyControlTransferInfo();
        ctrlTransInfo.mCtrlTransDir = UsbConstants.USB_DIR_OUT; // _OUT for write
        ctrlTransInfo.mCtrlTransType = UsbConstants.USB_TYPE_VENDOR;

        usbMsg.setCtrlTransInfo(ctrlTransInfo);

        // set the buffer
        byte[] cmdBytesEncoded = encodeUsbCommand(command);
        usbMsg.setRawBuffer(cmdBytesEncoded);

        return usbMsg;
    }

    public static MsgUsb getCommandMessage(UsbCommand cmdFlag, String cmdData) {

        MsgUsb usgMsg = getCommandMessage(cmdData);
        usgMsg.setCmd(cmdFlag);

        return usgMsg;
    }

    public static MsgUsb getCommandMessage(UsbCmdInterpreter it, String command) {

        MsgUsb usbMsg = getCommandMessage(command);

        byte[] cmdBytes = it.interpret(command);
        byte[] cmdBytesEncoded = encodeUsbCommand(cmdBytes);
        usbMsg.setRawBuffer(cmdBytesEncoded);

        return usbMsg;
    }

    public static MsgUsb getCommandMessage(byte[] rawInput) {

        MsgUsb usbMsg = getCommandMessage("0");
        usbMsg.setRawBuffer(rawInput);
        return usbMsg;
    }

    public static MsgUsb getInputMessage(UsbCommand cmdFlag, byte[] inputBuffer) {

        MsgUsb usbMsg = new MsgUsb();
        MyControlTransferInfo ctrlTransInfo = new MyControlTransferInfo();

        ctrlTransInfo.mCtrlTransDir = UsbConstants.USB_DIR_IN; // _IN for read
        ctrlTransInfo.mCtrlTransType = UsbConstants.USB_TYPE_VENDOR;

        usbMsg.setCtrlTransInfo(ctrlTransInfo);
        usbMsg.setCmd(cmdFlag);
        usbMsg.setRawBuffer(inputBuffer);

        return usbMsg;
    }

    public static MsgUsb getUsbDescriptorQueryMessage(byte[] inputBuffer, int value, int index, int timeout) {

        MsgUsb usbMsg = getInputMessage(null, inputBuffer);

        MyControlTransferInfo ctrlTransInfo = usbMsg.getCtrlTransInfo();

        ctrlTransInfo.mCtrlTransType = UsbConstants.USB_TYPE_STANDARD;
        ctrlTransInfo.mCtrlTransTimeout = timeout;
        ctrlTransInfo.mCtrlMsgValue = (LIB_USB_DT_STRING << 8) | value;
        ctrlTransInfo.mCtrlMsgIndex = index;

        usbMsg.setCmdFlag(STD_USB_REQUEST_GET_DESCRIPTOR);
        usbMsg.setCtrlTransInfo(ctrlTransInfo);

        return usbMsg;
    }

    public static MsgUsb wirelessToUsb(MsgWireless msg) {

        if (msg == null) {
            return null;
        }

        WirelessCommand cmd = msg.getCmd();

        if (WirelessCommand.CMD_CHAR.equals(cmd) ||
                WirelessCommand.CMD_DIR.equals(cmd)) {

            MsgUsb usbMsg = getCommandMessage(UsbCommand.CMD_UPDATE_OUTPUT, msg.toString());

            byte[] decodedCmd = getWlCmdEncodedByteMap(msg.toString());

            if (decodedCmd.length >= 3) {
                usbMsg.setRawBuffer(decodedCmd);
                usbMsg.getCtrlTransInfo().mCtrlMsgValue = decodedCmd[2];
            }

            return usbMsg;
        }

        return null;
    }


    public static int[] decodeAdcSensorMsg(byte[] rawBuff) {

        if (rawBuff == null || rawBuff.length < 2) {
            return null;
        }

        // first byte is the length of the data
        byte dataLength = rawBuff[0];
        // 0: command, 1: data
        byte cmdOrData = rawBuff[1];

        if (dataLength + 2 > rawBuff.length || cmdOrData != 0) {
            return null;
        }

        // two bytes for each adc reading
        int adcOutLen = dataLength/2;
        int[] adcOutArr = new int[adcOutLen];

        for (int i = 0; i < dataLength; i += 2) {

            adcOutArr[i/2] = getAdcInt(rawBuff[i+3], rawBuff[i+2]);
        }

        return adcOutArr;
    }

    public static byte[] encodeUsbCommand(String cmd) {

        int cmdBytesLen = 2;
        byte[] cmdBytes = new byte[cmdBytesLen];
        if (cmd != null && !cmd.isEmpty()) {
            cmdBytes = cmd.getBytes(StandardCharsets.US_ASCII);
            cmdBytesLen = cmdBytes.length;
        }
        //byte[] cmdTruncatedBytes = truncateByteArray(cmdBytes);
        byte[] output = new byte[cmdBytesLen+2];

        output[0] = (byte) cmdBytesLen;
        output[1] = 0;

        if (cmdBytesLen > 2) {
            System.arraycopy(cmdBytes, 0, output, 2, output.length - 2);
        }

        return output;
    }

    public static byte[] encodeUsbCommand(byte[] cmd) {

        int cmdBytesLen = 2;
        byte[] cmdBytes = new byte[cmdBytesLen];
        if (cmd != null && cmd.length > 0) {
            cmdBytes = cmd;
            cmdBytesLen = cmdBytes.length;
        }
        //byte[] cmdTruncatedBytes = truncateByteArray(cmdBytes);
        byte[] output = new byte[cmdBytesLen+2];

        output[0] = (byte) cmdBytesLen;
        output[1] = 1;

        if (cmdBytesLen > 2) {
            System.arraycopy(cmdBytes, 0, output, 2, output.length - 2);
        }

        return output;
    }

    public static byte[] decodeUsbCommand(byte[] rawInput) {

        if (rawInput == null || rawInput.length < 2) {
            return null;
        }

        int dataLength = rawInput[0];
        int cmdOrData = rawInput[1];

        if (dataLength + 2 > rawInput.length || cmdOrData != 0) {
            return null;
        }

        return Arrays.copyOfRange(rawInput, 2, 2+dataLength);
    }

    public static String decodeUsbCommandStr(byte[] rawInput) {
        byte[] decodedMsg = decodeUsbCommand(rawInput);
        if (decodedMsg != null && decodedMsg.length > 0) {
            return new String(decodedMsg, StandardCharsets.US_ASCII);
        }
        return "";
    }

    public static void decodeUsbSensorConfigInfo(MsgUsb usbMsg) {

        if (usbMsg == null) {
            return;
        }

        // get raw input buffer
        byte[] rawBuff = usbMsg.getRawBuffer();

        if (rawBuff == null || rawBuff.length < 2) {
            return;
        }

        int dataLength = rawBuff[0];
        int cmdOrData = rawBuff[1];

        if (dataLength + 2 > rawBuff.length || cmdOrData != 0) {
            return;
        }

        MyUsbInfo usbInfo = new MyUsbInfo();

        byte state = rawBuff[2];

        usbInfo.isAdcAvailable = (state & 0x01) == 0x01;
        // second bit is device's external sensors
        usbInfo.isControlAvailable = (state & 0x04) >> 2 == 0x01;
        usbInfo.isAdcStarted = (state & 0x08) >> 3 == 0x01;

        usbInfo.numAdcChannels = rawBuff[3];
        int srcFreqMHz = rawBuff[4] & 0xFF;
        int adcPreScaler = rawBuff[5] & 0xFF;
        usbInfo.adcSampleRate = (srcFreqMHz * 1e6) / adcPreScaler;
        usbInfo.adcResolution = rawBuff[6];

        usbMsg.setUsbInfo(usbInfo);
    }

    public static String decodeUsbDescriptorInfo(MsgUsb usbMsg) {

        if (usbMsg == null) {
            return null;
        }

        byte[] inputBuffer = usbMsg.getRawBuffer();
        // todo
        int rdo = inputBuffer.length;

        String descField = new String(inputBuffer, 2, rdo - 2, StandardCharsets.UTF_16LE);

        return descField;
    }

    public interface UsbCmdInterpreter {
        byte[] interpret(String msg);
    }

    public static byte[] getWlCmdEncodedByteMap(String command) {

        // set pins of an 8 pin port
        byte[] output = new byte[3];
        output[0] = 1;

        // 6-DoF command
        switch (command.toLowerCase(Locale.ROOT)) {
            case "w":
            case "up": // forward
                output[2] |= 0x01;
                break;
            case "s":
            case "down": // backward
                output[2] |= 0x02;
                break;
            case "d":
            case "right":
                output[2] |= 0x04;
                break;
            case "a":
            case "left":
                output[2] |= 0x08;
                break;
            case "q":
                //case "up":
                output[2] |= 0x10;
                break;
            case "e":
                //case "down":
                output[2] |= 0x20;
                break;
            case "r":
                output[2] |= 0x40;
                break;
            case "f":
                output[2] |= 0x80;
                break;
            default:
                //output[2] &= 0x00;
                break;
        }

        return output;
    }


    public static byte[] truncateByteArray(byte[] inputArr) {
        return Arrays.copyOfRange(inputArr, 0, TARGET_STATE_BUFFER_BYTES);
    }

    /**
     * Methods to work with byte sent and received.
     * @param value double value to be converted
     * @return byte array representing the value
     */
    public static byte[] toByteArray(double value) {
        byte[] bytes = new byte[8];
        ByteBuffer.wrap(bytes).putDouble(value);
        return bytes;
    }

    /**
     * Error: there's a limit on input to byte array size when
     *      sent to ByteBuffer.wrap method (lim = 8);
     *      Only work with this when have byte arr returned from
     *      #toByteArray().
     * @param bytes byte array containing a double number
     * @param offset the start of double
     * @param length the length of double bytes
     * @return the converted double number
     */
    public static double toDouble(byte[] bytes, int offset, int length) {
        //byte[] slice = Arrays.copyOfRange(bytes,offset,offset+length);
        return ByteBuffer.wrap(bytes).getDouble();
    }

    public static byte[] getBufferSlice(byte[] inArray, int offset, int length, int outSize) {
        byte[] outBuff = new byte[outSize];
        for (int i = 0; i < outSize; i++) {
            if (i >= length) {
                outBuff[i] = 0;
            }
            else {
                outBuff[i] = inArray[i+offset];
            }
        }
        return outBuff;
    }

    /**
     * For raw custom byte array, use a custom method like this.
     * @param high the high byte of integer number
     * @param low the low byte of integer number
     * @return the converted int
     */
    public static int toInteger(byte high, byte low) {
        return high*256+low;
    }

    public String getASCIIMessage(byte[] buff) {
        return new String(buff, 0, buff.length, StandardCharsets.US_ASCII);
    }

    /**
     * Assumes every 2 byte in buffer byte array is
     * a double number and there are at most 8 numbers.
     * @param buff (of size at least 16 bytes)
     * @return String representing the doubles
     */
    public String getDoublesString(byte[] buff) {
        StringBuilder msg = new StringBuilder();
        String mark = ", ";
        for (int i = 0; i < 8; i++) {
            if (i == 7) {
                mark = "\n";
            }
            msg.append(toInteger(buff[2 * i + 1], buff[2 * i])).append(mark);
        }
        return msg.toString();
    }

    /**
     *
     * @param msg String message
     * @return encoded message (with time & ...)
     */
    public String getSensorString(String msg) {
        return "USB_" +
                new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSSSS", Locale.US).format(new Date()) +
                ", " + msg;
    }

    public static int getAdcInt(byte high, byte low) {

        int highInt = (high & 0xFF);
        int lowInt = (low & 0xFF);
        return highInt * 256 + lowInt;
    }
}