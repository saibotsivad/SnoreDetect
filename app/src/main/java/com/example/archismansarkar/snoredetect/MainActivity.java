package com.example.archismansarkar.snoredetect;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

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

    private AudioRecord recorder = null;
    private Thread recordingThread = null;
    private boolean isRecording = false;

    private final Handler mHandler = new Handler();
    private Runnable mTimer;
    private LineGraphSeries<DataPoint> mSeries;
    private double graphLastXValue = 0d;
    public double data = 0d;
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

        int bufferSize = AudioRecord.getMinBufferSize(RECORDER_SAMPLERATE,
                RECORDER_CHANNELS, RECORDER_AUDIO_ENCODING);
        
        // Check permissions on startup
        checkPermissions();
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

    int BufferElements2Rec = 1024; // want to play 2048 (2K) since 2 bytes we use only 1024
    int BytesPerElement = 2; // 2 bytes in 16bit format

    private void startRecording() {

        recorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
                RECORDER_SAMPLERATE, RECORDER_CHANNELS,
                RECORDER_AUDIO_ENCODING, BufferElements2Rec * BytesPerElement);

        recorder.startRecording();

        mTimer = new Runnable() {
            @Override
            public void run() {
                graphLastXValue += 1d;
                if(decibel>=-30.0)textView.setText("SNORING");
                else textView.setText("NORMAL");
                mSeries.appendData(new DataPoint(graphLastXValue, decibel), true, 100);
                mHandler.postDelayed(this, 35);
            }
        };
        mHandler.postDelayed(mTimer, 1000);


        isRecording = true;

        recordingThread = new Thread(new Runnable() {

            public void run() {

                writeAudioDataToFile();

            }
        }, "AudioRecorder Thread");
        recordingThread.start();
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
                Log.e("AudioStorage", "Failed to create audio directory: " + audioDir.getPath());
                // Final fallback to files directory
                audioDir = getFilesDir();
            }
        }
        
        return new File(audioDir, "8k16bitMono.pcm");
    }

    //Conversion of short to byte
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
            Log.i("AudioStorage", "Audio file will be saved to: " + audioFile.getAbsolutePath());
        } catch (FileNotFoundException e) {
            Log.e("AudioStorage", "Failed to create audio file: " + audioFile.getAbsolutePath(), e);
            e.printStackTrace();
            return; // Exit early if we can't create the file
        }

        while (isRecording) {
            recorder.read(sData, 0, BufferElements2Rec);

            for(int x=0; x<BufferElements2Rec; x++) {
                Log.d("AudioData ", String.valueOf(sData[x]));

                data = sData[x];

                if (data>0)decibel = 20.0*Math.log10(data/65535.0);
                else if (data<0){
                    data = 0d-data;
                    decibel = 20.0*Math.log10(data/65535.0);
                }
            }

            try {
                // writes the data to file from buffer stores the voice buffer
                byte bData[] = short2byte(sData);

                os.write(bData, 0, BufferElements2Rec * BytesPerElement);

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (os != null) {
            try {
                os.close();
                Log.i("AudioStorage", "Audio file saved successfully");
            } catch (IOException e) {
                Log.e("AudioStorage", "Failed to close audio file", e);
                e.printStackTrace();
            }
        }
    }

    private void stopRecording() {
        // stops the recording activity
        if (null != recorder) {
            isRecording = false;


            recorder.stop();
            recorder.release();

            recorder = null;
            recordingThread = null;

            mHandler.removeCallbacks(mTimer);
        }
    }

    private View.OnClickListener btnClick = new View.OnClickListener() {
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.btnStart: {
                    //Log.i("AudioData ", "Start Pressed");
                    if (hasRequiredPermissions()) {
                        enableButtons(true);
                        startRecording();
                    } else {
                        requestPermissions();
                    }
                    break;
                }
                case R.id.btnStop: {
                    //Log.i("AudioData ", "Stop pressed");
                    enableButtons(false);
                    stopRecording();
                    break;
                }
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
}
