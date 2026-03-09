# tartan

The Tartan SmartHome Platform
---
This Dropwizard appllication is a RESTful service to control the Tartan SmartHome platform. 
This depends on the IoTController library.

How to start the tartan application
---

In order for this to run properly, MySQL and one or more House Simulator (Hubs) have to be running on the 
system.

1. Inside the Platform folder, run `./gradlew shadowJar` to build your application
1. Start application with `./gradlew run`
1. To check that your application is running enter url `http://localhost:8080/smarthome/state/mse`

## Production Server
- **IP Address**: 10.2.4.125
- **Location**: Cybera Edmonton

## Automated Deployment

The system automatically deploys when code is pushed to the repository and passes all tests.

### How Deployment Works
1. Push code to GitHub
2. GitHub Actions runs build and test
3. If tests pass, code is automatically deployed to production server
   1. build-and-test - compiles code and runs unit test
   2. system-test - runs docker containers and executes the system test
   3. deploy - deploys to production server (only if tests pass)
4. Previous version is automatically backed up before deployment
5. Docker containers are rebuilt and restarted


### Manual Deployment

If you need to deploy manually, SSH into the production server:
```bash
ssh -i ~/.ssh/id_rsa ubuntu@10.2.4.125
cd ~/prod/Tartan/smart-home
docker compose down
docker compose up --build -d
```

## Rollback System
The platform includes a rollback mechanism to quickly recover from failed deployments /

### How Rollback works:
before each deployment the system automatically:
1. Saves the current version to ~/backup/latest_working
2. Moves the previous latest_working to ~/backup/previous_working
3. Creates a timestamped backup for history

Backup structure:
1. previous_working - The rollback target (version before current)
2. latest_working - Current production version (saved before new deployment)
3. backup_YYYYMMDD_HHMMSS - Historical snapshots (last 5 kept)

### Performing a rollback
To revert to the previous version:
```bash
ssh -i ~/.ssh/id_rsa ubuntu@10.2.4.125
cd ~/prod/Tartan/smart-home
./rollback.sh
```
Has an expected time of 1-2 minutes \

#### What the rollback script does:
1. Verifies that a previous backup exists
2. Stops current Docker containers
3. Restores files from ~/backup/previous_working
4. Rebuilds and starts containers with previous version
5. Waits for MySQL and platform to be ready
6. Reports completion status


To rollback up to 5 versions from a saved timestamped backup :
```bash
# List available backups
ls -lt ~/backup/backup_*

# Restore specific version
ssh -i ~/.ssh/id_rsa ubuntu@10.2.4.125
cd ~/prod/Tartan/smart-home
docker compose down
rm -rf ~/prod/Tartan/smart-home/*
cp -r ~/backup/backup_<YYYYMMDD_HHMMSS>/* ~/prod/Tartan/smart-home/
docker compose up -d
```

### When to use rollback
1. new deployment cause runtime errors 
2. Platform fails to start after deployment 
3. Critical bugs discovered in production 
4. Database connection issues appear 
5. System performance degrades after update


## Backups
save-backup.sh - backs up current deployment before new version is deployed

### Backup management:
#### Backups are stored:::   ~/backup/
#### Naming convention:
They follow the format of backup_\<date of creation\>_\<time of creation\> \
The format of the time and date follows UTC. \
So as an example: backup_20260308_143022 (March 8, 2026 at 14:30:22 UTC)

### View available backups:
```bash
ssh -i ~/.ssh/id_rsa ubuntu@10.2.4.125
ls -lth ~/backup/
```
### Troubleshooting:
"No previous version found" \
caused by no previous_working backup existing, either due to a first deployment or backup deletion. \
To solve, you have to restore from a timestamped backup, or fix the current deployment.



