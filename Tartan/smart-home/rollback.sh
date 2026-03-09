#!/bin/bash
set -e

BACKUP_DIR="$HOME/backup"
PROD_DIR="$HOME/prod/Tartan/smart-home"

echo "=== Starting rollback ==="

# Check that a previous version exists
if [ ! -d "$BACKUP_DIR/previous_working" ]; then
    echo "ERROR: No previous version found at $BACKUP_DIR/previous_working"
    echo "Cannot rollback - no backup available."
    exit 1
fi

# Stop current containers
echo "Stopping current containers..."
cd "$PROD_DIR"
docker compose down || true

# Restore previous version
echo "Restoring previous version from backup..."
rm -rf "$PROD_DIR"/*
cp -r "$BACKUP_DIR/previous_working"/* "$PROD_DIR"/

# Start restored version
echo "Starting restored containers..."
cd "$PROD_DIR"
docker compose up --build -d

# Wait for MySQL
echo "Waiting for MySQL..."
for i in {1..60}; do
    if docker exec smart-home-mysql-container-1 mysqladmin ping -h localhost -u root -proot --silent 2>/dev/null; then
        echo "MySQL is ready!"
        break
    fi
    sleep 2
done

# Wait for platform
echo "Waiting for platform..."
for i in {1..60}; do
    code=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/smarthome/state/mse || true)
    if [ "$code" = "200" ] || [ "$code" = "401" ]; then
        echo "Platform is ready!"
        break
    fi
    sleep 2
done

echo "=== Rollback complete ==="
echo "System has been reverted to the previous version."
