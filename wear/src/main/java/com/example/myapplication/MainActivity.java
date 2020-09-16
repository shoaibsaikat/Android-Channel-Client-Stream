package com.example.myapplication;

import android.os.Bundle;
import android.support.wearable.activity.WearableActivity;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.wearable.ChannelClient;
import com.google.android.gms.wearable.Wearable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class MainActivity extends WearableActivity {

    private static final String TAG = "CUSTOM_TAG";
    private static final String CHANNEL_MSG = "com.example.android.wearable.datalayer.channelmessage";

    private ExecutorService executorService;
    private TextView tvMessage;
    private EditText etMessage;

    private InputStream is;
    private OutputStream os;

    private ChannelClient.Channel connectedChannel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        tvMessage = findViewById(R.id.tvMessage);
        etMessage = findViewById(R.id.etMessage);

        executorService = new ThreadPoolExecutor(4, 5, 60L, TimeUnit.SECONDS, new LinkedBlockingDeque<Runnable>());

        // Enables Always-on
        setAmbientEnabled();
    }

    @Override
    protected void onResume() {
        super.onResume();

        Wearable.getChannelClient(getApplicationContext()).registerChannelCallback(new ChannelClient.ChannelCallback() {
            @Override
            public void onChannelOpened(@NonNull ChannelClient.Channel channel) {
                super.onChannelOpened(channel);
                Log.d(TAG, "onChannelOpened");
                connectedChannel = channel;
                if (channel != null) {
                    addOutputListener(channel);
                    addInputListener(channel);
                } else {
                    Log.d(TAG, "connectedChannel is null");
                }
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();

        try {
            if (is != null)
                is.close();
            if (os != null)
                os.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (connectedChannel != null)
            Wearable.getChannelClient(getApplicationContext()).close(connectedChannel);
    }

    private void addOutputListener(ChannelClient.Channel channel) {
        Task<OutputStream> outputStreamTask = Wearable.getChannelClient(getApplicationContext()).getOutputStream(channel);
        outputStreamTask.addOnSuccessListener(new OnSuccessListener<OutputStream>() {
            @Override
            public void onSuccess(OutputStream outputStream) {
                Log.d(TAG, "output stream onSuccess");
                os = outputStream;
            }
        });
    }

    private void addInputListener(ChannelClient.Channel channel) {
        Task<InputStream> inputStreamTask = Wearable.getChannelClient(getApplicationContext()).getInputStream(channel);
        inputStreamTask.addOnSuccessListener(new OnSuccessListener<InputStream>() {
            @Override
            public void onSuccess(final InputStream inputStream) {
                is = inputStream;
                executorService.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                            int read;
                            byte[] data = new byte[1024];
                            while ((read = is.read(data, 0, data.length)) != -1) {
                                Log.d(TAG, "data length " + read); /** use Log.e to force print, if Log.d does not work */
                                buffer.write(data, 0, read);

                                buffer.flush();
                                byte[] byteArray = buffer.toByteArray();

                                final String text = new String(byteArray, StandardCharsets.UTF_8);
                                Log.d(TAG, "reading: " + text);
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        tvMessage.setText(text);
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

    public void onSend(View view) {
        final String text = etMessage.getText().toString();
        if (os != null) {
            Log.d(TAG, "sending message");
            try {
                os.write(text.getBytes());
                os.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            Log.e(TAG, "output stream is null");
        }
    }
}
