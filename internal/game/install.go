package game

import (
	"context"
	"fmt"
	"os"
	"path/filepath"
	"runtime"
	"strconv"

	"HyLauncher/internal/env"
	"HyLauncher/internal/java"
	"HyLauncher/internal/pwr"
	"HyLauncher/internal/pwr/butler"
)

func EnsureInstalled(ctx context.Context, progress func(stage string, progress float64, msg string, file string, speed string, down, total int64)) error {
	if err := java.DownloadJRE(ctx, progress); err != nil {
		return err
	}
	if _, err := butler.InstallButler(ctx, progress); err != nil {
		return err
	}

	remoteVer := pwr.FindLatestVersion("release")
	if remoteVer == 0 {
		return fmt.Errorf("could not find any game versions on server")
	}

	return InstallGame(ctx, "release", remoteVer, progress)
}

func InstallGame(ctx context.Context, versionType string, remoteVer int, progressCallback func(stage string, progress float64, message string, currentFile string, speed string, downloaded, total int64)) error {
	localStr := pwr.GetLocalVersion()
	local, _ := strconv.Atoi(localStr)

	gameLatestDir := filepath.Join(env.GetDefaultAppDir(), "release", "package", "game", "latest")

	gameClient := "HytaleClient"
	if runtime.GOOS == "windows" {
		gameClient += ".exe"
	}
	clientPath := filepath.Join(gameLatestDir, "Client", gameClient)
	_, clientErr := os.Stat(clientPath)

	if local == remoteVer && clientErr == nil {
		if progressCallback != nil {
			progressCallback("game", 100, "Game is up to date", "", "", 0, 0)
		}
		return nil
	}

	prevVer := local
	if clientErr != nil {
		prevVer = 0
	}

	pwrPath, err := pwr.DownloadPWR(ctx, versionType, prevVer, remoteVer, progressCallback)
	if err != nil {
		return err
	}

	if err := pwr.ApplyPWR(ctx, pwrPath, progressCallback); err != nil {
		return err
	}

	return pwr.SaveLocalVersion(remoteVer)
}
