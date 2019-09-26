package com.example.demo06_service;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.Hashtable;
import java.util.Iterator;

public class MainActivity extends AppCompatActivity {
    // singleton reference
    AmbientSounds ambientSounds = AmbientSounds.getSharedInstance();

    // variable
    Intent exampleService;
    Intent backLiveAudioRecordService;
    Button startBtn, stopBtn;
    TextInputLayout keyInput;

    // Firebase database
    private DatabaseReference mDatabaseRef = FirebaseDatabase.getInstance().getReference().child("ambient");

    // constants
    final int REQUEST_PERMISSION_CODE = 1000;
    private static final String TAG = "MainActivity";
    public static final String TEST_KEY = "testKey";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // *********** set up UI elements ************
        startBtn = findViewById(R.id.start);
        stopBtn = findViewById(R.id.stop);
        keyInput = findViewById(R.id.keyInput);

        startBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(checkPermissionFromDevice()) {
                    // validate the key input
                    String keyInputText = keyInput.getEditText().getText().toString();
                    if(validateKeyInput(keyInputText)) {
                        backLiveAudioRecordService = new Intent(MainActivity.this, BackLiveAudioRecorder.class);
                        backLiveAudioRecordService.putExtra(TEST_KEY, keyInputText);
                        startService(backLiveAudioRecordService);
                    }
                }

                else {
                    requestPermission();
                }
            }
        });

        stopBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(backLiveAudioRecordService != null) {
                    stopService(backLiveAudioRecordService);

                }
            }
        });


        // ********** set up database **********
        ambientSounds.readAmbientFromDB(mDatabaseRef, false);
        Toast.makeText(MainActivity.this, "Database loaded", Toast.LENGTH_LONG).show();

    }

    // ************ Helper functions **************
    private boolean validateKeyInput(String keyInputText) {


        // check if the key is empty
        if (keyInputText == null || keyInputText.isEmpty()) {
            keyInput.setError("Key cannot be empty");
            return false;
        }

        // check if the key overlap with existing keys
        if(ambientSounds != null && ambientSounds.containsKey(keyInputText)) {
            keyInput.setError("Key already exist! Please enter a new key.");
            return false;
        }

        keyInput.setError(null);
        keyInput.setErrorEnabled(false);
        return true;
    }



    // ************ request permission code **************
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case REQUEST_PERMISSION_CODE:
            {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Permission Granted", Toast.LENGTH_SHORT).show();
                }
                else {
                    Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private void requestPermission() {
        ActivityCompat.requestPermissions(this, new String[]{
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.RECORD_AUDIO
        }, REQUEST_PERMISSION_CODE);
    }

    private boolean checkPermissionFromDevice() {
        int write_external_storage_result = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        int record_audio_result = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO);
        return write_external_storage_result == PackageManager.PERMISSION_GRANTED && record_audio_result == PackageManager.PERMISSION_GRANTED;
    }
}
