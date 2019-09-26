package com.example.demo06_service;

import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import java.util.Hashtable; // HashTable is threadsafe
import java.util.Iterator;

// singleton class
public class AmbientSounds extends Hashtable<String, Hashtable<String, Double>> {
    private static AmbientSounds sharedInstance = new AmbientSounds();
    private static String TAG = "AmbientSounds";

    private AmbientSounds(){
        super();
    }

    public static AmbientSounds getSharedInstance() {
        return sharedInstance;
    }

    public void resetAmbientSounds() {
        sharedInstance.clear();
    }

    public void readAmbientFromDB(DatabaseReference mDatabaseRef, boolean refresh) {
        if (refresh) {
            mDatabaseRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    readAmbientFromDBHelper(dataSnapshot);
                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {
                    Log.w(TAG, "loadPost:onCancelled", databaseError.toException());
                }
            });
        }
        else {
            mDatabaseRef.addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    readAmbientFromDBHelper(dataSnapshot);
                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {
                    Log.w(TAG, "loadPost:onCancelled", databaseError.toException());
                }
            });
        }
    }

    private void readAmbientFromDBHelper(DataSnapshot dataSnapshot) {
        // empty the data model
        resetAmbientSounds();

        // for every set of parameter
        Iterator<DataSnapshot> recordingSet = dataSnapshot.getChildren().iterator();
        while(recordingSet.hasNext()) {
            DataSnapshot item = recordingSet.next();
            String key = item.getKey();

            // for every timestamp-amplitude pair
            Hashtable<String, Double> amplitudes = new Hashtable<>();
            Iterator<DataSnapshot> amplitudesRaw = item.getChildren().iterator();
            while(amplitudesRaw.hasNext()) {
                DataSnapshot ampPair = amplitudesRaw.next();
                String timestamp = ampPair.getKey();
                Double amp = Double.parseDouble(ampPair.getValue().toString());
                amplitudes.put(timestamp, amp);
            }
            sharedInstance.put(key, amplitudes);
        }
    }
}
