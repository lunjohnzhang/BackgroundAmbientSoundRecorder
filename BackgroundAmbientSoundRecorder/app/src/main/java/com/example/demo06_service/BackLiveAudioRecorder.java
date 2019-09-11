package com.example.demo06_service;

import android.app.Service;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.util.Log;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

public class BackLiveAudioRecorder extends Service {

    // constants
    private static final String TAG = "BackLiveAudioRecorder";
    final int SAMPLE_RATE = 44100; // The sampling rate
    private String url = "https://ohmni-android-comm.herokuapp.com/android";

    // variables
    AudioRecord audioRecord;
    int bufferSize;
    private Looper serviceLooper;
    private ServiceHandler serviceHandler;
    private String testKey = ""; // key of current test
    private Map<Date, Double> currAmbient = Collections.synchronizedMap(new LinkedHashMap<Date, Double>()); // a synchronized and ordered hashtable
    RequestQueue queue;

    // firebase database
    private DatabaseReference mDatabaseRef = FirebaseDatabase.getInstance().getReference().child("ambient");

    // actual data
    AmbientSounds ambientSounds = AmbientSounds.getSharedInstance();

    // ******** Handler that receives messages from the thread ********
    private final class ServiceHandler extends Handler {
        public ServiceHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            Log.d(TAG, "audio recorder is on");
            audioRecord.startRecording();
            while (true) {
                if(audioRecord != null) {
                    readAudioBuffer();
                }
                else {
                    break;
                }
            }
        }
    }

    // ************ Helper functions ************

    // read in the buffered audio and calculate volume
    private void readAudioBuffer() {
        try {
            short[] buffer = new short[bufferSize];

            int bufferReadResult;
            double lastLevel;
            if (audioRecord != null) {
                // Sense the voice
                bufferReadResult = audioRecord.read(buffer, 0, bufferSize);
                double sumLevel = 0;
                for (int i = 0; i < bufferReadResult; i++) {
                    sumLevel += Math.abs(buffer[i]);
                }
                lastLevel = Math.abs((sumLevel / bufferReadResult));
                String timestamp = getCurrentTimeStamp();
                Log.d(TAG, timestamp  + " " + lastLevel);

                // add timestamp-noiseLevel pair to the hashtable
                if(!Double.isNaN(lastLevel)) {
                    currAmbient.put(getCurrentTime(), lastLevel);

                    // send the data to the heroku server and then to the front end
                    JSONObject jsonObject = new JSONObject();
                    try {
                        jsonObject.put(timestamp, lastLevel);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    sendJsonData(jsonObject);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // get timestamp
    public static String getCurrentTimeStamp(){
        try {
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSS z");
            dateFormat.setTimeZone(Calendar.getInstance().getTimeZone());
            String currentDateTime = dateFormat.format(new Date()); // Find today's date
            return currentDateTime;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // convert Date object to Date string
    public static String DateToDateString(Date d) {
        try {
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");
            dateFormat.setTimeZone(Calendar.getInstance().getTimeZone());
            String currentDateTime = dateFormat.format(d); // Find today's date
            return currentDateTime;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static String DateToDateStringWithMS(Date d) {
        try {
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSS z");
            dateFormat.setTimeZone(Calendar.getInstance().getTimeZone());
            String currentDateTime = dateFormat.format(d); // Find today's date
            return currentDateTime;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // get current date object
    public static Date getCurrentTime(){
        return new Date();
    }

    public synchronized Hashtable<String, Double> smoothAmbientNoiseData(Map<Date, Double> currAmbient) {
        Hashtable<Date, Double> raw = new Hashtable<>(currAmbient);
        Hashtable<String, Double> smoothed = new Hashtable<>();
        Iterator iter = raw.entrySet().iterator();
        while(iter.hasNext()) {
            // store values of current second
            ArrayList<Double> currSecond = new ArrayList<>();

            // obtain current date
            Map.Entry ele = (Map.Entry) iter.next();
            Date d = (Date) ele.getKey();

//            Log.d(TAG, "current date: " + DateToDateStringWithMS(d));

            currSecond.add((Double)ele.getValue());

            // obtain the next date, if any
            if(iter.hasNext()) {
                ele = (Map.Entry)iter.next();
                Date nextDate = (Date)ele.getKey();
//                Log.d(TAG, "next date: " + DateToDateStringWithMS(nextDate));


                while(iter.hasNext() && getDateDiffInMiliSeconds(d, nextDate) <= 1000) {
                    Log.d(TAG, "current date: " + DateToDateStringWithMS(d));
                    Log.d(TAG, "next date: " + DateToDateStringWithMS(nextDate));
                    Log.d(TAG, "difference = " + getDateDiffInMiliSeconds(d, nextDate));

                    currSecond.add((Double)ele.getValue());

                    // obtain the next date
                    ele = (Map.Entry)iter.next();
                    nextDate = (Date)ele.getKey();
                }
            }

            // calculate average
            Double averageAmbient = 0.0;
            for(int i=0; i<currSecond.size(); i++) {
                averageAmbient += currSecond.get(i);
                Log.d(TAG, i + ": " + currSecond.get(i).toString());
            }
            averageAmbient /= currSecond.size();

            // put the average into the hashtable tobe appended
            smoothed.put(DateToDateString(d), averageAmbient);
        }

        return smoothed;
    }

    public static double getDateDiffInMiliSeconds(Date d1, Date d2) {
        return Math.abs(d2.getTime() - d1.getTime());
    }

    private void sendJsonData(JSONObject data) {
        JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, url, data,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        Log.d(TAG, response.toString());
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {

                    }
                }
        );
        queue.add(request);
    }

    // ********* overridden functions of Service class
    @Override
    public void onCreate() {
        super.onCreate();

        // set up the audio recorder
        bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        audioRecord = new AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.DEFAULT)
                .setAudioFormat(new AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .build())
                .setBufferSizeInBytes(bufferSize)
                .build();

        // set up json message queue
        queue = Volley.newRequestQueue(BackLiveAudioRecorder.this);

        // create the thread
        HandlerThread thread = new HandlerThread("ServiceStartArguments",
                Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();

        // Get the HandlerThread's Looper and use it for our Handler
        serviceLooper = thread.getLooper();
        serviceHandler = new ServiceHandler(serviceLooper);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // obtaint the key
        testKey = intent.getStringExtra(MainActivity.TEST_KEY);

        Message msg = serviceHandler.obtainMessage();
        msg.arg1 = startId;
        serviceHandler.sendMessage(msg);
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        audioRecord.stop();
        audioRecord.release();
        audioRecord = null;

        // upload data to database
        if(ambientSounds != null) {
            // add current testing data to the data model

            // if test key is not specified, generate a warning
            if(testKey.isEmpty()) {
                Log.w(TAG, "key is not specified");
            }
            else {
                // smooth out the data --> calculate average of every second
                Hashtable<String, Double> smoothedAmbient = smoothAmbientNoiseData(currAmbient);

                // push the data to the database
                ambientSounds.put(testKey, smoothedAmbient);

                mDatabaseRef.setValue(ambientSounds);
                Log.d(TAG, "Testing data uploaded to database");
            }
        }
    }
}
