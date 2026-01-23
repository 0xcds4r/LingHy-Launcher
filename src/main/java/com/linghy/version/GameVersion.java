package com.linghy.version;

public class GameVersion
{
    private final String name;
    private final String fileName;
    private final String downloadUrl;
    private final int patchNumber;
    private final long size;
    private final boolean installed;
    private final String branch; // "release" or "pre-release"

    public GameVersion(String name, String fileName, String downloadUrl,
                       int patchNumber, long size, boolean installed, String branch)
    {
        this.name = name;
        this.fileName = fileName;
        this.downloadUrl = downloadUrl;
        this.patchNumber = patchNumber;
        this.size = size;
        this.installed = installed;
        this.branch = branch;
    }

    public GameVersion(String name, String fileName, String downloadUrl,
                       int patchNumber, long size, boolean installed)
    {
        this(name, fileName, downloadUrl, patchNumber, size, installed, "release");
    }

    public String getName() {
        return name;
    }

    public String getFileName() {
        return fileName;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public int getPatchNumber() {
        return patchNumber;
    }

    public long getSize() {
        return size;
    }

    public boolean isInstalled() {
        return installed;
    }

    public String getBranch() {
        return branch;
    }

    public boolean isPreRelease() {
        return "pre-release".equals(branch);
    }

    public String getFormattedSize()
    {
        if (size < 0) return "Unknown";

        double mb = size / (1024.0 * 1024.0);
        double gb = mb / 1024.0;

        if (gb >= 1.0) {
            return String.format("%.2f GB", gb);
        } else {
            return String.format("%.2f MB", mb);
        }
    }

    @Override
    public String toString()
    {
        String branchLabel = isPreRelease() ? " [Pre-Release]" : "";
        return String.format("%s (%s)%s%s",
                name,
                getFormattedSize(),
                branchLabel,
                installed ? " [Installed]" : "");
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        GameVersion that = (GameVersion) o;
        return patchNumber == that.patchNumber && branch.equals(that.branch);
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(patchNumber) * 31 + branch.hashCode();
    }
}