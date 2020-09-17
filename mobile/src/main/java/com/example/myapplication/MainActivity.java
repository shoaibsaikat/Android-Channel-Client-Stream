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

    private static final int REQUEST_IMAGE_CAPTURE = 1;

    private boolean cameraSupported;

    private TextView tvMessage;
    private EditText etMessage;
//    private ImageView ivThumbView;
//    private Bitmap imageBitmap;
//    private Button btnTakePhoto;

    private ThreadPoolExecutor executorService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.e(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        cameraSupported = getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA);

        tvMessage = findViewById(R.id.tvMessage);
        etMessage = findViewById(R.id.etMessage);
//        ivThumbView = findViewById(R.id.imageView);
//        btnTakePhoto = findViewById(R.id.btnOpenCamera);

        executorService = new ThreadPoolExecutor(4, 5, 60L, TimeUnit.SECONDS, new LinkedBlockingDeque<Runnable>());

//        ActivityCompat.requestPermissions(
//                MainActivity.this,
//                new String[] {
//                        Manifest.permission.READ_EXTERNAL_STORAGE,
//                        Manifest.permission.WRITE_EXTERNAL_STORAGE
//                },
//                1
//        );
    }

    @Override
    protected void onResume() {
        super.onResume();

//        btnTakePhoto.setEnabled(cameraSupported);

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
                                        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                                        int read;
                                        byte[] data = new byte[1024];
                                        while ((read = inputStream.read(data, 0, data.length)) != -1) {
                                            LOGD(TAG, "data length " + read); /** use Log.e to force print, if Log.d does not work */
                                            buffer.write(data, 0, read);

                                            buffer.flush();
                                            byte[] byteArray = buffer.toByteArray();

                                            final String text = new String(byteArray, StandardCharsets.UTF_8);
                                            LOGD(TAG, "reading: " + text);
                                            runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    tvMessage.setText(text);
                                                }
                                            });
                                        }
                                        inputStream.close();
                                    } catch (IOException e) {
                                        e.printStackTrace();
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

//    @Override
//    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
//        super.onActivityResult(requestCode, resultCode, data);
//
//        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
//            Bundle extras = data.getExtras();
//            imageBitmap = (Bitmap) extras.get("data");
//            ivThumbView.setImageBitmap(imageBitmap);
//        }
//    }

//    /**
//     * Dispatches an {@link android.content.Intent} to take a photo. Result will be returned back in
//     * onActivityResult().
//     */
//    public void onTakePhotoClick(View view) {
//        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
//        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
//            startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
//        }
//    }

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

//    public void onImageSend(View view) {
//        final String text = etMessage.getText().toString();
//
//        executorService.execute(new Runnable() {
//            @Override
//            public void run() {
//                Collection<String> nodes = getNodes();
//                LOGD(TAG, "Nodes: " + nodes.size());
//                for (String node : nodes) {
//                    Task<ChannelClient.Channel> channelTask = Wearable.getChannelClient(getApplicationContext()).openChannel(node, CHANNEL_MSG);
//                    channelTask.addOnSuccessListener(new OnSuccessListener<ChannelClient.Channel>() {
//                        @Override
//                        public void onSuccess(ChannelClient.Channel channel) {
//                            LOGD(TAG, "onSuccess " + channel.getNodeId());
//
//                            try {
//                                File textFile = new File(Environment.getExternalStorageDirectory(), "given_message.txt");
//                                Files.deleteIfExists(Paths.get(Uri.fromFile(textFile).getPath()));
//
//                                OutputStream out = new FileOutputStream(textFile);
//                                out.write(text.getBytes());
//                                out.flush();
//                                out.close();
//
//                                Wearable.getChannelClient(getApplicationContext()).sendFile(channel, Uri.fromFile(textFile));
//                            } catch (IOException e) {
//                                e.printStackTrace();
//                            }

//                            try {
//                                File imageFile = new File(Environment.getExternalStorageDirectory(), "taken_image.png");
//                                Files.deleteIfExists(Paths.get(Uri.fromFile(imageFile).getPath()));
//
//                                FileOutputStream out = new FileOutputStream(imageFile);
//                                imageBitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
//                                /** PNG is a lossless format, the compression factor (100) is ignored */
//                                out.flush();
//                                out.close();
//
//                                Wearable.getChannelClient(getApplicationContext()).sendFile(channel, Uri.fromFile(imageFile));
//                            } catch (IOException e) {
//                                e.printStackTrace();
//                            }
//                        }
//                    });
//                }
//            }
//        });
//    }
}
