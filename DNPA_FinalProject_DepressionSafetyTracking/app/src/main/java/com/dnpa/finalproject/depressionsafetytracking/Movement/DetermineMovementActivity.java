/*
 * Copyright 2012 Greg Milette and Adam Stroud
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * 		http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.dnpa.finalproject.depressionsafetytracking.Movement;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.Engine;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ToggleButton;

import androidx.appcompat.app.AppCompatActivity;

import com.androidplot.xy.BoundaryMode;
import com.androidplot.xy.XYPlot;
import com.dnpa.finalproject.depressionsafetytracking.R;

import java.io.File;
import java.util.HashMap;
import java.util.List;

/**
 * Determines when the device has been moved and notifies the user.
 * 
 * @author Adam Stroud &#60;<a href="mailto:adam.stroud@gmail.com">adam.stroud@gmail.com</a>&#62;
 */
public class DetermineMovementActivity extends AppCompatActivity
{
    private static final String TAG = "DetermineMovementActivity";
    private static final int RATE = SensorManager.SENSOR_DELAY_NORMAL;
    private static final String USE_HIGH_PASS_FILTER_PREFERENCE_KEY =
            "USE_HIGH_PASS_FILTER_PREFERENCE_KEY";
    private static final String USE_TTS_NOTIFICATION_PREFERENCE_KEY =
            "USE_TTS_NOTIFICATION_PREFERENCE_KEY";
    private static final String SELECTED_SENSOR_TYPE_PREFERENCE_KEY =
            "SELECTED_SENSOR_TYPE_PREFERENCE_KEY";
    private static final int TTS_STREAM = AudioManager.STREAM_NOTIFICATION;
    
    private SensorManager sensorManager;
    private TextToSpeech tts;
    private RadioGroup sensorSelector;
    private int selectedSensorType;
    private boolean readingAccelerationData;
    private SharedPreferences preferences;
    private AccelerationEventListener accelerometerListener;
    private AccelerationEventListener linearAccelerationListener;
    private boolean useTtsNotification;
    private boolean useHighPassFilter;
    private XYPlot xyPlot;
    private CheckBox ttsNotificationsCheckBox;
    private CheckBox highPassFilterCheckBox;
    private HashMap<String, String> ttsParams;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        super.setContentView(R.layout.determine_movement);
        
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        
        ttsParams = new HashMap<String, String>();
        ttsParams.put(Engine.KEY_PARAM_STREAM, String.valueOf(TTS_STREAM));
        
        this.setVolumeControlStream(TTS_STREAM);
        
        sensorSelector = (RadioGroup)findViewById(R.id.sensorSelector);
        ttsNotificationsCheckBox = (CheckBox)findViewById(R.id.ttsNotificationsCheckBox);
        highPassFilterCheckBox = (CheckBox)findViewById(R.id.highPassFilterCheckBox);
        
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        
        readingAccelerationData = false;
        
        preferences = getPreferences(MODE_PRIVATE);
        
        useHighPassFilter =
                true;
        useHighPassFilter =
                preferences.getBoolean(USE_HIGH_PASS_FILTER_PREFERENCE_KEY,
                                       useHighPassFilter);
        ((CheckBox)findViewById(R.id.highPassFilterCheckBox)).setChecked(useHighPassFilter);
        
        useTtsNotification =
                true;
        useTtsNotification =
                preferences.getBoolean(USE_TTS_NOTIFICATION_PREFERENCE_KEY,
                                       useTtsNotification);
        ((CheckBox)findViewById(R.id.ttsNotificationsCheckBox)).setChecked(useTtsNotification);
        
        selectedSensorType =
                preferences.getInt(SELECTED_SENSOR_TYPE_PREFERENCE_KEY,
                                   Sensor.TYPE_ACCELEROMETER);
        
        if (selectedSensorType == Sensor.TYPE_ACCELEROMETER)
        {
            ((RadioButton)findViewById(R.id.accelerometer)).setChecked(true);
        }
        else
        {
            ((RadioButton)findViewById(R.id.linearAcceleration)).setChecked(true);
        }
        
        xyPlot = (XYPlot)findViewById(R.id.XYPlot);
        xyPlot.setDomainLabel("Elapsed Time (ms)");
        xyPlot.setRangeLabel("Acceleration (m/sec^2)");
        xyPlot.setBorderPaint(null);
        xyPlot.disableAllMarkup();
        xyPlot.setRangeBoundaries(-10, 10, BoundaryMode.FIXED);
    }

    @Override
    protected void onPause()
    {
        super.onPause();
        stopReadingAccelerationData();
        
        if (tts != null)
        {
            tts.shutdown();
        }
    }

    /*
    @Override
    public void onSuccessfulInit(TextToSpeech tts)
    {
        super.onSuccessfulInit(tts);
        this.tts = tts;
    }

     */

    public void onSensorSelectorClick(View view)
    {
        int selectedSensorId = sensorSelector.getCheckedRadioButtonId(); 
        if (selectedSensorId == R.id.accelerometer)
        {
            selectedSensorType = Sensor.TYPE_ACCELEROMETER;
        }
        else if (selectedSensorId == R.id.linearAcceleration)
        {
            selectedSensorType = Sensor.TYPE_LINEAR_ACCELERATION;
        }
        
        preferences
            .edit()
            .putInt(SELECTED_SENSOR_TYPE_PREFERENCE_KEY, selectedSensorType)
            .commit();
    }

    public void onReadAccelerationDataToggleButtonClicked(View view)
    {
        ToggleButton toggleButton = (ToggleButton)view;
        
        if (toggleButton.isChecked())
        {
            startReadingAccelerationData();
        }
        else
        {
            stopReadingAccelerationData();
        }
    }

    @SuppressLint("LongLogTag")
    private void startReadingAccelerationData()
    {
        if (!readingAccelerationData)
        {
            // Clear any plot that may already exist on the chart
            xyPlot.clear();
            xyPlot.redraw();
            
            // Disable UI components so they cannot be changed while plotting
            // sensor data
            for (int i = 0; i < sensorSelector.getChildCount(); i++)
            {
                sensorSelector.getChildAt(i).setEnabled(false);
            }
            ttsNotificationsCheckBox.setEnabled(false);
            highPassFilterCheckBox.setEnabled(false);
            
            // Data files are stored on the external cache directory so they can
            // be pulled off of the device by the user
            File accelerometerDataFile =
                    new File(getExternalCacheDir(), "accelerometer.csv");
            File linearAcceclerationDataFile =
                    new File(getExternalCacheDir(), "linearAcceleration.csv");
            
            if (selectedSensorType == Sensor.TYPE_ACCELEROMETER)
            {
                xyPlot.setTitle("Sensor.TYPE_ACCELEROMETER");
                accelerometerListener =
                        new AccelerationEventListener(xyPlot,
                                useHighPassFilter,
                                accelerometerDataFile,
                                (useTtsNotification ? tts : null),
                                ttsParams,
                                "Movement Detected");
                
                linearAccelerationListener =
                        new AccelerationEventListener(null,
                                useHighPassFilter,
                                linearAcceclerationDataFile,
                                (useTtsNotification ? tts : null),
                                ttsParams,
                                "Movement Detected");
            }
            else
            {
                xyPlot.setTitle("Sensor.TYPE_LINEAR_ACCELERATION");
                accelerometerListener =
                        new AccelerationEventListener(null,
                                useHighPassFilter,
                                accelerometerDataFile,
                                (useTtsNotification ? tts : null),
                                ttsParams,
                                "Movement Detected");
                
                linearAccelerationListener =
                        new AccelerationEventListener(xyPlot,
                                useHighPassFilter,
                                linearAcceclerationDataFile,
                                (useTtsNotification ? tts : null),
                                ttsParams,
                                "Movement Detected");
            }
            
            sensorManager.registerListener(accelerometerListener,
                    sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                    RATE);
            
            sensorManager.registerListener(linearAccelerationListener,
                    sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION),
                    RATE);
            
            readingAccelerationData = true;
            
            Log.d(TAG, "Started reading acceleration data");
        }
    }

    @SuppressLint("LongLogTag")
    private void stopReadingAccelerationData()
    {
        if (readingAccelerationData)
        {
            // Re-enable sensor and options UI views
            for (int i = 0; i < sensorSelector.getChildCount(); i++)
            {
                sensorSelector.getChildAt(i).setEnabled(true);
            }
            ttsNotificationsCheckBox.setEnabled(true);
            highPassFilterCheckBox.setEnabled(true);
            
            sensorManager.unregisterListener(accelerometerListener);
            sensorManager.unregisterListener(linearAccelerationListener);
            
            // Tell listeners to clean up after themselves
            accelerometerListener.stop();
            linearAccelerationListener.stop();
            
            readingAccelerationData = false;
            
            Log.d(TAG, "Stopped reading acceleration data");
        }
    }
    
    public void onTtsNotificationsCheckBoxClicked(View view)
    {
        useTtsNotification = ((CheckBox)view).isChecked();
        preferences
            .edit()
            .putBoolean(USE_TTS_NOTIFICATION_PREFERENCE_KEY, useTtsNotification)
            .commit();
    }

    public void onHighPassFilterCheckBoxClicked(View view)
    {
       useHighPassFilter = ((CheckBox)view).isChecked();
        preferences
            .edit()
            .putBoolean(USE_HIGH_PASS_FILTER_PREFERENCE_KEY, useHighPassFilter)
            .commit();
    }

    protected void receiveWhatWasHeard(List<String> heard, float[] confidenceScores)
    {
        // no-op
    }
}
