package util

import (
	"fmt"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"time"
)

func DownloadWithProgress(dest, url, stage string, multiplier float64, progressCallback func(stage string, progress float64, message string, currentFile string, speed string, downloaded, total int64)) error {
	resp, err := http.Get(url)
	if err != nil {
		return err
	}
	defer resp.Body.Close()

	out, err := os.Create(dest)
	if err != nil {
		return err
	}
	defer out.Close()

	total := resp.ContentLength
	var downloaded int64
	buf := make([]byte, 32*1024)
	start := time.Now()
	lastUpdate := time.Now()

	for {
		n, err := resp.Body.Read(buf)
		if n > 0 {
			if _, wErr := out.Write(buf[:n]); wErr != nil {
				return wErr
			}
			downloaded += int64(n)

			if time.Since(lastUpdate) > 200*time.Millisecond {
				if total > 0 {
					percent := float64(downloaded) / float64(total) * 100
					elapsed := time.Since(start).Seconds()
					speed := ""
					if elapsed > 0 {
						mbps := float64(downloaded) / 1024 / 1024 / elapsed
						speed = fmt.Sprintf("%.2f MB/s", mbps)
					}

					if progressCallback != nil {
						// Use the dynamic stage and multiplier
						overallProgress := percent * multiplier
						msg := fmt.Sprintf("Downloading %s...", stage)
						progressCallback(stage, overallProgress, msg, filepath.Base(dest), speed, downloaded, total)
					}
				}
				lastUpdate = time.Now()
			}
		}
		if err != nil {
			if err == io.EOF {
				break
			}
			return err
		}
	}
	return nil
}
