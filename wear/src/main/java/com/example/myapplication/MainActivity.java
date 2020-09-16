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
    private ChannelClient.Channel connectedChannel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        tvMessage = findViewById(R.id.tvMessage);
        etMessage = findViewById(R.id.etMessage);

        executorService = new ThreadPoolExecutor(4, 5, 60L, TimeUnit.SECONDS, new LinkedBlockingDeque<Runnable>());

        Wearable.getChannelClient(getApplicationContext()).registerChannelCallback(new ChannelClient.ChannelCallback() {
            @Override
            public void onChannelOpened(@NonNull ChannelClient.Channel channel) {
                super.onChannelOpened(channel);
                Log.e(TAG, "onChannelOpened");
                addInputListener(channel);
                connectedChannel = channel;
            }
        });

        // Enables Always-on
        setAmbientEnabled();
    }

    private void addInputListener(ChannelClient.Channel channel) {
        Task<InputStream> inputStreamTask = Wearable.getChannelClient(getApplicationContext()).getInputStream(channel);
        inputStreamTask.addOnSuccessListener(new OnSuccessListener<InputStream>() {
            @Override
            public void onSuccess(final InputStream inputStream) {
                Log.d(TAG, "onSuccess");
                executorService.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                            int nRead;
                            byte[] data = new byte[1024];
                            while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
                                Log.d(TAG, "data length " + nRead);
                                buffer.write(data, 0, nRead);

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
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                Task<OutputStream> outputStreamTask = Wearable.getChannelClient(getApplicationContext()).getOutputStream(connectedChannel);
                outputStreamTask.addOnSuccessListener(new OnSuccessListener<OutputStream>() {
                    @Override
                    public void onSuccess(OutputStream outputStream) {
                        Log.d(TAG, "sending message");
                        try {
                            outputStream.write(text.getBytes());
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                });
            }
        });
    }
}
