package com.example.myapplication;

import android.Manifest;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.wearable.activity.WearableActivity;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.gms.wearable.ChannelClient;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
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
//    private ImageView ivImage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        tvMessage = findViewById(R.id.tvMessage);
        etMessage = findViewById(R.id.etMessage);
//        ivImage = findViewById(R.id.imageView);

//        ActivityCompat.requestPermissions(
//                MainActivity.this,
//                new String[] {
//                        Manifest.permission.READ_EXTERNAL_STORAGE,
//                        Manifest.permission.WRITE_EXTERNAL_STORAGE
//                },
//                1
//        );

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

//                /**
//                 * file stream
//                 */
//                Wearable.getChannelClient(getApplicationContext()).registerChannelCallback(new ChannelClient.ChannelCallback() {
//                    @Override
//                    public void onInputClosed(@NonNull ChannelClient.Channel channel, int i, int i1) {
//                        super.onInputClosed(channel, i, i1);
//
//                        try {
//                            File textFile = new File(Environment.getExternalStorageDirectory(), "given_message.txt");
//                            Files.deleteIfExists(Paths.get(Uri.fromFile(textFile).getPath()));
//
//                            Wearable.getChannelClient(getApplicationContext()).receiveFile(channel, Uri.fromFile(textFile), false);
//
//                            String text = "";
//                            InputStream in = new FileInputStream(textFile);
//                            int read;
//                            byte[] data = new byte[1024];
//                            while ((read = in.read(data, 0, data.length)) != -1) {
//                                text += new String(data, StandardCharsets.UTF_8);
//                            }
//                            in.close();
//
//                            String finalText = text;
//                            runOnUiThread(new Runnable() {
//                                @Override
//                                public void run() {
//                                    tvMessage.setText(finalText);
//                                }
//                            });
//
//                            Wearable.getChannelClient(getApplicationContext()).close(channel);
//                        } catch (IOException e) {
//                            e.printStackTrace();
//                        }
//
//                        try {
//                            File imageFile = new File(Environment.getExternalStorageDirectory(), "taken_image.png");
//                            Files.deleteIfExists(Paths.get(Uri.fromFile(imageFile).getPath()));
//
//                            Wearable.getChannelClient(getApplicationContext()).receiveFile(channel, Uri.fromFile(imageFile), false);
//
//                            runOnUiThread(new Runnable() {
//                                @Override
//                                public void run() {
//                                    Bitmap image = BitmapFactory.decodeFile(Uri.fromFile(imageFile).getPath());
//                                    ivImage.setImageBitmap(image);
//                                }
//                            });
//
//                            Wearable.getChannelClient(getApplicationContext()).close(channel);
//                        } catch (IOException e) {
//                            e.printStackTrace();
//                        }
//                    }
//                });

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
                                    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                                    int read;
                                    byte[] data = new byte[1024];
                                    String text = "";

                                    while ((read = inputStream.read(data, 0, data.length)) != -1) {
                                        Log.d(TAG, "data length " + read); /** use Log.e to force print, if Log.d does not work */
                                        buffer.write(data, 0, read);

                                        buffer.flush();
                                        byte[] byteArray = buffer.toByteArray();

                                        text = text + new String(byteArray, StandardCharsets.UTF_8);

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
                                    } finally {
                                        Wearable.getChannelClient(getApplicationContext()).close(channel);
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
