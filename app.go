package main

import (
	"context"
	"errors"

	"HyLauncher/internal/env"
	"HyLauncher/internal/java"
)

// App struct
type App struct {
	ctx context.Context
}

// NewApp creates a new App application struct
func NewApp() *App {
	return &App{}
}

// startup is called when the app starts. The context is saved
// so we can call the runtime methods
func (a *App) startup(ctx context.Context) {
	a.ctx = ctx
	env.CreateFolders()
}

func (a *App) DownloadGame() error {
	err := java.DownloadJRE()
	if err != nil {
		return err
	}
	return nil
}

func (a *App) LaunchGame() error {
	return errors.New("asd")
}
