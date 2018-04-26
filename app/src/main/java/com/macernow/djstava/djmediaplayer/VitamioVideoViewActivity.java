package com.macernow.djstava.djmediaplayer;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.view.Display;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.Toast;

import io.vov.vitamio.LibsChecker;
import io.vov.vitamio.MediaPlayer;
import io.vov.vitamio.widget.MediaController;
import io.vov.vitamio.widget.VideoView;

public class VitamioVideoViewActivity extends AppCompatActivity {

    private static final String TAG = VitamioVideoViewActivity.class.getSimpleName();
    private VideoView videoView;
    private Intent intent;
    private String movieUrl;
    private MediaController mediaController;
    private AudioManager audioManager;
    private GestureDetector gestureDetector;
    private ImageView mOperationBg, mOperationPercent;
    private long currentPosition, duration;
    private View mVolumeBrightnessLayout;

    /*最大声音*/
    private int maxVolume;

    /*当前声音*/
    private int currentVolume = -1;

    /**/

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!LibsChecker.checkVitamioLibs(this))
            return;
        setContentView(R.layout.activity_vitamio_video_view);

        /*
        * 去屏保
        * */
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON, WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        videoView = (VideoView) this.findViewById(R.id.surface_view);
        mVolumeBrightnessLayout = findViewById(R.id.operation_volume);
        mOperationBg = (ImageView) findViewById(R.id.operation_bg);
        mOperationPercent = (ImageView) findViewById(R.id.operation_percent);

        intent = getIntent();
        movieUrl = intent.getStringExtra("movieUrl");
        movieUrl = "rtmp://live.hkstv.hk.lxdns.com/live/hks";
        mediaController = new MediaController(this);

        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);

        if (!movieUrl.isEmpty()) {
            if (movieUrl.startsWith("http:")) {
                videoView.setVideoURI(Uri.parse(movieUrl));
            } else {
                videoView.setVideoPath(movieUrl);
            }

            videoView.setVideoLayout(VideoView.VIDEO_LAYOUT_STRETCH, 0);
            videoView.setMediaController(mediaController);
            videoView.requestFocus();

            videoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    mp.setPlaybackSpeed(1.0f);
                    duration = mp.getDuration();
                }
            });

            videoView.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    Toast.makeText(VitamioVideoViewActivity.this, R.string.video_finish, Toast.LENGTH_SHORT).show();
                    finish();
                }
            });

            videoView.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    new AlertDialog.Builder(VitamioVideoViewActivity.this).setTitle(R.string.video_error)
                            .setPositiveButton(R.string.video_error_yes, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    finish();
                                }
                            })
                            .show();

                    return true;
                }
            });
        } else {
            Toast.makeText(VitamioVideoViewActivity.this, R.string.no_such_movie, Toast.LENGTH_SHORT).show();
        }

        gestureDetector = new GestureDetector(this, new CustomGestureListener());
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (gestureDetector.onTouchEvent(event)) {
            return true;
        }

        switch (event.getAction() & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_UP:
                endGesture();
                break;
        }

        return super.onTouchEvent(event);
    }

    private void endGesture() {
        currentVolume = -1;

        mDismissHandler.removeMessages(0);
        mDismissHandler.sendEmptyMessageDelayed(0, 500);
    }

    private class CustomGestureListener extends GestureDetector.SimpleOnGestureListener {

        /*
        * 双击操作
        * */
        @Override
        public boolean onDoubleTap(MotionEvent e) {
            if (videoView.isPlaying()) {
                videoView.pause();
            } else {
                videoView.start();
            }

            return super.onDoubleTap(e);
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {

            //上下滑动
            if (Math.abs(distanceY) / Math.abs(distanceX) > 3) {
                float mOldY = e1.getY();
                int y = (int) e2.getRawY();
                Display disp = getWindowManager().getDefaultDisplay();
                int windowHeight = disp.getHeight();

                onVolumeSlide((mOldY - y) / windowHeight);

            }

            return super.onScroll(e1, e2, distanceX, distanceY);
        }

        /*
         * 滑动操作
         * */
        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {

            currentPosition = videoView.getCurrentPosition();

            /*向左滑*/
            if (e1.getX() - e2.getX() > 120) {

                if (currentPosition < 10000) {
                    currentPosition = 0;
                    videoView.seekTo(currentPosition);
                } else {
                    videoView.seekTo(currentPosition - 10000);
                }

            } else if (e2.getX() - e1.getX() > 120) {
            /*向右滑*/
                if (currentPosition + 10000 > duration) {
                    currentPosition = duration;
                    videoView.seekTo(currentPosition);
                } else {
                    videoView.seekTo(currentPosition + 10000);
                }

            }

            return super.onFling(e1, e2, velocityX, velocityY);
        }
    }

    /*
    * 定时隐藏
    * */
    private Handler mDismissHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            mVolumeBrightnessLayout.setVisibility(View.GONE);
        }
    };

    /**
     * 滑动改变声音大小
     */
    private void onVolumeSlide(float percent) {

        if (currentVolume == -1) {
            currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            if (currentVolume < 0)
                currentVolume = 0;

            // 显示
            mOperationBg.setImageResource(R.drawable.video_volumn_bg);
            mVolumeBrightnessLayout.setVisibility(View.VISIBLE);
        }

        int value = currentVolume + (int) (percent * maxVolume);
        if (value > maxVolume) {
            value = maxVolume;
        } else if (value < 0) {
            value = 0;
        }

        // 变更声音
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, value, 0);

        // 变更进度条
        ViewGroup.LayoutParams lp = mOperationPercent.getLayoutParams();
        lp.width = findViewById(R.id.operation_full).getLayoutParams().width * value / maxVolume;
        mOperationPercent.setLayoutParams(lp);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
                new AlertDialog.Builder(VitamioVideoViewActivity.this).setMessage(R.string.alertdialog_message)
                        .setTitle(R.string.alertdialog_title)
                        .setCancelable(false)
                        .setPositiveButton(R.string.alertdialog_yes, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                finish();
                            }
                        })
                        .setNegativeButton(R.string.alertdialog_no, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        }).show();
                break;

            default:
                break;
        }

        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (videoView != null) {
            videoView.pause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (videoView != null) {
            videoView.resume();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (videoView != null) {
            videoView.stopPlayback();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_vitamio_video_view, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
