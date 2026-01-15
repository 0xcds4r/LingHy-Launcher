package com.hylauncher.launcher;

public class NewsItem {
    public String title;
    public String date;
    public String description;
    public String url;
    public String imageUrl;

    public NewsItem(String title, String date, String desc, String url, String img) {
        this.title = title;
        this.date = date;
        this.description = desc;
        this.url = url;
        this.imageUrl = img;
    }
}
