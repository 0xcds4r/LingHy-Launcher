package pwr

import (
	"HyLauncher/internal/env"
	"encoding/json"
	"fmt"
	"net/http"
	"os"
	"path/filepath"
	"runtime"
	"strconv"
	"sync"
	"time"
)

type VersionInfo struct {
	Version int `json:"version"`
}

func GetLocalVersion() string {
	path := filepath.Join(env.GetDefaultAppDir(), "release", "version.json")
	data, err := os.ReadFile(path)
	if err != nil {
		return "0"
	}
	var info VersionInfo
	if err := json.Unmarshal(data, &info); err != nil {
		return "0"
	}
	return strconv.Itoa(info.Version)
}

func SaveLocalVersion(v int) error {
	path := filepath.Join(env.GetDefaultAppDir(), "release", "version.json")
	_ = os.MkdirAll(filepath.Dir(path), 0755)
	data, _ := json.Marshal(VersionInfo{Version: v})
	return os.WriteFile(path, data, 0644)
}

// FindLatestVersion discovers the newest version using parallel HEAD requests
func FindLatestVersion(versionType string) int {
	osName := runtime.GOOS
	arch := runtime.GOARCH

	client := &http.Client{
		Timeout: 2 * time.Second,
		CheckRedirect: func(req *http.Request, via []*http.Request) error {
			return http.ErrUseLastResponse
		},
	}

	latestFound := 0
	batchSize := 10

	for start := 1; start < 500; start += batchSize {
		var wg sync.WaitGroup
		batchResults := make(chan int, batchSize)

		for i := 0; i < batchSize; i++ {
			wg.Add(1)
			go func(v int) {
				defer wg.Done()
				// We check for the "/0/" path because every version MUST have a full installer
				url := fmt.Sprintf("https://game-patches.hytale.com/patches/%s/%s/%s/0/%d.pwr",
					osName, arch, versionType, v)

				resp, err := client.Head(url)
				if err == nil && resp.StatusCode == http.StatusOK {
					batchResults <- v
				} else {
					batchResults <- 0
				}
			}(start + i)
		}

		// Wait for this batch to finish
		wg.Wait()
		close(batchResults)

		maxInBatch := 0
		for v := range batchResults {
			if v > maxInBatch {
				maxInBatch = v
			}
		}

		if maxInBatch > latestFound {
			latestFound = maxInBatch
		} else {
			// If this batch didn't find a version higher than our current latest,
			// it means we've hit the ceiling of the CDN.
			break
		}
	}

	return latestFound
}
