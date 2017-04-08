package com.lqr.audiodemo;

import android.Manifest;
import android.graphics.drawable.AnimationDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import com.lqr.adapter.LQRAdapterForRecyclerView;
import com.lqr.adapter.LQRViewHolder;
import com.lqr.adapter.LQRViewHolderForRecyclerView;
import com.lqr.adapter.OnItemClickListener;
import com.lqr.audio.AudioPlayManager;
import com.lqr.audio.AudioRecordManager;
import com.lqr.audio.IAudioPlayListener;
import com.lqr.audio.IAudioRecordListener;
import com.lqr.recyclerview.LQRRecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import butterknife.Bind;
import butterknife.ButterKnife;
import kr.co.namee.permissiongen.PermissionGen;

public class MainActivity extends AppCompatActivity {

    @Bind(R.id.root)
    LinearLayout mRoot;
    @Bind(R.id.rvMsg)
    LQRRecyclerView mRvMsg;
    @Bind(R.id.btnVoice)
    Button mBtnVoice;
    private File mAudioDir;
    private LQRAdapterForRecyclerView<File> mAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        init();
        initData();
        initListener();
    }

    private void init() {
        PermissionGen.with(this)
                .addRequestCode(100)
                .permissions(Manifest.permission.RECORD_AUDIO
                        , Manifest.permission.WRITE_EXTERNAL_STORAGE
                        , Manifest.permission.WAKE_LOCK
                        , Manifest.permission.READ_EXTERNAL_STORAGE)
                .request();
        AudioRecordManager.getInstance(this).setMaxVoiceDuration(12);
        mAudioDir = new File(Environment.getExternalStorageDirectory(), "LQR_AUDIO");
        if (!mAudioDir.exists()) {
            mAudioDir.mkdirs();
        }
        AudioRecordManager.getInstance(this).setAudioSavePath(mAudioDir.getAbsolutePath());
    }

    private void initData() {
        loadData();
        setAdapter();
    }

    private void initListener() {
        mBtnVoice.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        AudioRecordManager.getInstance(MainActivity.this).startRecord();
                        break;
                    case MotionEvent.ACTION_MOVE:
                        if (isCancelled(v, event)) {
                            AudioRecordManager.getInstance(MainActivity.this).willCancelRecord();
                        } else {
                            AudioRecordManager.getInstance(MainActivity.this).continueRecord();
                        }
                        break;
                    case MotionEvent.ACTION_UP:
                        AudioRecordManager.getInstance(MainActivity.this).stopRecord();
                        AudioRecordManager.getInstance(MainActivity.this).destroyRecord();
                        break;
                }
                return false;
            }
        });

        AudioRecordManager.getInstance(this).setAudioRecordListener(new IAudioRecordListener() {

            private TextView mTimerTV;
            private TextView mStateTV;
            private ImageView mStateIV;
            private PopupWindow mRecordWindow;

            @Override
            public void initTipView() {
                View view = View.inflate(MainActivity.this, R.layout.popup_audio_wi_vo, null);
                mStateIV = (ImageView) view.findViewById(R.id.rc_audio_state_image);
                mStateTV = (TextView) view.findViewById(R.id.rc_audio_state_text);
                mTimerTV = (TextView) view.findViewById(R.id.rc_audio_timer);
                mRecordWindow = new PopupWindow(view, -1, -1);
                mRecordWindow.showAtLocation(mRoot, 17, 0, 0);
                mRecordWindow.setFocusable(true);
                mRecordWindow.setOutsideTouchable(false);
                mRecordWindow.setTouchable(false);
            }

            @Override
            public void setTimeoutTipView(int counter) {
                if (this.mRecordWindow != null) {
                    this.mStateIV.setVisibility(View.GONE);
                    this.mStateTV.setVisibility(View.VISIBLE);
                    this.mStateTV.setText(R.string.voice_rec);
                    this.mStateTV.setBackgroundResource(R.drawable.bg_voice_popup);
                    this.mTimerTV.setText(String.format("%s", new Object[]{Integer.valueOf(counter)}));
                    this.mTimerTV.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void setRecordingTipView() {
                if (this.mRecordWindow != null) {
                    this.mStateIV.setVisibility(View.VISIBLE);
                    this.mStateIV.setImageResource(R.mipmap.ic_volume_1);
                    this.mStateTV.setVisibility(View.VISIBLE);
                    this.mStateTV.setText(R.string.voice_rec);
                    this.mStateTV.setBackgroundResource(R.drawable.bg_voice_popup);
                    this.mTimerTV.setVisibility(View.GONE);
                }
            }

            @Override
            public void setAudioShortTipView() {
                if (this.mRecordWindow != null) {
                    mStateIV.setImageResource(R.mipmap.ic_volume_wraning);
                    mStateTV.setText(R.string.voice_short);
                }
            }

            @Override
            public void setCancelTipView() {
                if (this.mRecordWindow != null) {
                    this.mTimerTV.setVisibility(View.GONE);
                    this.mStateIV.setVisibility(View.VISIBLE);
                    this.mStateIV.setImageResource(R.mipmap.ic_volume_cancel);
                    this.mStateTV.setVisibility(View.VISIBLE);
                    this.mStateTV.setText(R.string.voice_cancel);
                    this.mStateTV.setBackgroundResource(R.drawable.corner_voice_style);
                }
            }

            @Override
            public void destroyTipView() {
                if (this.mRecordWindow != null) {
                    this.mRecordWindow.dismiss();
                    this.mRecordWindow = null;
                    this.mStateIV = null;
                    this.mStateTV = null;
                    this.mTimerTV = null;
                }
            }

            @Override
            public void onStartRecord() {
                //开始录制
            }

            @Override
            public void onFinish(Uri audioPath, int duration) {
                //发送文件
                File file = new File(audioPath.getPath());
                if (file.exists()) {
                    Toast.makeText(getApplicationContext(), "录制成功", Toast.LENGTH_SHORT).show();
                    loadData();
                }
            }

            @Override
            public void onAudioDBChanged(int db) {
                switch (db / 5) {
                    case 0:
                        this.mStateIV.setImageResource(R.mipmap.ic_volume_1);
                        break;
                    case 1:
                        this.mStateIV.setImageResource(R.mipmap.ic_volume_2);
                        break;
                    case 2:
                        this.mStateIV.setImageResource(R.mipmap.ic_volume_3);
                        break;
                    case 3:
                        this.mStateIV.setImageResource(R.mipmap.ic_volume_4);
                        break;
                    case 4:
                        this.mStateIV.setImageResource(R.mipmap.ic_volume_5);
                        break;
                    case 5:
                        this.mStateIV.setImageResource(R.mipmap.ic_volume_6);
                        break;
                    case 6:
                        this.mStateIV.setImageResource(R.mipmap.ic_volume_7);
                        break;
                    default:
                        this.mStateIV.setImageResource(R.mipmap.ic_volume_8);
                }
            }
        });
    }

    private boolean isCancelled(View view, MotionEvent event) {
        int[] location = new int[2];
        view.getLocationOnScreen(location);
        if (event.getRawX() < location[0] || event.getRawX() > location[0] + view.getWidth() || event.getRawY() < location[1] - 40) {
            return true;
        }
        return false;
    }

    private List<File> mData = new ArrayList<>();

    private void loadData() {
        if (mAudioDir.exists()) {
            mData.clear();
            File[] files = mAudioDir.listFiles();
            for (File file : files) {
                if (file.getAbsolutePath().endsWith("voice")) {
                    mData.add(file);
                }
            }
            setAdapter();
        }
    }

    private void setAdapter() {
        if (mAdapter == null) {
            mAdapter = new LQRAdapterForRecyclerView<File>(this, mData, R.layout.item_audio_send) {
                @Override
                public void convert(LQRViewHolderForRecyclerView helper, File item, int position) {
                    //这里就不考虑语音长度了，实际开发中用到的Sdk有提供保存语音信息的bean


                }
            };
            mAdapter.setOnItemClickListener(new OnItemClickListener() {
                @Override
                public void onItemClick(LQRViewHolder helper, ViewGroup parent, View itemView, int position) {
                    AudioPlayManager.getInstance().stopPlay();
                    File item = mData.get(position);
                    final ImageView ivAudio = helper.getView(R.id.ivAudio);
                    Uri audioUri = Uri.fromFile(item);
                    Log.e("LQR", audioUri.toString());
                    AudioPlayManager.getInstance().startPlay(MainActivity.this, audioUri, new IAudioPlayListener() {
                        @Override
                        public void onStart(Uri var1) {
                            if (ivAudio != null && ivAudio.getBackground() instanceof AnimationDrawable) {
                                AnimationDrawable animation = (AnimationDrawable) ivAudio.getBackground();
                                animation.start();
                            }
                        }

                        @Override
                        public void onStop(Uri var1) {
                            if (ivAudio != null && ivAudio.getBackground() instanceof AnimationDrawable) {
                                AnimationDrawable animation = (AnimationDrawable) ivAudio.getBackground();
                                animation.stop();
                                animation.selectDrawable(0);
                            }

                        }

                        @Override
                        public void onComplete(Uri var1) {
                            if (ivAudio != null && ivAudio.getBackground() instanceof AnimationDrawable) {
                                AnimationDrawable animation = (AnimationDrawable) ivAudio.getBackground();
                                animation.stop();
                                animation.selectDrawable(0);
                            }
                        }
                    });
                }
            });
            mRvMsg.setAdapter(mAdapter);
        } else

        {
            mAdapter.notifyDataSetChangedWrapper();
        }
    }
}
