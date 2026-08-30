package com.example.smartattendance;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ApiClient {
    // Use 10.0.2.2 for Android Emulator connecting to localhost
    // Use your computer's local IP (e.g. 192.168.x.x) if testing on a physical device
    public static final String BASE_URL = "https://smart-automatic-attendance-system.onrender.com";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private static ApiClient instance;
    private final OkHttpClient client;
    private final PrefsHelper prefsHelper;
    private final Handler mainHandler;

    private ApiClient(Context context) {
        prefsHelper = new PrefsHelper(context);
        mainHandler = new Handler(Looper.getMainLooper());

        // Setup OkHttp Client with Auth Interceptor
        client = new OkHttpClient.Builder()
                .addInterceptor(new Interceptor() {
                    @Override
                    public Response intercept(Chain chain) throws IOException {
                        Request originalRequest = chain.request();
                        Request.Builder builder = originalRequest.newBuilder();
                        
                        String token = prefsHelper.getJwtToken();
                        if (token != null && !token.isEmpty()) {
                            builder.header("Authorization", "Bearer " + token);
                        }
                        
                        return chain.proceed(builder.build());
                    }
                })
                .build();
    }

    public static synchronized ApiClient getInstance(Context context) {
        if (instance == null) {
            instance = new ApiClient(context.getApplicationContext());
        }
        return instance;
    }

    // Callback interface to return responses to the UI thread
    public interface ApiCallback {
        void onSuccess(JSONObject response);
        void onError(String errorMessage);
    }

    private void runCallbackOnMainThread(ApiCallback callback, JSONObject response, String error) {
        if (callback == null) return;
        mainHandler.post(() -> {
            if (error != null) {
                callback.onError(error);
            } else {
                callback.onSuccess(response);
            }
        });
    }

    private void executeRequest(Request request, ApiCallback callback) {
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runCallbackOnMainThread(callback, null, "Network Error: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        String resStr = response.body().string();
                        JSONObject json = new JSONObject(resStr);
                        runCallbackOnMainThread(callback, json, null);
                    } catch (JSONException e) {
                        runCallbackOnMainThread(callback, null, "JSON Parse Error: " + e.getMessage());
                    }
                } else {
                    try {
                        String errStr = response.body() != null ? response.body().string() : "Unknown Error";
                        JSONObject json = new JSONObject(errStr);
                        String errMsg = json.optString("error", json.optString("message", "HTTP " + response.code()));
                        runCallbackOnMainThread(callback, null, errMsg);
                    } catch (JSONException e) {
                        runCallbackOnMainThread(callback, null, "HTTP Error " + response.code());
                    }
                }
            }
        });
    }

    // --- Specific API Calls ---

    public void login(String endpoint, String email, String password, ApiCallback callback) {
        try {
            JSONObject json = new JSONObject();
            json.put("email", email);
            json.put("password", password);

            RequestBody body = RequestBody.create(json.toString(), JSON);
            Request request = new Request.Builder()
                    .url(BASE_URL + endpoint) // e.g. /api/auth/student/login
                    .post(body)
                    .build();

            executeRequest(request, callback);
        } catch (JSONException e) {
            callback.onError("JSON Build Error: " + e.getMessage());
        }
    }

    public void startSession(int classroomId, int subjectId, int teacherId, ApiCallback callback) {
        try {
            JSONObject json = new JSONObject();
            json.put("classroom_id", classroomId);
            json.put("subject_id", subjectId);
            json.put("teacher_id", teacherId);

            RequestBody body = RequestBody.create(json.toString(), JSON);
            Request request = new Request.Builder()
                    .url(BASE_URL + "/api/teacher/start-session")
                    .post(body)
                    .build();

            executeRequest(request, callback);
        } catch (JSONException e) {
            callback.onError("JSON Build Error: " + e.getMessage());
        }
    }

    public void markAttendance(int sessionId, int studentId, ApiCallback callback) {
        try {
            JSONObject json = new JSONObject();
            json.put("session_id", sessionId);
            json.put("student_id", studentId);

            RequestBody body = RequestBody.create(json.toString(), JSON);
            Request request = new Request.Builder()
                    .url(BASE_URL + "/api/student/mark-attendance")
                    .post(body)
                    .build();

            executeRequest(request, callback);
        } catch (JSONException e) {
            callback.onError("JSON Build Error: " + e.getMessage());
        }
    }

    public void updateFcmToken(String fcmToken, ApiCallback callback) {
        try {
            JSONObject json = new JSONObject();
            json.put("fcm_token", fcmToken);

            RequestBody body = RequestBody.create(json.toString(), JSON);
            Request request = new Request.Builder()
                    .url(BASE_URL + "/api/student/update-fcm-token")
                    .post(body)
                    .build();

            executeRequest(request, callback);
        } catch (JSONException e) {
            callback.onError("JSON Build Error: " + e.getMessage());
        }
    }

    public void getProfile(ApiCallback callback) {
        Request request = new Request.Builder()
                .url(BASE_URL + "/api/auth/me")
                .get()
                .build();
        executeRequest(request, callback);
    }

    public void getTeacherSubjects(ApiCallback callback) {
        Request request = new Request.Builder()
                .url(BASE_URL + "/api/teacher/my-subjects")
                .get()
                .build();
        executeRequest(request, callback);
    }

    public void getSubjectHistory(int subjectId, ApiCallback callback) {
        Request request = new Request.Builder()
                .url(BASE_URL + "/api/teacher/subject-history/" + subjectId)
                .get()
                .build();
        executeRequest(request, callback);
    }

    public void getActiveSession(ApiCallback callback) {
        Request request = new Request.Builder()
                .url(BASE_URL + "/api/student/active-session")
                .get()
                .build();
        executeRequest(request, callback);
    }

    public void stopSession(ApiCallback callback) {
        RequestBody body = RequestBody.create("{}", JSON);
        Request request = new Request.Builder()
                .url(BASE_URL + "/api/teacher/stop-session")
                .post(body)
                .build();
        executeRequest(request, callback);
    }

    /** Generic GET for any admin endpoint */
    public void adminGet(String endpoint, ApiCallback callback) {
        Request request = new Request.Builder()
                .url(BASE_URL + endpoint)
                .get()
                .build();
        executeRequest(request, callback);
    }

    /** Generic POST for any admin endpoint */
    public void adminPost(String endpoint, JSONObject body, ApiCallback callback) {
        RequestBody rb = RequestBody.create(body.toString(), JSON);
        Request request = new Request.Builder()
                .url(BASE_URL + endpoint)
                .post(rb)
                .build();
        executeRequest(request, callback);
    }

    /** Generic DELETE for any admin endpoint */
    public void adminDelete(String endpoint, ApiCallback callback) {
        Request request = new Request.Builder()
                .url(BASE_URL + endpoint)
                .delete()
                .build();
        executeRequest(request, callback);
    }

    /** Generic PUT for any admin endpoint */
    public void adminPut(String endpoint, JSONObject body, ApiCallback callback) {
        RequestBody rb = RequestBody.create(body.toString(), JSON);
        Request request = new Request.Builder()
                .url(BASE_URL + endpoint)
                .put(rb)
                .build();
        executeRequest(request, callback);
    }
}
