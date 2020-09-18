package com.example.myapplication;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.ChannelClient;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "CUSTOM_TAG";
    private static final String CHANNEL_MSG = "com.example.android.wearable.datalayer.channelmessage";

    private TextView tvMessage;
    private EditText etMessage;

    private ThreadPoolExecutor executorService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.e(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvMessage = findViewById(R.id.tvMessage);
        etMessage = findViewById(R.id.etMessage);
    }

    @Override
    protected void onResume() {
        super.onResume();

        executorService = new ThreadPoolExecutor(4, 5, 60L, TimeUnit.SECONDS, new LinkedBlockingDeque<Runnable>());

        Wearable.getChannelClient(getApplicationContext()).registerChannelCallback(new ChannelClient.ChannelCallback() {
            @Override
            public void onChannelOpened(@NonNull ChannelClient.Channel channel) {
                super.onChannelOpened(channel);
                LOGD(TAG, "onChannelOpened");
                if (channel != null) {
                    Task<InputStream> inputStreamTask = Wearable.getChannelClient(getApplicationContext()).getInputStream(channel);
                    inputStreamTask.addOnSuccessListener(new OnSuccessListener<InputStream>() {
                        @Override
                        public void onSuccess(final InputStream inputStream) {
                            executorService.execute(new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        String text = "";
                                        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                                        int read;
                                        byte[] data = new byte[1024];
                                        while ((read = inputStream.read(data, 0, data.length)) != -1) {
                                            LOGD(TAG, "data length " + read); /** use Log.e to force print, if Log.d does not work */
                                            buffer.write(data, 0, read);

                                            buffer.flush();
                                            byte[] byteArray = buffer.toByteArray();

                                            text += new String(byteArray, StandardCharsets.UTF_8);
                                        }
                                        LOGD(TAG, "reading: " + text);
                                        String finalText = text;
                                        runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                tvMessage.setText(finalText);
                                            }
                                        });
                                        inputStream.close();
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    } finally {
                                        Wearable.getChannelClient(getApplicationContext()).close(channel);
                                    }
                                }
                            });
                        }
                    });
                } else {
                    Log.e(TAG, "connectedChannel is null");
                }
            }
        });
    }

    private Collection<String> getNodes() {
        HashSet<String> results = new HashSet<>();
        Task<List<Node>> nodeListTask = Wearable.getNodeClient(getApplicationContext()).getConnectedNodes();

        try {
            // Block on a task and get the result synchronously (because this is on a background
            // thread).
            List<Node> nodes = Tasks.await(nodeListTask);

            for (Node node : nodes) {
                results.add(node.getId());
            }

        } catch (ExecutionException exception) {
            Log.e(TAG, "Task failed: " + exception);

        } catch (InterruptedException exception) {
            Log.e(TAG, "Interrupt occurred: " + exception);
        }

        return results;
    }

    /** As simple wrapper around Log.d */
    private static void LOGD(final String tag, String message) {
        if (Log.isLoggable(tag, Log.DEBUG)) {
            Log.d(tag, message);
        }
    }

    public void onSend(View view) {
        final String text = etMessage.getText().toString();

        executorService.execute(new Runnable() {
            @Override
            public void run() {
                Collection<String> nodes = getNodes();
                LOGD(TAG, "Nodes: " + nodes.size());
                for (String node : nodes) {
                    Task<ChannelClient.Channel> channelTask = Wearable.getChannelClient(getApplicationContext()).openChannel(node, CHANNEL_MSG);
                    channelTask.addOnSuccessListener(new OnSuccessListener<ChannelClient.Channel>() {
                        @Override
                        public void onSuccess(ChannelClient.Channel channel) {
                            LOGD(TAG, "onSuccess " + channel.getNodeId());
                            Task<OutputStream> outputStreamTask = Wearable.getChannelClient(getApplicationContext()).getOutputStream(channel);
                            outputStreamTask.addOnSuccessListener(new OnSuccessListener<OutputStream>() {
                                @Override
                                public void onSuccess(OutputStream outputStream) {
                                    LOGD(TAG, "output stream onSuccess");
                                    try {
                                        outputStream.write(text.getBytes());
                                        outputStream.flush();
                                        outputStream.close();
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                }
                            });
                        }
                    });
                }
            }
        });
    }
}
