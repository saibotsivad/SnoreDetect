package com.davistobias.snoredetect;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.Buffer;

public class MainActivity extends AppCompatActivity {

    private static final int RECORDER_SAMPLERATE = 8000;
    private static final int RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_MONO;
    private static final int RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    
    private static final int PERMISSIONS_REQUEST_RECORD_AUDIO = 1;
    private static final String[] REQUIRED_PERMISSIONS = {
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    };
    
    private AudioManager audioManager;
    private boolean hasAudioFocus = false;

    private AudioRecordingService audioService;
    private boolean serviceBound = false;

    private final Handler mHandler = new Handler();
    private LineGraphSeries<DataPoint> mSeries;
    private double graphLastXValue = 0d;
    public double decibel = 0d;
    public TextView textView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setButtonHandlers();
        enableButtons(false);
        textView = (TextView) findViewById(R.id.textView);

        GraphView graph = (GraphView) findViewById(R.id.graph);
        mSeries = new LineGraphSeries<>();
        graph.addSeries(mSeries);
        graph.getViewport().setXAxisBoundsManual(true);
        graph.getViewport().setMinX(0);
        graph.getViewport().setMaxX(100);

        // Initialize AudioManager
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        
        // Check permissions on startup
        checkPermissions();
        
        // Bind to AudioRecordingService
        bindAudioService();
    }

    private void setButtonHandlers() {
        ((Button) findViewById(R.id.btnStart)).setOnClickListener(btnClick);
        ((Button) findViewById(R.id.btnStop)).setOnClickListener(btnClick);
    }

    private void enableButton(int id, boolean isEnable) {
        ((Button) findViewById(id)).setEnabled(isEnable);
    }

    private void enableButtons(boolean isRecording) {
        enableButton(R.id.btnStart, !isRecording);
        enableButton(R.id.btnStop, isRecording);
    }

    private void startRecording() {
        if (serviceBound && audioService != null) {
            Intent serviceIntent = new Intent(this, AudioRecordingService.class);
            startService(serviceIntent);
            
            boolean started = audioService.startRecording();
            if (!started) {
                Toast.makeText(this, "Failed to start recording", Toast.LENGTH_SHORT).show();
                enableButtons(false);
            }
        } else {
            Toast.makeText(this, "Audio service not available", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopRecording() {
        if (serviceBound && audioService != null) {
            audioService.stopRecording();
            
            Intent serviceIntent = new Intent(this, AudioRecordingService.class);
            stopService(serviceIntent);
        }
    }

    private View.OnClickListener btnClick = new View.OnClickListener() {
        public void onClick(View v) {
            int viewId = v.getId();
            if (viewId == R.id.btnStart) {
                //Log.i("AudioData ", "Start Pressed");
                if (hasRequiredPermissions()) {
                    if (requestAudioFocus()) {
                        enableButtons(true);
                        startRecording();
                    } else {
                        Toast.makeText(MainActivity.this, "Cannot start recording - audio focus denied", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    requestPermissions();
                }
            } else if (viewId == R.id.btnStop) {
                //Log.i("AudioData ", "Stop pressed");
                enableButtons(false);
                stopRecording();
                releaseAudioFocus();
            }
        }
    };

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!hasRequiredPermissions()) {
                requestPermissions();
            }
        }
    }

    private boolean hasRequiredPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            for (String permission : REQUIRED_PERMISSIONS) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && 
                    permission.equals(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                    // Skip WRITE_EXTERNAL_STORAGE check on API 33+ (Android 13+)
                    continue;
                }
                if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
        }
        return true;
    }

    private void requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            String[] permissionsToRequest;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // On API 33+, only request RECORD_AUDIO
                permissionsToRequest = new String[]{Manifest.permission.RECORD_AUDIO};
            } else {
                permissionsToRequest = REQUIRED_PERMISSIONS;
            }
            ActivityCompat.requestPermissions(this, permissionsToRequest, PERMISSIONS_REQUEST_RECORD_AUDIO);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSIONS_REQUEST_RECORD_AUDIO) {
            boolean allPermissionsGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allPermissionsGranted = false;
                    break;
                }
            }
            
            if (allPermissionsGranted) {
                Toast.makeText(this, "Permissions granted. You can now start recording.", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Permissions are required for audio recording", Toast.LENGTH_LONG).show();
            }
        }
    }

    private AudioManager.OnAudioFocusChangeListener audioFocusChangeListener = new AudioManager.OnAudioFocusChangeListener() {
        @Override
        public void onAudioFocusChange(int focusChange) {
            switch (focusChange) {
                case AudioManager.AUDIOFOCUS_LOSS:
                    // Lost focus permanently - stop recording
                    Log.i("AudioFocus", "Audio focus lost permanently");
                    hasAudioFocus = false;
                    if (serviceBound && audioService != null && audioService.isRecording()) {
                        stopRecording();
                        runOnUiThread(() -> {
                            enableButtons(false);
                            Toast.makeText(MainActivity.this, "Recording stopped - audio focus lost", Toast.LENGTH_SHORT).show();
                        });
                    }
                    break;
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                    // Lost focus temporarily - pause recording
                    Log.i("AudioFocus", "Audio focus lost temporarily");
                    hasAudioFocus = false;
                    // For simplicity, we'll stop recording rather than pause
                    if (serviceBound && audioService != null && audioService.isRecording()) {
                        stopRecording();
                        runOnUiThread(() -> {
                            enableButtons(false);
                            Toast.makeText(MainActivity.this, "Recording stopped - temporary interruption", Toast.LENGTH_SHORT).show();
                        });
                    }
                    break;
                case AudioManager.AUDIOFOCUS_GAIN:
                    // Regained focus
                    Log.i("AudioFocus", "Audio focus gained");
                    hasAudioFocus = true;
                    break;
            }
        }
    };

    private boolean requestAudioFocus() {
        if (audioManager == null) {
            return false;
        }

        int result;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Use AudioFocusRequest for API 26+
            android.media.AudioFocusRequest focusRequest = new android.media.AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(new android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build())
                    .setAcceptsDelayedFocusGain(true)
                    .setOnAudioFocusChangeListener(audioFocusChangeListener)
                    .build();
            result = audioManager.requestAudioFocus(focusRequest);
        } else {
            // Use deprecated method for older APIs
            result = audioManager.requestAudioFocus(
                    audioFocusChangeListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN
            );
        }

        hasAudioFocus = (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
        Log.i("AudioFocus", "Audio focus request result: " + (hasAudioFocus ? "GRANTED" : "DENIED"));
        return hasAudioFocus;
    }

    private void releaseAudioFocus() {
        if (audioManager != null && hasAudioFocus) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // For API 26+, we'd need to store the AudioFocusRequest object
                // For simplicity, using the deprecated method for all versions
                audioManager.abandonAudioFocus(audioFocusChangeListener);
            } else {
                audioManager.abandonAudioFocus(audioFocusChangeListener);
            }
            hasAudioFocus = false;
            Log.i("AudioFocus", "Audio focus released");
        }
    }

    // onClick of backbutton finishes the activity.
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finish();
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clean up audio focus when activity is destroyed
        if (serviceBound && audioService != null && audioService.isRecording()) {
            stopRecording();
        }
        releaseAudioFocus();
        
        // Unbind from service
        if (serviceBound) {
            unbindService(serviceConnection);
            serviceBound = false;
        }
    }

    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            AudioRecordingService.AudioRecordingBinder binder = (AudioRecordingService.AudioRecordingBinder) service;
            audioService = binder.getService();
            serviceBound = true;
            
            // Set up audio data callback
            audioService.setAudioDataCallback(new AudioRecordingService.AudioDataCallback() {
                @Override
                public void onAudioData(double decibel) {
                    MainActivity.this.decibel = decibel;
                    updateAudioVisualization();
                }

                @Override
                public void onRecordingStarted() {
                    runOnUiThread(() -> {
                        enableButtons(true);
                        startAudioVisualization();
                    });
                }

                @Override
                public void onRecordingStopped() {
                    runOnUiThread(() -> {
                        enableButtons(false);
                        stopAudioVisualization();
                    });
                }

                @Override
                public void onRecordingError(String error) {
                    runOnUiThread(() -> {
                        enableButtons(false);
                        stopAudioVisualization();
                        Toast.makeText(MainActivity.this, "Recording error: " + error, Toast.LENGTH_LONG).show();
                    });
                }
            });
            
            Log.d("MainActivity", "Audio service connected");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            audioService = null;
            serviceBound = false;
            Log.d("MainActivity", "Audio service disconnected");
        }
    };

    private void bindAudioService() {
        Intent intent = new Intent(this, AudioRecordingService.class);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    private void updateAudioVisualization() {
        runOnUiThread(() -> {
            graphLastXValue += 1d;
            if (decibel >= -30.0) {
                textView.setText("SNORING");
            } else {
                textView.setText("NORMAL");
            }
            mSeries.appendData(new DataPoint(graphLastXValue, decibel), true, 100);
        });
    }

    private Runnable mTimer;

    private void startAudioVisualization() {
        if (mTimer != null) {
            mHandler.removeCallbacks(mTimer);
        }
        
        mTimer = new Runnable() {
            @Override
            public void run() {
                // The visualization is now updated by the service callback
                // This timer is just to ensure consistent UI updates
                mHandler.postDelayed(this, 35);
            }
        };
        mHandler.postDelayed(mTimer, 1000);
    }

    private void stopAudioVisualization() {
        if (mTimer != null) {
            mHandler.removeCallbacks(mTimer);
            mTimer = null;
        }
    }
}
