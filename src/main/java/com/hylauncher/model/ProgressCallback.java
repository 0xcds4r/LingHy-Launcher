package com.hylauncher.model;

@FunctionalInterface
public interface ProgressCallback {
    void onProgress(ProgressUpdate update);
}