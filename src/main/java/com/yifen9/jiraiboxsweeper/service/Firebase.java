package com.yifen9.jiraiboxsweeper.service;

import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class Firebase {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private final OkHttpClient client = new OkHttpClient();
    private final String dbUrl;

    public Firebase() {
        Properties props = new Properties();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("firebase.properties")) {
            props.load(in);
        } catch (IOException e) {
            throw new RuntimeException("Cannot load firebase.properties", e);
        }
        dbUrl = props.getProperty("firebase.databaseUrl");
    }

    public void postScore(String name, int time) throws IOException {
        JSONObject obj = new JSONObject()
            .put("name", name)
            .put("time", time);
        RequestBody body = RequestBody.create(obj.toString(), JSON);
        Request req = new Request.Builder()
            .url(dbUrl + "/scores.json")
            .post(body)
            .build();
        client.newCall(req).execute().close();
    }

    public List<Score> fetchAllScores() throws IOException {
        String url = dbUrl + "/scores.json";
        Request req = new Request.Builder().url(url).build();
        try (Response resp = client.newCall(req).execute()) {
            if (!resp.isSuccessful()) {
                throw new IOException("HTTP " + resp.code() + ": " + resp.body().string());
            }
            String body = resp.body().string();
            JSONObject root = new JSONObject(body);
            List<Score> list = new ArrayList<>();
            for (String key : root.keySet()) {
                JSONObject item = root.getJSONObject(key);
                list.add(new Score(item.getString("name"), item.getInt("time")));
            }
            return list;
        }
    }

    public static class Score {
        public final String name;
        public final int time;
        public Score(String name, int time) {
            this.name = name;
            this.time = time;
        }
    }
}
