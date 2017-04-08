package com.lqr.audio;

public class IAudioRecordEvent {
    public static final int AUDIO_RECORD_EVENT_TRIGGER = 1;
    public static final int AUDIO_RECORD_EVENT_SAMPLING = 2;
    public static final int AUDIO_RECORD_EVENT_WILL_CANCEL = 3;
    public static final int AUDIO_RECORD_EVENT_CONTINUE = 4;
    public static final int AUDIO_RECORD_EVENT_RELEASE = 5;
    public static final int AUDIO_RECORD_EVENT_ABORT = 6;
    public static final int AUDIO_RECORD_EVENT_TIME_OUT = 7;
    public static final int AUDIO_RECORD_EVENT_TICKER = 8;
    public static final int AUDIO_RECORD_EVENT_SEND_FILE = 9;

    public IAudioRecordEvent() {
    }
}
