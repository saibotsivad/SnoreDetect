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
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.POST_NOTIFICATIONS
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
        try {
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
        } catch (Exception e) {
            Toast.makeText(this, "onCreate Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            Log.e("MainActivity", "onCreate failed", e);
        }
    }

    private void setButtonHandlers() {
        try {
            ((Button) findViewById(R.id.btnStart)).setOnClickListener(btnClick);
            ((Button) findViewById(R.id.btnStop)).setOnClickListener(btnClick);
        } catch (Exception e) {
            Toast.makeText(this, "Button Setup Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            Log.e("MainActivity", "setButtonHandlers failed", e);
        }
    }

    private void enableButton(int id, boolean isEnable) {
        try {
            ((Button) findViewById(id)).setEnabled(isEnable);
        } catch (Exception e) {
            Toast.makeText(this, "Enable Button Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            Log.e("MainActivity", "enableButton failed for id: " + id, e);
        }
    }

    private void enableButtons(boolean isRecording) {
        try {
            enableButton(R.id.btnStart, !isRecording);
            enableButton(R.id.btnStop, isRecording);
        } catch (Exception e) {
            Toast.makeText(this, "Enable Buttons Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            Log.e("MainActivity", "enableButtons failed", e);
        }
    }

    private void startRecording() {
        try {
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
        } catch (Exception e) {
            Toast.makeText(this, "Start Recording Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            Log.e("MainActivity", "startRecording failed", e);
            enableButtons(false);
        }
    }

    private void stopRecording() {
        try {
            if (serviceBound && audioService != null) {
                audioService.stopRecording();
                
                Intent serviceIntent = new Intent(this, AudioRecordingService.class);
                stopService(serviceIntent);
            }
        } catch (Exception e) {
            Toast.makeText(this, "Stop Recording Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            Log.e("MainActivity", "stopRecording failed", e);
        }
    }

    private View.OnClickListener btnClick = new View.OnClickListener() {
        public void onClick(View v) {
            try {
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
            } catch (Exception e) {
                Toast.makeText(MainActivity.this, "Button Click Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                Log.e("MainActivity", "btnClick failed", e);
            }
        }
    };

    private void checkPermissions() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!hasRequiredPermissions()) {
                    requestPermissions();
                }
            }
        } catch (Exception e) {
            Toast.makeText(this, "Check Permissions Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            Log.e("MainActivity", "checkPermissions failed", e);
        }
    }

    private boolean hasRequiredPermissions() {
        try {
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
        } catch (Exception e) {
            Toast.makeText(this, "Permission Check Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            Log.e("MainActivity", "hasRequiredPermissions failed", e);
            return false;
        }
    }

    private void requestPermissions() {
        try {
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
        } catch (Exception e) {
            Toast.makeText(this, "Request Permissions Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            Log.e("MainActivity", "requestPermissions failed", e);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        try {
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
        } catch (Exception e) {
            Toast.makeText(this, "Permission Result Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            Log.e("MainActivity", "onRequestPermissionsResult failed", e);
        }
    }

    private AudioManager.OnAudioFocusChangeListener audioFocusChangeListener = new AudioManager.OnAudioFocusChangeListener() {
        @Override
        public void onAudioFocusChange(int focusChange) {
            try {
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
            } catch (Exception e) {
                Toast.makeText(MainActivity.this, "Audio Focus Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                Log.e("MainActivity", "onAudioFocusChange failed", e);
            }
        }
    };

    private boolean requestAudioFocus() {
        try {
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
        } catch (Exception e) {
            Toast.makeText(this, "Audio Focus Request Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            Log.e("MainActivity", "requestAudioFocus failed", e);
            return false;
        }
    }

    private void releaseAudioFocus() {
        try {
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
        } catch (Exception e) {
            Toast.makeText(this, "Release Audio Focus Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            Log.e("MainActivity", "releaseAudioFocus failed", e);
        }
    }

    // onClick of backbutton finishes the activity.
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        try {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                finish();
            }
            return super.onKeyDown(keyCode, event);
        } catch (Exception e) {
            Toast.makeText(this, "Key Down Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            Log.e("MainActivity", "onKeyDown failed", e);
            return false;
        }
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        try {
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
        } catch (Exception e) {
            Toast.makeText(this, "Destroy Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            Log.e("MainActivity", "onDestroy failed", e);
        }
    }

    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            try {
                AudioRecordingService.AudioRecordingBinder binder = (AudioRecordingService.AudioRecordingBinder) service;
                audioService = binder.getService();
                serviceBound = true;
                
                // Set up audio data callback
                audioService.setAudioDataCallback(new AudioRecordingService.AudioDataCallback() {
                    @Override
                    public void onAudioData(double decibel) {
                        try {
                            MainActivity.this.decibel = decibel;
                            updateAudioVisualization();
                        } catch (Exception e) {
                            runOnUiThread(() -> {
                                Toast.makeText(MainActivity.this, "Audio Data Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            });
                            Log.e("MainActivity", "onAudioData failed", e);
                        }
                    }

                    @Override
                    public void onRecordingStarted() {
                        try {
                            runOnUiThread(() -> {
                                enableButtons(true);
                                startAudioVisualization();
                            });
                        } catch (Exception e) {
                            runOnUiThread(() -> {
                                Toast.makeText(MainActivity.this, "Recording Started Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            });
                            Log.e("MainActivity", "onRecordingStarted failed", e);
                        }
                    }

                    @Override
                    public void onRecordingStopped() {
                        try {
                            runOnUiThread(() -> {
                                enableButtons(false);
                                stopAudioVisualization();
                            });
                        } catch (Exception e) {
                            runOnUiThread(() -> {
                                Toast.makeText(MainActivity.this, "Recording Stopped Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            });
                            Log.e("MainActivity", "onRecordingStopped failed", e);
                        }
                    }

                    @Override
                    public void onRecordingError(String error) {
                        try {
                            runOnUiThread(() -> {
                                enableButtons(false);
                                stopAudioVisualization();
                                Toast.makeText(MainActivity.this, "Recording error: " + error, Toast.LENGTH_LONG).show();
                            });
                        } catch (Exception e) {
                            Log.e("MainActivity", "onRecordingError callback failed", e);
                        }
                    }
                });
                
                Log.d("MainActivity", "Audio service connected");
            } catch (Exception e) {
                Toast.makeText(MainActivity.this, "Service Connected Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                Log.e("MainActivity", "onServiceConnected failed", e);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            try {
                audioService = null;
                serviceBound = false;
                Log.d("MainActivity", "Audio service disconnected");
            } catch (Exception e) {
                Log.e("MainActivity", "onServiceDisconnected failed", e);
            }
        }
    };

    private void bindAudioService() {
        try {
            Intent intent = new Intent(this, AudioRecordingService.class);
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        } catch (Exception e) {
            Toast.makeText(this, "Bind Service Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            Log.e("MainActivity", "bindAudioService failed", e);
        }
    }

    private void updateAudioVisualization() {
        try {
            runOnUiThread(() -> {
                try {
                    graphLastXValue += 1d;
                    if (decibel >= -30.0) {
                        textView.setText("SNORING");
                    } else {
                        textView.setText("NORMAL");
                    }
                    mSeries.appendData(new DataPoint(graphLastXValue, decibel), true, 100);
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "UI Update Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    Log.e("MainActivity", "updateAudioVisualization UI failed", e);
                }
            });
        } catch (Exception e) {
            Toast.makeText(this, "Visualization Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            Log.e("MainActivity", "updateAudioVisualization failed", e);
        }
    }

    private Runnable mTimer;

    private void startAudioVisualization() {
        try {
            if (mTimer != null) {
                mHandler.removeCallbacks(mTimer);
            }
            
            mTimer = new Runnable() {
                @Override
                public void run() {
                    try {
                        // The visualization is now updated by the service callback
                        // This timer is just to ensure consistent UI updates
                        mHandler.postDelayed(this, 35);
                    } catch (Exception e) {
                        Toast.makeText(MainActivity.this, "Timer Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        Log.e("MainActivity", "visualization timer failed", e);
                    }
                }
            };
            mHandler.postDelayed(mTimer, 1000);
        } catch (Exception e) {
            Toast.makeText(this, "Start Visualization Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            Log.e("MainActivity", "startAudioVisualization failed", e);
        }
    }

    private void stopAudioVisualization() {
        try {
            if (mTimer != null) {
                mHandler.removeCallbacks(mTimer);
                mTimer = null;
            }
        } catch (Exception e) {
            Toast.makeText(this, "Stop Visualization Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            Log.e("MainActivity", "stopAudioVisualization failed", e);
        }
    }
}
