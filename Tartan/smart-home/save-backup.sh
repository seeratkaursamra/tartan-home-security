#!/bin/bash
set -e

BACKUP_DIR="$HOME/backup"
PROD_DIR="$HOME/prod/Tartan/smart-home"
TIMESTAMP=$(date -u +"%Y%m%d_%H%M%S")

echo "=== Starting backup before deployment ==="

# Create backup directory if it doesn't exist
mkdir -p "$BACKUP_DIR"

# Rotate: move current latest_working to previous_working
if [ -d "$BACKUP_DIR/latest_working" ]; then
    echo "Moving latest_working -> previous_working..."
    rm -rf "$BACKUP_DIR/previous_working"
    mv "$BACKUP_DIR/latest_working" "$BACKUP_DIR/previous_working"
fi

# Save current production as latest_working
echo "Saving current production as latest_working..."
mkdir -p "$BACKUP_DIR/latest_working"
cp -r "$PROD_DIR"/* "$BACKUP_DIR/latest_working/"

# Create timestamped backup
echo "Creating timestamped backup: backup_${TIMESTAMP}..."
mkdir -p "$BACKUP_DIR/backup_${TIMESTAMP}"
cp -r "$PROD_DIR"/* "$BACKUP_DIR/backup_${TIMESTAMP}/"

# Keep only the last 5 timestamped backups
echo "Cleaning old backups (keeping last 5)..."
cd "$BACKUP_DIR"
ls -dt backup_* 2>/dev/null | tail -n +6 | xargs rm -rf

echo "=== Backup complete ==="
echo "  latest_working:  current production snapshot"
echo "  previous_working: previous production snapshot"
echo "  backup_${TIMESTAMP}: timestamped archive"
