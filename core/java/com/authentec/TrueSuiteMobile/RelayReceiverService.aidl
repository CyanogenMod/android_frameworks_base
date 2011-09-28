package com.authentec.TrueSuiteMobile;
interface RelayReceiverService
{
    void sendCommand(String command, String args);
    String receiveEvent();
    void quit();
}
