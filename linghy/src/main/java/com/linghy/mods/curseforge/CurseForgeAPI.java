package com.linghy.mods.curseforge;

import com.google.gson.*;
import com.google.gson.annotations.SerializedName;
import com.linghy.utils.CryptoUtil;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

public class CurseForgeAPI
{
    public static final int GAME_ID = 70216;
    private static final Gson gson = new GsonBuilder().create();

    public static String getApiKey()
    {
        try {
            String url = "https://raw.githubusercontent.com/0xcds4r/LingHy-Launcher/main/keys/cf.key";
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                System.err.println("Failed to fetch API key, status: " + response.statusCode());
                return "";
            }

            return CryptoUtil.readEncryptedBytes(response.body());
        } catch (Exception e) {
            System.err.println("Error fetching API key: " + e.getMessage());
            e.printStackTrace();
        }
        return "";
    }

    public static void init()
    {
        // test
        try {
            List<Mod> mods = searchMods("");
            System.out.println("Total founded mods: " + mods.size());
            if (!mods.isEmpty()) {
                Mod m = mods.get(0);
                System.out.println("First founded mod: " + m.name + " (ID: " + m.id + ")");
                System.out.println("Authors: " + m.authors.stream().map(a -> a.name).toList());
                System.out.println("Category: " + m.categories.stream().map(c -> c.name).toList());

                ModFile latest = getLatestFile(m.id);
                if (latest != null) {
                    System.out.println("Last file: " + latest.fileName + ", URL: " + latest.downloadUrl);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static List<Mod> searchMods(String query) throws Exception
    {
        String url = "mods/search?gameId=" + GAME_ID + "&searchFilter=" +
                URLEncoder.encode(query, StandardCharsets.UTF_8);
        String resp = CFHttp.get(url).body();
        ApiResponse<Mod[]> apiResp = gson.fromJson(resp, ApiResponseModArray.class);
        return List.of(apiResp.data);
    }

    public static Mod getMod(int modId) throws Exception
    {
        String url = "mods/" + modId;
        String resp = CFHttp.get(url).body();
        ApiResponse<Mod> apiResp = gson.fromJson(resp, ApiResponseMod.class);
        return apiResp.data;
    }

    public static List<ModFile> getModFiles(int modId) throws Exception
    {
        String url = "mods/" + modId + "/files";
        String resp = CFHttp.get(url).body();
        ApiResponse<ModFile[]> apiResp = gson.fromJson(resp, ApiResponseModFileArray.class);
        return List.of(apiResp.data);
    }

    public static ModFile getLatestFile(int modId) throws Exception
    {
        List<ModFile> files = getModFiles(modId);
        if (files.isEmpty()) return null;
        ModFile latest = files.get(0);
        for (ModFile f : files) {
            if (f.fileDate.getTime() > latest.fileDate.getTime()) {
                latest = f;
            }
        }
        return latest;
    }

    public static void downloadFile(ModFile file, Path out) throws Exception {
        CFHttp.download(file.downloadUrl, out);
    }

    public static class ApiResponse<T> {
        public T data;
    }

    private static class ApiResponseMod extends ApiResponse<Mod> {}
    private static class ApiResponseModArray extends ApiResponse<Mod[]> {}
    private static class ApiResponseModFileArray extends ApiResponse<ModFile[]> {}

    public static class Mod {
        public int id;
        public String name;
        public String slug;
        public String summary;
        public List<Author> authors;
        public List<Category> categories;
        @SerializedName("mainFileId")
        public int mainFileId;
    }

    public static class Author {
        public int id;
        public String name;
        public String url;
    }

    public static class Category {
        public int id;
        public String name;
        public String url;
    }

    public static class ModFile {
        public int id;
        public String fileName;
        public String displayName;
        public String downloadUrl;
        @SerializedName("fileDate")
        public java.util.Date fileDate;
        public long fileLength;
        public String[] gameVersions;
    }
}