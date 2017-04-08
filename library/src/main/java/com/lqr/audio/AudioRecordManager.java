package com.lqr.audio;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Resources;
import android.media.AudioManager;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

import java.io.File;

public class AudioRecordManager implements Handler.Callback {
    private static final String TAG = "LQR_AudioRecordManager";
    private int RECORD_INTERVAL;
    private String SAVE_PATH;
    private IAudioState mCurAudioState;
    private Context mContext;
    private Handler mHandler;
    private AudioManager mAudioManager;
    private MediaRecorder mMediaRecorder;
    private Uri mAudioPath;
    private long smStartRecTime;
    private AudioManager.OnAudioFocusChangeListener mAfChangeListener;
    IAudioState idleState;
    IAudioState recordState;
    IAudioState sendingState;
    IAudioState cancelState;
    IAudioState timerState;
    private IAudioRecordListener mAudioRecordListener;

    public static AudioRecordManager mInstance;

    public static AudioRecordManager getInstance(Context context) {
        if (mInstance == null) {
            synchronized (AudioRecordManager.class) {
                if (mInstance == null) {
                    mInstance = new AudioRecordManager(context);
                }
            }
        }
        return mInstance;
    }

    @TargetApi(21)
    private AudioRecordManager(Context context) {
        this.mContext = context;
        this.mHandler = new Handler(this);
        this.RECORD_INTERVAL = 60;
        this.idleState = new AudioRecordManager.IdleState();
        this.recordState = new AudioRecordManager.RecordState();
        this.sendingState = new AudioRecordManager.SendingState();
        this.cancelState = new AudioRecordManager.CancelState();
        this.timerState = new AudioRecordManager.TimerState();
        if (Build.VERSION.SDK_INT < 21) {
            try {
                TelephonyManager e = (TelephonyManager) this.mContext.getSystemService(Context.TELEPHONY_SERVICE);
                e.listen(new PhoneStateListener() {
                    public void onCallStateChanged(int state, String incomingNumber) {
                        switch (state) {
                            case 1:
                                AudioRecordManager.this.sendEmptyMessage(6);
                            case 0:
                            case 2:
                            default:
                                super.onCallStateChanged(state, incomingNumber);
                        }
                    }
                }, 32);
            } catch (SecurityException var2) {
                var2.printStackTrace();
            }
        }

        this.mCurAudioState = this.idleState;
        this.idleState.enter();
    }

    public final boolean handleMessage(Message msg) {
        Log.i(TAG, "handleMessage " + msg.what);
        AudioStateMessage m;
        switch (msg.what) {
            case 2:
                this.sendEmptyMessage(2);
                break;
            case 7:
                m = AudioStateMessage.obtain();
                m.what = msg.what;
                m.obj = msg.obj;
                this.sendMessage(m);
                break;
            case 8:
                m = AudioStateMessage.obtain();
                m.what = 7;
                m.obj = msg.obj;
                this.sendMessage(m);
        }

        return false;
    }

    private void initView() {
        if (mAudioRecordListener != null) {
            mAudioRecordListener.initTipView();
        }
    }

    private void setTimeoutView(int counter) {
        if (mAudioRecordListener != null) {
            mAudioRecordListener.setTimeoutTipView(counter);
        }
    }

    private void setRecordingView() {
        if (mAudioRecordListener != null) {
            mAudioRecordListener.setRecordingTipView();
        }
    }

    private void setCancelView() {
        if (mAudioRecordListener != null) {
            mAudioRecordListener.setCancelTipView();
        }
    }

    private void destroyView() {
        Log.d(TAG, "destroyTipView");
        this.mHandler.removeMessages(7);
        this.mHandler.removeMessages(8);
        this.mHandler.removeMessages(2);
        if (mAudioRecordListener != null) {
            mAudioRecordListener.destroyTipView();
        }
    }

    public void setMaxVoiceDuration(int maxVoiceDuration) {
        this.RECORD_INTERVAL = maxVoiceDuration;
    }

    public void setAudioSavePath(String path) {
        if (TextUtils.isEmpty(path)) {
            this.SAVE_PATH = mContext.getCacheDir().getAbsolutePath();
        } else {
            this.SAVE_PATH = path;
        }
    }

    public int getMaxVoiceDuration() {
        return this.RECORD_INTERVAL;
    }

    public void startRecord() {
        this.mAudioManager = (AudioManager) this.mContext.getSystemService(Context.AUDIO_SERVICE);
        if (this.mAfChangeListener != null) {
            this.mAudioManager.abandonAudioFocus(this.mAfChangeListener);
            this.mAfChangeListener = null;
        }

        this.mAfChangeListener = new AudioManager.OnAudioFocusChangeListener() {
            public void onAudioFocusChange(int focusChange) {
                Log.d(TAG, "OnAudioFocusChangeListener " + focusChange);
                if (focusChange == -1) {
                    AudioRecordManager.this.mAudioManager.abandonAudioFocus(AudioRecordManager.this.mAfChangeListener);
                    AudioRecordManager.this.mAfChangeListener = null;
                    AudioRecordManager.this.sendEmptyMessage(6);
                }

            }
        };
        this.sendEmptyMessage(1);
        if (mAudioRecordListener != null) {
            mAudioRecordListener.onStartRecord();
        }
    }

    public void willCancelRecord() {
        this.sendEmptyMessage(3);
    }

    public void continueRecord() {
        this.sendEmptyMessage(4);
    }

    public void stopRecord() {
        this.sendEmptyMessage(5);
    }

    public void destroyRecord() {
        AudioStateMessage msg = new AudioStateMessage();
        msg.obj = Boolean.valueOf(true);
        msg.what = 5;
        this.sendMessage(msg);
    }

    void sendMessage(AudioStateMessage message) {
        this.mCurAudioState.handleMessage(message);
    }

    void sendEmptyMessage(int event) {
        AudioStateMessage message = AudioStateMessage.obtain();
        message.what = event;
        this.mCurAudioState.handleMessage(message);
    }

    private void startRec() {
        Log.d(TAG, "startRec");

        try {
            this.muteAudioFocus(this.mAudioManager, true);
            this.mAudioManager.setMode(0);
            this.mMediaRecorder = new MediaRecorder();

            try {
//                Resources e = this.mContext.getResources();
//                int bps = e.getInteger(e.getIdentifier("rc_audio_encoding_bit_rate", "integer", this.mContext.getPackageName()));
                int bps = 7950;
                this.mMediaRecorder.setAudioSamplingRate(8000);
                this.mMediaRecorder.setAudioEncodingBitRate(bps);
            } catch (Resources.NotFoundException var3) {
                var3.printStackTrace();
            }

            this.mMediaRecorder.setAudioChannels(1);
            this.mMediaRecorder.setAudioSource(1);
            this.mMediaRecorder.setOutputFormat(3);
            this.mMediaRecorder.setAudioEncoder(1);
            this.mAudioPath = Uri.fromFile(new File(SAVE_PATH, System.currentTimeMillis() + "temp.voice"));
            this.mMediaRecorder.setOutputFile(this.mAudioPath.getPath());
            this.mMediaRecorder.prepare();
            this.mMediaRecorder.start();
            Message e1 = Message.obtain();
            e1.what = 7;
            e1.obj = Integer.valueOf(10);
            this.mHandler.sendMessageDelayed(e1, (long) (this.RECORD_INTERVAL * 1000 - 10000));
        } catch (Exception var4) {
            var4.printStackTrace();
        }

    }

    private boolean checkAudioTimeLength() {
        long delta = SystemClock.elapsedRealtime() - this.smStartRecTime;
        return delta < 1000L;
    }

    private void stopRec() {
        Log.d(TAG, "stopRec");

        try {
            this.muteAudioFocus(this.mAudioManager, false);
            if (this.mMediaRecorder != null) {
                this.mMediaRecorder.stop();
                this.mMediaRecorder.release();
                this.mMediaRecorder = null;
            }
        } catch (Exception var2) {
            var2.printStackTrace();
        }

    }

    private void deleteAudioFile() {
        Log.d(TAG, "deleteAudioFile");
        if (this.mAudioPath != null) {
            File file = new File(this.mAudioPath.getPath());
            if (file.exists()) {
                file.delete();
            }
        }

    }

    private void finishRecord() {
        Log.d(TAG, "finishRecord path = " + this.mAudioPath);
        if (mAudioRecordListener != null) {
            int duration = (int) (SystemClock.elapsedRealtime() - this.smStartRecTime) / 1000;
            mAudioRecordListener.onFinish(this.mAudioPath, duration);
        }
    }

    private void audioDBChanged() {
        if (this.mMediaRecorder != null) {
            int db = this.mMediaRecorder.getMaxAmplitude() / 600;
            if (mAudioRecordListener != null) {
                mAudioRecordListener.onAudioDBChanged(db);
            }
        }

    }

    private void muteAudioFocus(AudioManager audioManager, boolean bMute) {
        if (Build.VERSION.SDK_INT < 8) {
            Log.d(TAG, "muteAudioFocus Android 2.1 and below can not stop music");
        } else {
            if (bMute) {
                audioManager.requestAudioFocus(this.mAfChangeListener, 3, 2);
            } else {
                audioManager.abandonAudioFocus(this.mAfChangeListener);
                this.mAfChangeListener = null;
            }

        }
    }

    class TimerState extends IAudioState {
        TimerState() {
        }

        void handleMessage(AudioStateMessage msg) {
            Log.d(TAG, this.getClass().getSimpleName() + " handleMessage : " + msg.what);
            switch (msg.what) {
                case 3:
                    AudioRecordManager.this.setCancelView();
                    AudioRecordManager.this.mCurAudioState = AudioRecordManager.this.cancelState;
                case 4:
                default:
                    break;
                case 5:
                    AudioRecordManager.this.mHandler.postDelayed(new Runnable() {
                        public void run() {
                            AudioRecordManager.this.stopRec();
                            AudioRecordManager.this.finishRecord();
                            AudioRecordManager.this.destroyView();
                        }
                    }, 500L);
                    AudioRecordManager.this.mCurAudioState = AudioRecordManager.this.idleState;
                    AudioRecordManager.this.idleState.enter();
                    break;
                case 6:
                    AudioRecordManager.this.stopRec();
                    AudioRecordManager.this.destroyView();
                    AudioRecordManager.this.deleteAudioFile();
                    AudioRecordManager.this.mCurAudioState = AudioRecordManager.this.idleState;
                    AudioRecordManager.this.idleState.enter();
                    break;
                case 7:
                    int counter = ((Integer) msg.obj).intValue();
                    if (counter > 0) {
                        Message message = Message.obtain();
                        message.what = 8;
                        message.obj = Integer.valueOf(counter - 1);
                        AudioRecordManager.this.mHandler.sendMessageDelayed(message, 1000L);
                        AudioRecordManager.this.setTimeoutView(counter);
                    } else {
                        AudioRecordManager.this.mHandler.postDelayed(new Runnable() {
                            public void run() {
                                AudioRecordManager.this.stopRec();
                                AudioRecordManager.this.finishRecord();
                                AudioRecordManager.this.destroyView();
                            }
                        }, 500L);
                        AudioRecordManager.this.mCurAudioState = AudioRecordManager.this.idleState;
                    }
            }

        }
    }

    class CancelState extends IAudioState {
        CancelState() {
        }

        void handleMessage(AudioStateMessage msg) {
            Log.d(TAG, this.getClass().getSimpleName() + " handleMessage : " + msg.what);
            switch (msg.what) {
                case 1:
                case 2:
                case 3:
                default:
                    break;
                case 4:
                    AudioRecordManager.this.setRecordingView();
                    AudioRecordManager.this.mCurAudioState = AudioRecordManager.this.recordState;
                    AudioRecordManager.this.sendEmptyMessage(2);
                    break;
                case 5:
                case 6:
                    AudioRecordManager.this.stopRec();
                    AudioRecordManager.this.destroyView();
                    AudioRecordManager.this.deleteAudioFile();
                    AudioRecordManager.this.mCurAudioState = AudioRecordManager.this.idleState;
                    AudioRecordManager.this.idleState.enter();
                    break;
                case 7:
                    int counter = ((Integer) msg.obj).intValue();
                    if (counter > 0) {
                        Message message = Message.obtain();
                        message.what = 8;
                        message.obj = Integer.valueOf(counter - 1);
                        AudioRecordManager.this.mHandler.sendMessageDelayed(message, 1000L);
                    } else {
                        AudioRecordManager.this.mHandler.postDelayed(new Runnable() {
                            public void run() {
                                AudioRecordManager.this.stopRec();
                                AudioRecordManager.this.finishRecord();
                                AudioRecordManager.this.destroyView();
                            }
                        }, 500L);
                        AudioRecordManager.this.mCurAudioState = AudioRecordManager.this.idleState;
                        AudioRecordManager.this.idleState.enter();
                    }
            }

        }
    }

    class SendingState extends IAudioState {
        SendingState() {
        }

        void handleMessage(AudioStateMessage message) {
            Log.d(TAG, "SendingState handleMessage " + message.what);
            switch (message.what) {
                case 9:
                    AudioRecordManager.this.stopRec();
                    if (((Boolean) message.obj).booleanValue()) {
                        AudioRecordManager.this.finishRecord();
                    }

                    AudioRecordManager.this.destroyView();
                    AudioRecordManager.this.mCurAudioState = AudioRecordManager.this.idleState;
                default:
            }
        }
    }

    class RecordState extends IAudioState {
        RecordState() {
        }

        void handleMessage(AudioStateMessage msg) {
            Log.d(TAG, this.getClass().getSimpleName() + " handleMessage : " + msg.what);
            switch (msg.what) {
                case 2:
                    AudioRecordManager.this.audioDBChanged();
                    AudioRecordManager.this.mHandler.sendEmptyMessageDelayed(2, 150L);
                    break;
                case 3:
                    AudioRecordManager.this.setCancelView();
                    AudioRecordManager.this.mCurAudioState = AudioRecordManager.this.cancelState;
                case 4:
                default:
                    break;
                case 5:
                    final boolean checked = AudioRecordManager.this.checkAudioTimeLength();
                    boolean activityFinished = false;
                    if (msg.obj != null) {
                        activityFinished = ((Boolean) msg.obj).booleanValue();
                    }

                    if (checked && !activityFinished) {
                        if (mAudioRecordListener != null) {
                            mAudioRecordListener.setAudioShortTipView();
                        }
                        AudioRecordManager.this.mHandler.removeMessages(2);
                    }

                    if (!activityFinished && AudioRecordManager.this.mHandler != null) {
                        AudioRecordManager.this.mHandler.postDelayed(new Runnable() {
                            public void run() {
                                AudioStateMessage message = AudioStateMessage.obtain();
                                message.what = 9;
                                message.obj = Boolean.valueOf(!checked);
                                AudioRecordManager.this.sendMessage(message);
                            }
                        }, 500L);
                        AudioRecordManager.this.mCurAudioState = AudioRecordManager.this.sendingState;
                    } else {
                        AudioRecordManager.this.stopRec();
                        if (!checked && activityFinished) {
                            AudioRecordManager.this.finishRecord();
                        }

                        AudioRecordManager.this.destroyView();
                        AudioRecordManager.this.mCurAudioState = AudioRecordManager.this.idleState;
                    }
                    break;
                case 6:
                    AudioRecordManager.this.stopRec();
                    AudioRecordManager.this.destroyView();
                    AudioRecordManager.this.deleteAudioFile();
                    AudioRecordManager.this.mCurAudioState = AudioRecordManager.this.idleState;
                    AudioRecordManager.this.idleState.enter();
                    break;
                case 7:
                    int counter = ((Integer) msg.obj).intValue();
                    AudioRecordManager.this.setTimeoutView(counter);
                    AudioRecordManager.this.mCurAudioState = AudioRecordManager.this.timerState;
                    if (counter > 0) {
                        Message message = Message.obtain();
                        message.what = 8;
                        message.obj = Integer.valueOf(counter - 1);
                        AudioRecordManager.this.mHandler.sendMessageDelayed(message, 1000L);
                    } else {
                        AudioRecordManager.this.mHandler.postDelayed(new Runnable() {
                            public void run() {
                                AudioRecordManager.this.stopRec();
                                AudioRecordManager.this.finishRecord();
                                AudioRecordManager.this.destroyView();
                            }
                        }, 500L);
                        AudioRecordManager.this.mCurAudioState = AudioRecordManager.this.idleState;
                    }
            }

        }
    }

    class IdleState extends IAudioState {
        public IdleState() {
            Log.d(TAG, "IdleState");
        }

        void enter() {
            super.enter();
            if (AudioRecordManager.this.mHandler != null) {
                AudioRecordManager.this.mHandler.removeMessages(7);
                AudioRecordManager.this.mHandler.removeMessages(8);
                AudioRecordManager.this.mHandler.removeMessages(2);
            }

        }

        void handleMessage(AudioStateMessage msg) {
            Log.d(TAG, "IdleState handleMessage : " + msg.what);
            switch (msg.what) {
                case 1:
                    AudioRecordManager.this.initView();
                    AudioRecordManager.this.setRecordingView();
                    AudioRecordManager.this.startRec();
                    AudioRecordManager.this.smStartRecTime = SystemClock.elapsedRealtime();
                    AudioRecordManager.this.mCurAudioState = AudioRecordManager.this.recordState;
                    AudioRecordManager.this.sendEmptyMessage(2);
                default:
            }
        }
    }

    public IAudioRecordListener getAudioRecordListener() {
        return mAudioRecordListener;
    }

    public void setAudioRecordListener(IAudioRecordListener audioRecordListener) {
        mAudioRecordListener = audioRecordListener;
    }
}
