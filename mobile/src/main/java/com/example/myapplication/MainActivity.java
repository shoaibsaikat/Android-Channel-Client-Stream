package com.example.myapplication;

import androidx.annotation.WorkerThread;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.gms.wearable.ChannelClient;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
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

    private Collection<String> nodes;
    private ChannelClient.Channel connectedChannel;
    private InputStream is;
    private OutputStream os;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.e(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvMessage = findViewById(R.id.tvMessage);
        etMessage = findViewById(R.id.etMessage);
        executorService = new ThreadPoolExecutor(4, 5, 60L, TimeUnit.SECONDS, new LinkedBlockingDeque<Runnable>());
    }

    @Override
    protected void onResume() {
        super.onResume();

        executorService.execute(new Runnable() {
            @Override
            public void run() {
                nodes = getNodes();
                Log.e(TAG, "Nodes: " + nodes.size());
                for (String node : nodes) {
                    Task<ChannelClient.Channel> channelTask = Wearable.getChannelClient(getApplicationContext()).openChannel(node, CHANNEL_MSG);
                    channelTask.addOnSuccessListener(new OnSuccessListener<ChannelClient.Channel>() {
                        @Override
                        public void onSuccess(ChannelClient.Channel channel) {
                            Log.e(TAG, "onSuccess " + channel.getNodeId());
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
            }
        });
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
            Log.e(TAG, "sending message");
            try {
                os.write(text.getBytes());
                os.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            Log.e(TAG, "output stream is null");
            Toast.makeText(getApplicationContext(), "Please close the app and clear from recent list", Toast.LENGTH_SHORT).show();
        }
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
}
