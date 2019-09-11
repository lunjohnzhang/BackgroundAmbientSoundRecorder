package com.example.demo06_service;

import java.util.Hashtable; // HashTable is threadsafe

// singleton class
public class AmbientSounds extends Hashtable<String, Hashtable<String, Double>> {
    private static AmbientSounds sharedInstance = new AmbientSounds();

    private AmbientSounds(){
        super();
    }

    public static AmbientSounds getSharedInstance() {
        return sharedInstance;
    }
}
