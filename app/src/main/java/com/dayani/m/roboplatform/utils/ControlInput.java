package com.dayani.m.roboplatform.utils;

public class ControlInput {

    private byte[] sensorInput;
    private String keyboardInput;

    public byte[] getSensorInput() {
        return sensorInput;
    }

    public String getKeyboardInput() {
        return keyboardInput;
    }

    public void setSensorInput(byte[] sensorInput) {
        this.sensorInput = sensorInput;
    }

    public void setKeyboardInput(String keyboardInput) {
        this.keyboardInput = keyboardInput;
    }

    ControlInput(byte[] sensorInput, String keyboardCmd) {
        this.sensorInput = sensorInput;
        this.keyboardInput = keyboardCmd;
    }

    public ControlInput() {
        sensorInput = new byte[64];
        keyboardInput = "x";
    }
}
