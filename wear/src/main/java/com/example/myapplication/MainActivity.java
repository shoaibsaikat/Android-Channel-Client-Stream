package com.example.myapplication;

import android.os.Bundle;
import android.support.wearable.activity.WearableActivity;
import android.util.Log;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.wearable.ChannelClient;
import com.google.android.gms.wearable.Wearable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class MainActivity extends WearableActivity {

    private static final String TAG = "CUSTOM";
    private static final String CHANNEL_MSG = "com.example.android.wearable.datalayer.channelmessage";

    private ExecutorService executorService;
    private TextView etMessage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        etMessage = findViewById(R.id.tvMessage);

        executorService = new ThreadPoolExecutor(4, 5, 60L, TimeUnit.SECONDS, new LinkedBlockingDeque<Runnable>());

        Wearable.getChannelClient(getApplicationContext()).registerChannelCallback(new ChannelClient.ChannelCallback() {
            @Override
            public void onChannelOpened(@NonNull ChannelClient.Channel channel) {
                super.onChannelOpened(channel);
                Log.e(TAG, "onChannelOpened");

                Task<InputStream> inputStreamTask = Wearable.getChannelClient(getApplicationContext()).getInputStream(channel);
                inputStreamTask.addOnSuccessListener(new OnSuccessListener<InputStream>() {
                    @Override
                    public void onSuccess(final InputStream inputStream) {
                        Log.e(TAG, "onSuccess");
                        executorService.execute(new Runnable() {
                            @Override
                            public void run() {
                                Log.e(TAG, "running");
                                try {
                                    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                                    int nRead;
                                    byte[] data = new byte[1024];
                                    while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
                                        Log.e(TAG, "data length " + nRead);
                                        buffer.write(data, 0, nRead);

                                        buffer.flush();
                                        byte[] byteArray = buffer.toByteArray();

                                        final String text = new String(byteArray, StandardCharsets.UTF_8);
                                        Log.e(TAG, "reading: " + text);
                                        runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                etMessage.setText(text);
                                            }
                                        });
                                    }
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                        });
                    }
                });
            }
        });

        // Enables Always-on
        setAmbientEnabled();
    }
}
