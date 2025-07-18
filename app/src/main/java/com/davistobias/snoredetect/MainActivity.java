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

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.components.LimitLine;
import android.graphics.Color;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.Buffer;
import java.util.ArrayList;

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
    private LineChart chart;
    private LineDataSet dataSet;
    private LineData lineData;
    private float graphLastXValue = 0f;
    private final int MAX_DATA_POINTS = 100; // Limit for memory management
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

            // Initialize MPAndroidChart for real-time audio visualization
            chart = findViewById(R.id.graph);
            setupChart();

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
                        Toast.makeText(MainActivity.this, "Starting...", Toast.LENGTH_SHORT).show();
                        if (requestAudioFocus()) {
                            enableButtons(true);
                            startRecording();
                        } else {
                            Toast.makeText(MainActivity.this, "Cannot start recording - audio focus denied", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(MainActivity.this, "Requesting permission", Toast.LENGTH_SHORT).show();
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
                    // Skip WRITE_EXTERNAL_STORAGE check on API 33+ (Android 13+)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && 
                        permission.equals(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                        Log.d("MainActivity", "Skipping WRITE_EXTERNAL_STORAGE on API " + Build.VERSION.SDK_INT);
                        continue;
                    }
                    
                    // Skip POST_NOTIFICATIONS check on API < 33 (it doesn't exist before Android 13)
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU && 
                        permission.equals(Manifest.permission.POST_NOTIFICATIONS)) {
                        Log.d("MainActivity", "Skipping POST_NOTIFICATIONS on API " + Build.VERSION.SDK_INT);
                        continue;
                    }
                    
                    int permissionResult = ContextCompat.checkSelfPermission(this, permission);
                    Log.d("MainActivity", "Permission " + permission + " result: " + 
                        (permissionResult == PackageManager.PERMISSION_GRANTED ? "GRANTED" : "DENIED"));
                    
                    if (permissionResult != PackageManager.PERMISSION_GRANTED) {
                        Log.w("MainActivity", "Missing permission: " + permission);
                        return false;
                    }
                }
            }
            Log.d("MainActivity", "All required permissions granted");
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
                java.util.List<String> permissionsToRequest = new java.util.ArrayList<>();
                
                // Always need RECORD_AUDIO
                permissionsToRequest.add(Manifest.permission.RECORD_AUDIO);
                
                // Add WRITE_EXTERNAL_STORAGE only on API < 33
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
                }
                
                // Add POST_NOTIFICATIONS only on API 33+
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS);
                }
                
                String[] permissionsArray = permissionsToRequest.toArray(new String[0]);
                Log.d("MainActivity", "Requesting permissions: " + java.util.Arrays.toString(permissionsArray));
                ActivityCompat.requestPermissions(this, permissionsArray, PERMISSIONS_REQUEST_RECORD_AUDIO);
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
                    // graphLastXValue is now updated in updateChart method
                    if (decibel >= -30.0) {
                        textView.setText("SNORING");
                    } else {
                        textView.setText("NORMAL");
                    }
                    updateChart((float) decibel);
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

    private void setupChart() {
        try {
            if (chart == null) {
                Toast.makeText(this, "Chart Setup Error: Chart is null", Toast.LENGTH_SHORT).show();
                return;
            }

            // Configure chart appearance
            chart.getDescription().setEnabled(false);
            chart.setTouchEnabled(true);
            chart.setDragEnabled(true);
            chart.setScaleEnabled(false);
            chart.setPinchZoom(false);
            chart.setDrawGridBackground(false);
            chart.setBackgroundColor(Color.BLACK);

            // Configure X-axis (time)
            XAxis xAxis = chart.getXAxis();
            xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
            xAxis.setTextColor(Color.WHITE);
            xAxis.setDrawGridLines(true);
            xAxis.setGridColor(Color.GRAY);
            xAxis.setAxisMinimum(0f);
            xAxis.setAxisMaximum(100f);

            // Configure Y-axis (decibels)
            YAxis leftAxis = chart.getAxisLeft();
            leftAxis.setTextColor(Color.WHITE);
            leftAxis.setDrawGridLines(true);
            leftAxis.setGridColor(Color.GRAY);
            leftAxis.setAxisMaximum(0f);    // 0 dB max
            leftAxis.setAxisMinimum(-60f);  // -60 dB min

            // Add snoring threshold line at -30.0 dB
            LimitLine snoreThreshold = new LimitLine(-30f, "Snoring Threshold");
            snoreThreshold.setLineColor(Color.RED);
            snoreThreshold.setLineWidth(2f);
            snoreThreshold.setTextColor(Color.RED);
            snoreThreshold.setTextSize(12f);
            leftAxis.addLimitLine(snoreThreshold);

            // Disable right axis
            chart.getAxisRight().setEnabled(false);

            // Initialize data
            dataSet = new LineDataSet(new ArrayList<Entry>(), "Audio Level (dB)");
            dataSet.setColor(Color.GREEN);
            dataSet.setCircleColor(Color.GREEN);
            dataSet.setLineWidth(2f);
            dataSet.setCircleRadius(1f);
            dataSet.setDrawCircleHole(false);
            dataSet.setValueTextSize(0f); // Hide value labels
            dataSet.setDrawValues(false);
            dataSet.setMode(LineDataSet.Mode.LINEAR);

            lineData = new LineData(dataSet);
            chart.setData(lineData);
            chart.invalidate();

            Log.d("MainActivity", "Chart setup completed successfully");
        } catch (Exception e) {
            Toast.makeText(this, "Chart Setup Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            Log.e("MainActivity", "setupChart failed", e);
        }
    }

    private void updateChart(float decibelValue) {
        try {
            if (chart == null || dataSet == null) {
                return;
            }

            // Add new data point
            graphLastXValue += 1f;
            dataSet.addEntry(new Entry(graphLastXValue, decibelValue));

            // Remove old data points if we exceed maximum
            if (dataSet.getEntryCount() > MAX_DATA_POINTS) {
                dataSet.removeFirst();
                
                // Shift X-axis to show recent data
                XAxis xAxis = chart.getXAxis();
                xAxis.setAxisMinimum(graphLastXValue - MAX_DATA_POINTS);
                xAxis.setAxisMaximum(graphLastXValue);
            }

            // Update chart data
            lineData.notifyDataChanged();
            chart.notifyDataSetChanged();
            chart.invalidate(); // Refresh the chart

        } catch (Exception e) {
            Log.e("MainActivity", "updateChart failed", e);
            // Don't show toast here as this is called frequently
        }
    }
}
