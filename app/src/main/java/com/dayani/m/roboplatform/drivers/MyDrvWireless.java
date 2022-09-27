package com.dayani.m.roboplatform.drivers;

import android.util.Log;

import com.dayani.m.roboplatform.utils.interfaces.MyMessages.MsgWireless;
import com.dayani.m.roboplatform.utils.interfaces.MyMessages.MsgUsb;
import com.dayani.m.roboplatform.utils.interfaces.MyMessages.MsgWireless.WirelessCommand;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MyDrvWireless {

    private static final String TAG = MyDrvWireless.class.getSimpleName();

    public static final String DEFAULT_TEST_COMMAND = "wl-8749";
    public static final String DEFAULT_TEST_RESPONSE = "wl-0462";

    private static final String CMD_SEPARATOR_CHAR = "#";

    public static String encodeMessage(MsgWireless msg) {

        return cmdToString(msg.getCmd())+CMD_SEPARATOR_CHAR+ msg;
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
                return "sensor";
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
                return WirelessCommand.SENSOR;
            case "chat":
                return WirelessCommand.CHAT;
            case "misc":
            default:
                return WirelessCommand.BROADCAST;
        }
    }
}
