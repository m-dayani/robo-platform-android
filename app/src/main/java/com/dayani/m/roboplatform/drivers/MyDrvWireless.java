package com.dayani.m.roboplatform.drivers;

import android.util.Log;

import com.dayani.m.roboplatform.utils.interfaces.MyMessages.MsgWireless;
import com.dayani.m.roboplatform.utils.interfaces.MyMessages.MsgUsb;
import com.dayani.m.roboplatform.utils.interfaces.MyMessages.MsgWireless.WirelessCommand;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MyDrvWireless {

    private static final String TAG = MyDrvWireless.class.getSimpleName();

    public static final String DEFAULT_TEST_COMMAND = "wl-8749";
    public static final String DEFAULT_TEST_RESPONSE = "wl-0462";

    private static final String CMD_SEPARATOR_CHAR = "#";

    public static String encodeMessage(MsgWireless msg) {

        char sep_char = CMD_SEPARATOR_CHAR.charAt(0);
        char end_char = '\n';
        if (msg.getCmd() == WirelessCommand.SENSOR) {
            sep_char = ':';
            end_char = ';';
        }

        return cmdToString(msg.getCmd())+sep_char+msg+end_char;
    }

    public static byte[] encodeMessageBytes(MsgWireless msg) {

        char sep_char = CMD_SEPARATOR_CHAR.charAt(0);
        char end_char = '\n';
        if (msg.getCmd() == WirelessCommand.SENSOR) {
            sep_char = ':';
            end_char = ';';
        }

        String dataHead = cmdToString(msg.getCmd())+sep_char;
        byte[] msgBytes = msg.getData();
//        if (msg.getCmd() == WirelessCommand.SENSOR) {
//            dataHead += (msgBytes.length + ",0,0:");
//        }
        byte[] headBytes = dataHead.getBytes(StandardCharsets.US_ASCII);
        int lenOut = headBytes.length + msgBytes.length + 1;
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            outputStream.write(headBytes);
            outputStream.write(msgBytes);
            outputStream.write(end_char);
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }

        return outputStream.toByteArray();
    }

    public static MsgWireless decodeMessage(String msg) {

        MsgWireless wlMsg = new MsgWireless(WirelessCommand.BROADCAST, "");
        String[] msgParts = msg.split(CMD_SEPARATOR_CHAR);

        if (msgParts.length < 2) {
            return wlMsg;
        }

        return new MsgWireless(stringToCmd(msgParts[0]), msgParts[1]);
    }

    public static boolean matchesTestRequest(MsgWireless msg) {

        return msg != null && WirelessCommand.TEST.equals(msg.getCmd()) && DEFAULT_TEST_COMMAND.matches(msg.toString());
    }

    public static boolean matchesTestResponse(MsgWireless msg) {

        return msg != null && WirelessCommand.TEST.equals(msg.getCmd()) && DEFAULT_TEST_RESPONSE.matches(msg.toString());
    }

    public static String getTestRequest() {

        return encodeMessage(new MsgWireless(WirelessCommand.TEST, DEFAULT_TEST_COMMAND));
    }

    public static MsgWireless wirelessToUsb(MsgUsb msg) {

        // todo
        return new MsgWireless(WirelessCommand.SENSOR, msg.getAdcSensorString());
    }

    public static String cmdToString(WirelessCommand cmd) {

        switch (cmd) {
            case CMD_DIR:
                return "dir";
            case CMD_CHAR:
                return "char";
            case CMD_WORD:
                return "word";
            case TEST:
                return "test";
            case SENSOR:
                return "data";
            case CHAT:
                return "chat";
            case BROADCAST:
            default:
                return "misc";
        }
    }

    public static WirelessCommand stringToCmd(String cmd) {

        switch (cmd) {
            case "dir":
                return WirelessCommand.CMD_DIR;
            case "char":
                return WirelessCommand.CMD_CHAR;
            case "word":
                return WirelessCommand.CMD_WORD;
            case "test":
                return WirelessCommand.TEST;
            case "sensor":
            case "data":
                return WirelessCommand.SENSOR;
            case "chat":
                return WirelessCommand.CHAT;
            case "misc":
            default:
                return WirelessCommand.BROADCAST;
        }
    }
}
