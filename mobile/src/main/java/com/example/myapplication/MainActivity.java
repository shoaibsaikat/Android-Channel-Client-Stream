package com.example.myapplication;

import androidx.annotation.WorkerThread;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

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
    private List<ChannelClient.Channel> channels;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvMessage = findViewById(R.id.tvMessage);
        etMessage = findViewById(R.id.etMessage);
        executorService = new ThreadPoolExecutor(4, 5, 60L, TimeUnit.SECONDS, new LinkedBlockingDeque<Runnable>());
        channels = new ArrayList<>();

        executorService.execute(new Runnable() {
            @Override
            public void run() {
                nodes = getNodes();
                Log.d(TAG, "Nodes: " + nodes.size());
                for (String node : nodes) {
                    Task<ChannelClient.Channel> channelTask = Wearable.getChannelClient(getApplicationContext()).openChannel(node, CHANNEL_MSG);
                    channelTask.addOnSuccessListener(new OnSuccessListener<ChannelClient.Channel>() {
                        @Override
                        public void onSuccess(ChannelClient.Channel channel) {
                            Log.d(TAG, "onSuccess " + channel.getNodeId());
                            channels.add(channel);
                        }
                    });
                }
            }
        });

        for (ChannelClient.Channel channel : channels) {
            addInputListener(channel);
        }
    }

    private void addInputListener(ChannelClient.Channel channel) {
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
                Task<OutputStream> outputStreamTask = Wearable.getChannelClient(getApplicationContext()).getOutputStream(channels.get(0));
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

    @WorkerThread
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
}
