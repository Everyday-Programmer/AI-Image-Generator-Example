package com.example.nebiusapiimagegeneration;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {
    private static final String API_BASE_URL = "https://api.studio.nebius.com/v1/images/generations";
    private static final String API_KEY = ""; //Your API key here

    private TextInputEditText promptEditText;
    private MaterialAutoCompleteTextView modelDropDown;
    private MaterialAutoCompleteTextView sizeDropDown;
    private MaterialButton generateButton;
    private ImageView resultImage;
    private LinearProgressIndicator progressIndicator;
    private TextView statusTV;
    private OkHttpClient httpClient;
    private ExecutorService executorService;
    private Handler mainHandler;
    private final String[] models = {
            "stability-ai/sdxl",
            "black-forest-labs/flux-dev",
            "black-forest-labs/flux-schnell"
    };
    private final String[] sizeOptions = {
            "512x512",
            "1024x1024",
            "2000x2000",
            "768x1024",
            "576x1024",
            "1024x768",
            "1024x576",
            "1280x720",
            "1920x1080"
    };
    private Map<String, Map<String, Integer>> sizeMap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        promptEditText = findViewById(R.id.promptET);
        modelDropDown = findViewById(R.id.modelDropDown);
        sizeDropDown = findViewById(R.id.sizeDropDown);
        generateButton = findViewById(R.id.generateBtn);
        resultImage = findViewById(R.id.resultImageView);
        progressIndicator = findViewById(R.id.progressBar);
        statusTV = findViewById(R.id.statusTV);

        httpClient = new OkHttpClient();
        executorService = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        ArrayAdapter<String> modelAdapter = new ArrayAdapter<>(this, com.google.android.material.R.layout.support_simple_spinner_dropdown_item, models);
        modelDropDown.setAdapter(modelAdapter);
        modelDropDown.setText(models[0], false);

        ArrayAdapter<String> sizeAdapter = new ArrayAdapter<>(this, com.google.android.material.R.layout.support_simple_spinner_dropdown_item, sizeOptions);
        sizeDropDown.setAdapter(sizeAdapter);
        sizeDropDown.setText(sizeOptions[0], false);

        sizeMap = new HashMap<>();
        String[] sizeKeys = {
                "512x512",
                "1024x1024",
                "2000x2000",
                "768x1024",
                "576x1024",
                "1024x768",
                "1024x576",
                "1280x720",
                "1920x1080"
        };
        int[][] dimensions = {
                {512, 512},
                {1024, 1024},
                {2000, 2000},
                {768, 1024},
                {576, 1024},
                {1024, 768},
                {1024, 576},
                {1280, 720},
                {1920, 1080}
        };
        for (int i = 0; i < sizeKeys.length; i++){
            Map<String, Integer> dimensionMap = new HashMap<>();
            dimensionMap.put("width", dimensions[i][0]);
            dimensionMap.put("height", dimensions[i][1]);
            sizeMap.put(sizeKeys[i], dimensionMap);
        }

        httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();

        generateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                generateImage();
            }
        });
    }

    private void generateImage() {
        String prompt = promptEditText.getText().toString().trim();
        TextInputLayout promptLayout = findViewById(R.id.promptLayout);
        if (prompt.isEmpty()) {
            promptLayout.setError("Please enter a prompt");
            return;
        }

        String selectedModel = modelDropDown.getText().toString();
        String selectedSize = sizeDropDown.getText().toString();

        showLoading(true);

        executorService.execute(() -> {
            try {
                String imageUrl = callAPI(prompt, selectedModel, selectedSize);
                mainHandler.post(() -> {
                    showLoading(false);
                    if (imageUrl != null) {
                        loadImageFromUrl(imageUrl);
                        statusTV.setText("Image generated successfully!");
                    } else  {
                        statusTV.setText("Failed to generate image");
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    showLoading(false);
                    statusTV.setText("Error: " + e.getMessage());
                });
            }
        });
    }

    private String callAPI(String prompt, String model, String size) throws IOException, JSONException {
        Map<String, Integer> dimensions = sizeMap.get(size);

        JSONObject requestBody = new JSONObject();
        requestBody.put("prompt", prompt);
        requestBody.put("width", dimensions.get("width"));
        requestBody.put("height", dimensions.get("height"));
        requestBody.put("model", model);

        RequestBody body = RequestBody.create(
                MediaType.parse("application/json"),
                requestBody.toString()
        );

        Request request = new Request.Builder()
                .url(API_BASE_URL)
                .post(body)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer " + API_KEY)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("API call failed with code: " + response.code());
            }

            String responseBody= response.body().string();
            JSONObject jsonResponse = new JSONObject(responseBody);

            if (jsonResponse.has("data")) {
                return jsonResponse.getJSONArray("data")
                        .getJSONObject(0)
                        .getString("url");
            } else if (jsonResponse.has("url")) {
                return jsonResponse.getString("url");
            } else if (jsonResponse.has("image_url")) {
                return jsonResponse.getString("image_url");
            }

            return null;
        }
    }

    private void showLoading(boolean show) {
        progressIndicator.setVisibility(show ? View.VISIBLE : View.GONE);
        generateButton.setEnabled(!show);
        generateButton.setText(show ? "Generating..." : "Generate Image");
        statusTV.setText(show ? "Generating image..." : "");
    }

    private void loadImageFromUrl(String imageUrl) {
        RequestOptions options = new RequestOptions()
                .placeholder(R.drawable.outline_crop_original_24)
                .error(R.drawable.outline_broken_image_24)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .centerCrop();

        Glide.with(this).load(imageUrl).apply(options).into(resultImage);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executorService != null) {
            executorService.shutdown();
        }
    }
}