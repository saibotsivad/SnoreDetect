package com.davistobias.snoredetect;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.os.Environment;
import androidx.core.app.NotificationCompat;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

public class AudioRecordingService extends Service {
    
    private static final String TAG = "AudioRecordingService";
    private static final String CHANNEL_ID = "SnoreDetectChannel";
    private static final int NOTIFICATION_ID = 1;
    
    // Audio recording constants
    private static final int RECORDER_SAMPLERATE = 8000;
    private static final int RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_MONO;
    private static final int RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    private static final int BufferElements2Rec = 1024;
    private static final int BytesPerElement = 2;
    
    // Recording state
    private AudioRecord recorder = null;
    private Thread recordingThread = null;
    private boolean isRecording = false;
    
    // Audio data processing
    private Handler dataHandler = new Handler(Looper.getMainLooper());
    private AudioDataCallback audioDataCallback;
    
    // Service binding
    private final IBinder binder = new AudioRecordingBinder();
    
    public interface AudioDataCallback {
        void onAudioData(double decibel);
        void onRecordingStarted();
        void onRecordingStopped();
        void onRecordingError(String error);
    }
    
    public class AudioRecordingBinder extends Binder {
        AudioRecordingService getService() {
            return AudioRecordingService.this;
        }
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        Log.d(TAG, "AudioRecordingService created");
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        
        if ("STOP_RECORDING".equals(action)) {
            stopRecording();
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        
        startForeground(NOTIFICATION_ID, createNotification());
        Log.d(TAG, "AudioRecordingService started in foreground");
        
        return START_STICKY; // Restart if killed by system
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        stopRecording();
        Log.d(TAG, "AudioRecordingService destroyed");
    }
    
    @Override
    public void onTimeout() {
        // Handle Android 15 6-hour timeout for mediaProcessing services
        Log.w(TAG, "Service timeout reached - stopping recording");
        if (audioDataCallback != null) {
            audioDataCallback.onRecordingError("Recording stopped due to 6-hour timeout limit");
        }
        stopRecording();
        stopForeground(true);
        stopSelf();
    }
    
    public void setAudioDataCallback(AudioDataCallback callback) {
        this.audioDataCallback = callback;
    }
    
    public boolean startRecording() {
        if (isRecording) {
            Log.w(TAG, "Recording already in progress");
            return false;
        }
        
        try {
            recorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
                    RECORDER_SAMPLERATE, RECORDER_CHANNELS,
                    RECORDER_AUDIO_ENCODING, BufferElements2Rec * BytesPerElement);
            
            if (recorder.getState() != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord initialization failed");
                if (audioDataCallback != null) {
                    audioDataCallback.onRecordingError("Failed to initialize audio recorder");
                }
                return false;
            }
            
            recorder.startRecording();
            isRecording = true;
            
            recordingThread = new Thread(this::writeAudioDataToFile, "AudioRecorder Thread");
            recordingThread.start();
            
            updateNotification("Recording snore data...");
            
            if (audioDataCallback != null) {
                audioDataCallback.onRecordingStarted();
            }
            
            Log.i(TAG, "Recording started successfully");
            return true;
            
        } catch (SecurityException e) {
            Log.e(TAG, "Permission denied for audio recording", e);
            if (audioDataCallback != null) {
                audioDataCallback.onRecordingError("Permission denied for audio recording");
            }
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Failed to start recording", e);
            if (audioDataCallback != null) {
                audioDataCallback.onRecordingError("Failed to start recording: " + e.getMessage());
            }
            return false;
        }
    }
    
    public void stopRecording() {
        if (!isRecording) {
            return;
        }
        
        isRecording = false;
        
        if (recorder != null) {
            try {
                recorder.stop();
                recorder.release();
            } catch (Exception e) {
                Log.e(TAG, "Error stopping recorder", e);
            }
            recorder = null;
        }
        
        if (recordingThread != null) {
            try {
                recordingThread.join(1000); // Wait up to 1 second
            } catch (InterruptedException e) {
                Log.w(TAG, "Interrupted while waiting for recording thread", e);
            }
            recordingThread = null;
        }
        
        updateNotification("Recording stopped");
        
        if (audioDataCallback != null) {
            audioDataCallback.onRecordingStopped();
        }
        
        Log.i(TAG, "Recording stopped");
    }
    
    public boolean isRecording() {
        return isRecording;
    }
    
    private File getAudioFile() {
        File audioDir = null;
        
        // Try to use external files directory first (preferred for API 29+)
        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            audioDir = getExternalFilesDir(Environment.DIRECTORY_MUSIC);
        }
        
        // Fallback to internal storage if external storage is not available
        if (audioDir == null || !audioDir.exists()) {
            audioDir = new File(getFilesDir(), "audio");
        }
        
        // Create directory if it doesn't exist
        if (!audioDir.exists()) {
            boolean created = audioDir.mkdirs();
            if (!created) {
                Log.e(TAG, "Failed to create audio directory: " + audioDir.getPath());
                // Final fallback to files directory
                audioDir = getFilesDir();
            }
        }
        
        return new File(audioDir, "8k16bitMono.pcm");
    }
    
    private byte[] short2byte(short[] sData) {
        int shortArrsize = sData.length;
        byte[] bytes = new byte[shortArrsize * 2];

        for (int i = 0; i < shortArrsize; i++) {
            bytes[i * 2] = (byte) (sData[i] & 0x00FF);
            bytes[(i * 2) + 1] = (byte) (sData[i] >> 8);
            sData[i] = 0;
        }
        return bytes;
    }
    
    private void writeAudioDataToFile() {
        short sData[] = new short[BufferElements2Rec];
        File audioFile = getAudioFile();

        FileOutputStream os = null;
        try {
            os = new FileOutputStream(audioFile);
            Log.i(TAG, "Audio file will be saved to: " + audioFile.getAbsolutePath());
        } catch (FileNotFoundException e) {
            Log.e(TAG, "Failed to create audio file: " + audioFile.getAbsolutePath(), e);
            if (audioDataCallback != null) {
                audioDataCallback.onRecordingError("Failed to create audio file");
            }
            return;
        }

        while (isRecording) {
            if (recorder == null) {
                break;
            }
            
            int bytesRead = recorder.read(sData, 0, BufferElements2Rec);
            if (bytesRead < 0) {
                Log.e(TAG, "Error reading audio data: " + bytesRead);
                break;
            }

            double maxSample = 0d;
            for (int x = 0; x < BufferElements2Rec; x++) {
                double sample = Math.abs(sData[x]);
                if (sample > maxSample) {
                    maxSample = sample;
                }
            }
            
            // Calculate decibel level
            double decibel = 0d;
            if (maxSample > 0) {
                decibel = 20.0 * Math.log10(maxSample / 65535.0);
            }
            
            // Send audio data to callback on main thread
            final double finalDecibel = decibel;
            dataHandler.post(() -> {
                if (audioDataCallback != null) {
                    audioDataCallback.onAudioData(finalDecibel);
                }
            });

            try {
                // Write audio data to file
                byte bData[] = short2byte(sData);
                os.write(bData, 0, BufferElements2Rec * BytesPerElement);
            } catch (IOException e) {
                Log.e(TAG, "Error writing audio data", e);
                break;
            }
        }

        if (os != null) {
            try {
                os.close();
                Log.i(TAG, "Audio file saved successfully");
            } catch (IOException e) {
                Log.e(TAG, "Failed to close audio file", e);
            }
        }
    }
    
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Snore Detection Recording",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Notification for snore detection audio recording");
            channel.setSound(null, null); // Silent notification
            
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }
    
    private Notification createNotification() {
        return createNotificationWithText("Snore detection ready");
    }
    
    private void updateNotification(String text) {
        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        notificationManager.notify(NOTIFICATION_ID, createNotificationWithText(text));
    }
    
    private Notification createNotificationWithText(String text) {
        Intent mainIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, mainIntent, 
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0);
        
        Intent stopIntent = new Intent(this, AudioRecordingService.class);
        stopIntent.setAction("STOP_RECORDING");
        PendingIntent stopPendingIntent = PendingIntent.getService(this, 0, stopIntent, 
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0);
        
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("SnoreDetect")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_media_play) // Using system icon since we don't have custom ones
                .setContentIntent(pendingIntent)
                .addAction(android.R.drawable.ic_media_pause, "Stop", stopPendingIntent)
                .setOngoing(true)
                .setSilent(true)
                .build();
    }
}