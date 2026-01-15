package com.hylauncher.model;

public class ProgressUpdate {
    private final String stage;
    private final double progress;
    private final String message;
    private final String currentFile;
    private final String speed;
    private final long downloaded;
    private final long total;

    public ProgressUpdate(String stage, double progress, String message,
                          String currentFile, String speed, long downloaded, long total) {
        this.stage = stage;
        this.progress = progress;
        this.message = message;
        this.currentFile = currentFile;
        this.speed = speed;
        this.downloaded = downloaded;
        this.total = total;
    }

    public String getStage() { return stage; }
    public double getProgress() { return progress; }
    public String getMessage() { return message; }
    public String getCurrentFile() { return currentFile; }
    public String getSpeed() { return speed; }
    public long getDownloaded() { return downloaded; }
    public long getTotal() { return total; }
}