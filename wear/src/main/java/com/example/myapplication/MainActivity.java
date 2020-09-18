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
import com.google.android.gms.tasks.Tasks;
import com.google.android.gms.wearable.ChannelClient;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutionException;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvMessage = findViewById(R.id.tvMessage);
        etMessage = findViewById(R.id.etMessage);

        // Enables Always-on
        setAmbientEnabled();
    }

    @Override
    protected void onResume() {
        super.onResume();

        executorService = new ThreadPoolExecutor(4, 5, 60L, TimeUnit.SECONDS, new LinkedBlockingDeque<Runnable>());

        Wearable.getChannelClient(getApplicationContext()).registerChannelCallback(new ChannelClient.ChannelCallback() {
            @Override
            public void onChannelOpened(@NonNull ChannelClient.Channel channel) {
                super.onChannelOpened(channel);
                Log.d(TAG, "onChannelOpened");
                /**
                 * input stream
                 */
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
                                        Log.d(TAG, "data length " + read); /** use Log.e to force print, if Log.d does not work */
                                        buffer.write(data, 0, read);

                                        buffer.flush();
                                        byte[] byteArray = buffer.toByteArray();

                                        text += new String(byteArray, StandardCharsets.UTF_8);

                                    }
                                    Log.d(TAG, "reading: " + text);
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
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();

        executorService.shutdown();
    }

    private Collection<String> getNodes() {
        HashSet<String> results = new HashSet<>();

        Task<List<Node>> nodeListTask = Wearable.getNodeClient(getApplicationContext()).getConnectedNodes();

        try {
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

    public void onSend(View view) {
        final String text = etMessage.getText().toString();

        executorService.execute(new Runnable() {
            @Override
            public void run() {
                Collection<String> nodes = getNodes();
                Log.e(TAG, "Nodes: " + nodes.size());
                for (String node : nodes) {
                    Task<ChannelClient.Channel> channelTask = Wearable.getChannelClient(getApplicationContext()).openChannel(node, CHANNEL_MSG);
                    channelTask.addOnSuccessListener(new OnSuccessListener<ChannelClient.Channel>() {
                        @Override
                        public void onSuccess(ChannelClient.Channel channel) {
                            Log.e(TAG, "onSuccess " + channel.getNodeId());
                            Task<OutputStream> outputStreamTask = Wearable.getChannelClient(getApplicationContext()).getOutputStream(channel);
                            outputStreamTask.addOnSuccessListener(new OnSuccessListener<OutputStream>() {
                                @Override
                                public void onSuccess(OutputStream outputStream) {
                                    Log.d(TAG, "output stream onSuccess");
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
