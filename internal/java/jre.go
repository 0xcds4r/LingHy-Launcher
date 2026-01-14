package java

import (
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"os"
	"path/filepath"
	"runtime"

	"HyLauncher/internal/env"
	"HyLauncher/internal/util"
)

type JREPlatform struct {
	URL    string `json:"url"`
	SHA256 string `json:"sha256"`
}

type JREJSON struct {
	Version     string                            `json:"version"`
	DownloadURL map[string]map[string]JREPlatform `json:"download_url"`
}

func DownloadJRE(ctx context.Context, progressCallback func(stage string, progress float64, message string, currentFile string, speed string, downloaded, total int64)) error {
	osName := env.GetOS()
	arch := env.GetArch()
	basePath := env.GetDefaultAppDir()

	cacheDir := filepath.Join(basePath, "cache")
	jreLatest := filepath.Join(basePath, "release", "package", "jre", "latest")

	if isJREInstalled(jreLatest) {
		if progressCallback != nil {
			progressCallback("jre", 100, "JRE already installed", "", "", 0, 0)
		}
		return nil
	}

	// Fetch JRE's
	resp, err := http.Get("https://launcher.hytale.com/version/release/jre.json")
	if err != nil {
		return err
	}
	defer resp.Body.Close()

	var jreData JREJSON
	if err := json.NewDecoder(resp.Body).Decode(&jreData); err != nil {
		return err
	}

	osData, ok := jreData.DownloadURL[osName]
	if !ok {
		return fmt.Errorf("no JRE for OS: %s", osName)
	}

	platform, ok := osData[arch]
	if !ok {
		return fmt.Errorf("no JRE for arch: %s on %s", arch, osName)
	}

	fileName := filepath.Base(platform.URL)
	cacheFile := filepath.Join(cacheDir, fileName)
	tempCacheFile := cacheFile + ".tmp"

	// Download JRE
	if _, err := os.Stat(cacheFile); os.IsNotExist(err) {
		_ = os.Remove(tempCacheFile) // Clean up old temp files

		err := util.DownloadWithProgress(tempCacheFile, platform.URL, "jre", 0.9, progressCallback)
		if err != nil {
			_ = os.Remove(tempCacheFile)
			return err
		}

		// Move temp file to final destination
		if err := os.Rename(tempCacheFile, cacheFile); err != nil {
			_ = os.Remove(tempCacheFile)
			return err
		}
	}

	// Verification
	if progressCallback != nil {
		progressCallback("jre", 92, "Verifying JRE integrity...", fileName, "", 0, 0)
	}
	if err := verifySHA256(cacheFile, platform.SHA256); err != nil {
		_ = os.Remove(cacheFile)
		return err
	}

	// Extraction
	if progressCallback != nil {
		progressCallback("jre", 95, "Extracting JRE...", fileName, "", 0, 0)
	}
	if err := extractJRE(cacheFile, jreLatest); err != nil {
		return err
	}

	// Cleanup
	if runtime.GOOS != "windows" {
		javaExec := filepath.Join(jreLatest, "bin", "java")
		_ = os.Chmod(javaExec, 0755)
	}

	flattenJREDir(jreLatest)
	_ = os.Remove(cacheFile)

	if progressCallback != nil {
		progressCallback("jre", 100, "JRE installed successfully", "", "", 0, 0)
	}

	return nil
}

func GetJavaExec() string {
	jreDir := filepath.Join(env.GetDefaultAppDir(), "release", "package", "jre", "latest")
	javaBin := filepath.Join(jreDir, "bin", "java")
	if runtime.GOOS == "windows" {
		javaBin += ".exe"
	}

	// Check if it exists
	if _, err := os.Stat(javaBin); os.IsNotExist(err) {
		fmt.Println("Warning: JRE not found, fallback to system java")
		return "java"
	}

	return javaBin
}

func isJREInstalled(jreDir string) bool {
	java := filepath.Join(jreDir, "bin", "java")
	if runtime.GOOS == "windows" {
		java += ".exe"
	}
	_, err := os.Stat(java)
	return err == nil
}
