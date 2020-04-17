package com.example.brian.stethotest;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Environment;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;


public class MainActivity extends ActionBarActivity {

    private static final int RECORDER_SAMPLERATE = 16000;
    private static final int RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_MONO;
    private static final int RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    private String filePath;
    private String filteredFilePath;
    private AudioRecord recorder = null;
    private Thread recordingThread = null;
    private boolean isRecording = false;

    byte[] audioBytes;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setButtonHandlers();
        enableButtons(false);

        int bufferSize = AudioRecord.getMinBufferSize(RECORDER_SAMPLERATE,
                RECORDER_CHANNELS, RECORDER_AUDIO_ENCODING);
    }

    private void setButtonHandlers() {
        findViewById(R.id.btnStart).setOnClickListener(btnClick);
        findViewById(R.id.btnStop).setOnClickListener(btnClick);
    }

    private void enableButtons(boolean isRecording) {
        enableButton(R.id.btnStart, !isRecording);
        enableButton(R.id.btnStop, isRecording);
    }

    private void enableButton(int id, boolean isEnable) {
        findViewById(id).setEnabled(isEnable);
    }

    int BufferElements2Rec = 1024; // want to play 2048 (2K) since 2 bytes we use only 1024
    int BytesPerElement = 2; // 2 bytes in 16bit format

    private void startRecording() {

        recorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
                RECORDER_SAMPLERATE, RECORDER_CHANNELS,
                RECORDER_AUDIO_ENCODING, BufferElements2Rec * BytesPerElement);

        recorder.startRecording();
        isRecording = true;
        recordingThread = new Thread(new Runnable() {
            public void run() {
                writeAudioDataToFile();
            }
        }, "AudioRecorder Thread");
        recordingThread.start();
    }

    // Write the output audio in byte[] form
    private void writeAudioDataToFile() {

        filePath = Environment.getExternalStorageDirectory() + "/voice44K16bitmono.pcm";
        short sData[] = new short[BufferElements2Rec];

        FileOutputStream os = null;
        try {
            os = new FileOutputStream(filePath);

            while (isRecording) {
                // gets the voice output from microphone to byte format

                recorder.read(sData, 0, BufferElements2Rec);
                System.out.println("Short writing to file" + sData.toString());
                try {
                    // // writes the data to file from buffer
                    // // stores the voice buffer
                    byte bData[] = short2byte(sData);
                    os.write(bData, 0, BufferElements2Rec * BytesPerElement);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            try {
                os.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    // Write the filtered output audio to WAV
    // However, WAV Header is not implemented yet.
    // TODO see email, link to generating correct header is included
    private void writeFilteredAudioDataToFile(double[] audioBytes) {

        filteredFilePath = Environment.getExternalStorageDirectory() + "/voice44K16bitmono.wav";
        short sData[] = shortMe(audioBytes);

        FileOutputStream os = null;
        try {
            os = new FileOutputStream(filteredFilePath);

            System.out.println("Filtered writing to file" + sData.toString());
            try {
                // // writes the data to file from buffer
                // // stores the voice buffer
                byte bData[] = short2byte(sData);
                os.write(bData);

                System.out.println("Done writing filtered data!");
                os.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
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
        }
    }

    // Take the raw PCM file and create a byte array for filtering
    private void writeByteArray() {
        System.out.println("Preparing to write Byte Array");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        BufferedInputStream in;
        try {
            in = new BufferedInputStream(new FileInputStream(filePath));

            int read = 0;
            byte[] buff = new byte[1024];
            while ((read = in.read(buff)) != -1) {
                System.out.println("writing byte array");
                out.write(buff, 0, read);
            }

            out.flush();
            audioBytes = out.toByteArray();
            System.out.println("Byte array written! " + audioBytes.toString());

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Takes byte[] array converted from reading in raw PCM data, and manually filters it
    // Based on March's Matlab code
    private double[] filterData(byte[] audioBytes) {

        //create the output array to store the filtered data
        double[] y = new double[audioBytes.length];

        //case the byte array audioBytes into double array for calculations
        double[] x = doubleMe(audioBytes);

        /*TODO constants are only applicable for 16000Hz at the moment. See email content for other numbers
          I suggest using case/switch to handle the other sample rates available
          {8000, 11025, 16000, 22050, 44100}
         */
        double[] Num = { 1, 4, 6, 4, 1 }; // Numerator coefficient of the filter
        double[] Den = { 1, 3.7947911031, -5.4051668617, 3.4247473473, -0.8144059977 }; // Denominators for 16000Hz
        double gain = 464993.2749;

        System.out.println("Starting Filtering");     // Debug message, ignore

        // This is March's MATLAB code ported to Java
        // Manually do the IIR formula since the first 5 since you won't have negative index
        y[0]=Num[0]*x[0];
        y[1]=Num[0]*x[1]+Num[1]*x[0]+Den[1]*y[0];
        y[2]=Num[0]*x[2]+Num[1]*x[1]+Num[2]*x[0]+Den[1]*y[1]+Den[2]*y[0];
        y[3]=Num[0]*x[3]+Num[1]*x[2]+Num[2]*x[1]+Num[3]*x[0]+Den[1]*y[2]+Den[2]*y[1]+Den[3]*y[0];
        y[4]=Num[0]*x[4]+Num[1]*x[3]+Num[2]*x[2]+Num[3]*x[1]+Num[4]*x[0]+Den[1]*y[3]+Den[2]*y[2]+Den[3]*y[1]+Den[4]*y[0];
        y[5]=Num[0]*x[5]+Num[1]*x[4]+Num[2]*x[3]+Num[3]*x[2]+Num[4]*x[1]+Den[1]*y[4]+Den[2]*y[3]+Den[3]*y[2]+Den[4]*y[1];
        // put IIR filter formula in for looop
        for (int n = 6; n < x.length; n++) {
            y[n] = (Num[0] * x[n] + Num[1] * x[n-1] + Num[2] * x[n-2] + Num[3] * x[n-3] + Num[4] * x[n-4]
                    + Den[1] * y[n-1] + Den[2] * y[n-2] + Den[3] * y[n-3] + Den[4] * y[n-4]) / gain;
            System.out.println("Total length " + x.length + "; iteration #" + n);
        }

        return y;
    }

    // Sound records from Android device as a byte[] array, so if you need a double[] array to do
    // any reasonable DSP, use this to convert it over.
    public static double[] doubleMe(byte[] bytes) {
        // Convert byte[] array to short[] array (well defined convention, Android records 2
        // consecutive bytes into one short
        short[] shorts = new short[bytes.length / 2]; // will drop last byte if odd number
        ByteBuffer bb = ByteBuffer.wrap(bytes);
        for (int i = 0; i < shorts.length; i++) {
            shorts[i] = bb.getShort();
        }

        // Manually cast each short as double, then return the double[]
        double[] doubles = new double[shorts.length];
        for (int i = 0; i < shorts.length; i++) {
            doubles[i] = shorts[i];
        }
        return doubles;
    }

    // Converting double[] to byte[] takes two steps as well
    // For double[] to short[] we can just manually case each element
    public static short[] shortMe(double[] bytes) {
        short[] out = new short[bytes.length];
        for (int i = 0; i < out.length; i++) {
            out[i] = (short) bytes[i];
        }
        return out;
    }

    // Converting short[] to byte[], Android has a canned method to do so
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

    // Set behaviors of Android UI elements (in this case, just the Start and Stop buttons
    private View.OnClickListener btnClick = new View.OnClickListener() {
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.btnStart: {
                    enableButtons(true);        // Just a UI function, don't worry
                    startRecording();           // Begin recording (incl. writing bytes into PCM)
                    break;
                }
                case R.id.btnStop: {
                    enableButtons(false);       // Just a UI function, don't worry
                    stopRecording();
                    writeByteArray();           // Takes the PCM we recorded and creates a byte[] array

                    // Manually filter double[] array of data, then take that and write to WAV file
                    writeFilteredAudioDataToFile(filterData(audioBytes));
                    break;
                }
            }
        }
    };

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finish();
        }
        return super.onKeyDown(keyCode, event);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
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
