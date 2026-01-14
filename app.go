package main

import (
	"context"
	"errors"
	"fmt"

	"HyLauncher/internal/env"
	"HyLauncher/internal/game"
	"HyLauncher/internal/pwr"

	"github.com/wailsapp/wails/v2/pkg/runtime"
)

type App struct {
	ctx context.Context
}

type ProgressUpdate struct {
	Stage       string  `json:"stage"`
	Progress    float64 `json:"progress"`
	Message     string  `json:"message"`
	CurrentFile string  `json:"currentFile"`
	Speed       string  `json:"speed"`
	Downloaded  int64   `json:"downloaded"`
	Total       int64   `json:"total"`
}

func NewApp() *App {
	return &App{}
}

func (a *App) startup(ctx context.Context) {
	a.ctx = ctx
	env.CreateFolders()

	fmt.Println("Cleaning up temporary files...")
	if err := env.CleanupIncompleteDownloads(); err != nil {
		fmt.Println("Warning: cleanup failed:", err)
	}
}

// progressCallback is passed to internal packages to report back to the UI
func (a *App) progressCallback(stage string, progress float64, message string, currentFile string, speed string, downloaded, total int64) {
	runtime.EventsEmit(a.ctx, "progress-update", ProgressUpdate{
		Stage:       stage,
		Progress:    progress,
		Message:     message,
		CurrentFile: currentFile,
		Speed:       speed,
		Downloaded:  downloaded,
		Total:       total,
	})
}

// GetVersions returns current local version and latest remote version for the UI
func (a *App) GetVersions() (currentVersion string, latestVersion string) {
	current := pwr.GetLocalVersion()
	latest := pwr.FindLatestVersion("release")

	return current, fmt.Sprintf("%d", latest)
}

func (a *App) DownloadAndLaunch(playerName string) error {
	if len(playerName) > 16 {
		return errors.New("Nickname is too long (max 16 chars)")
	}

	if err := game.EnsureInstalled(a.ctx, a.progressCallback); err != nil {
		return err
	}

	a.progressCallback("launch", 100, "Launching game...", "", "", 0, 0)

	if err := game.Launch(playerName, "latest"); err != nil {
		return fmt.Errorf("failed to launch: %w", err)
	}

	return nil
}
