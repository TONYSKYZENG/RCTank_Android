package com.example.rctank;

public interface UICommunicatorIm {
    String getAddressEditText();
    void updateAddressEditText(String text);
    String getPortEditText();
    void updatePortEditText(String text);
    String getMessageEditText();
    void updateMessageEditText(String text);
    void onSwitchToggle(boolean isCheck);
    void onButtonClick();
    void updateLogTextView(CharSequence text);
}
