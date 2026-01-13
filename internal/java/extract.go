package java

import (
	"archive/tar"
	"archive/zip"
	"compress/gzip"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strings"
)

func extractJRE(archivePath, destDir string) error {
	_ = os.RemoveAll(destDir)
	if err := os.MkdirAll(destDir, 0755); err != nil {
		return err
	}

	switch {
	case strings.HasSuffix(archivePath, ".zip"):
		return extractZip(archivePath, destDir)

	case strings.HasSuffix(archivePath, ".tar.gz"):
		return extractTarGz(archivePath, destDir)

	default:
		return fmt.Errorf("unsupported archive format: %s", archivePath)
	}
}

func extractZip(zipPath, dest string) error {
	r, err := zip.OpenReader(zipPath)
	if err != nil {
		return err
	}
	defer r.Close()

	for _, f := range r.File {
		fpath := filepath.Join(dest, f.Name)

		// ZipSlip protection
		if !strings.HasPrefix(fpath, filepath.Clean(dest)+string(os.PathSeparator)) {
			return fmt.Errorf("illegal file path: %s", fpath)
		}

		if f.FileInfo().IsDir() {
			if err := os.MkdirAll(fpath, f.Mode()); err != nil {
				return err
			}
			continue
		}

		if err := os.MkdirAll(filepath.Dir(fpath), 0755); err != nil {
			return err
		}

		outFile, err := os.OpenFile(fpath, os.O_WRONLY|os.O_CREATE|os.O_TRUNC, f.Mode())
		if err != nil {
			return err
		}

		rc, err := f.Open()
		if err != nil {
			outFile.Close()
			return err
		}

		_, err = io.Copy(outFile, rc)

		outFile.Close()
		rc.Close()

		if err != nil {
			return err
		}
	}
	return nil
}

func extractTarGz(tarGzPath, dest string) error {
	file, err := os.Open(tarGzPath)
	if err != nil {
		return err
	}
	defer file.Close()

	gzr, err := gzip.NewReader(file)
	if err != nil {
		return err
	}
	defer gzr.Close()

	tr := tar.NewReader(gzr)

	for {
		header, err := tr.Next()
		if err == io.EOF {
			break
		}
		if err != nil {
			return err
		}

		target := filepath.Join(dest, header.Name)

		// TarSlip protection
		if !strings.HasPrefix(target, filepath.Clean(dest)+string(os.PathSeparator)) {
			return fmt.Errorf("illegal file path: %s", target)
		}

		switch header.Typeflag {
		case tar.TypeDir:
			if err := os.MkdirAll(target, os.FileMode(header.Mode)); err != nil {
				return err
			}

		case tar.TypeReg:
			if err := os.MkdirAll(filepath.Dir(target), 0755); err != nil {
				return err
			}

			outFile, err := os.OpenFile(target, os.O_CREATE|os.O_RDWR, os.FileMode(header.Mode))
			if err != nil {
				return err
			}

			if _, err := io.Copy(outFile, tr); err != nil {
				outFile.Close()
				return err
			}

			outFile.Close()
		}
	}
	return nil
}

func flattenJREDir(jreLatest string) error {
	entries, err := os.ReadDir(jreLatest)
	if err != nil {
		return err
	}

	// Only flatten if there's exactly one directory
	if len(entries) != 1 || !entries[0].IsDir() {
		return nil
	}

	nested := filepath.Join(jreLatest, entries[0].Name())

	files, err := os.ReadDir(nested)
	if err != nil {
		return err
	}

	for _, f := range files {
		oldPath := filepath.Join(nested, f.Name())
		newPath := filepath.Join(jreLatest, f.Name())

		if err := os.Rename(oldPath, newPath); err != nil {
			return err
		}
	}

	return os.RemoveAll(nested)
}
