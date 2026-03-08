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
   3. deploy - deplots to production server (only if tests pass)
4. Docker containers are rebuilt and restarted
5. previous version is automatically backed up before deployment

### Manual Deployment

If you need to deploy manually, SSH into the production server:
```bash
ssh -i ~/.ssh/id_rsa ubuntu@10.2.4.125
cd ~/prod/Tartan/smart-home
docker compose down
docker compose up --build -d
```

## Rollback
The platform includes an automated rollback mechanism to quickly recover from failed deployments /

#### How Rollback works:
before each deployment the system automatically:
1. Saves the current version to ~/backup/latest_working
2. Moves the previous latest_working to ~/backup/previous_working
3. Creates a timestamped backup for history

Backup structure:
1. previous_working - The rollback target (version before current)
2. latest_working - Current production version (saved before new deployment)
3. backup_YYYYMMDD_HHMMSS - Historical snapshots (last 5 kept)

To revert to the previous version:
```bash
ssh -i ~/.ssh/id_rsa ubuntu@10.2.4.125
cd ~/prod/Tartan/smart-home
./rollback.sh

**Single command version** (if already SSH into server):
bash
cd ~/prod/Tartan/smart-home && docker compose down && docker compose up --build -d
```

To rollback multiple versions
```bash
# List available backups
ls -lt ~/backup/backup_*

# Restore specific version
cd ~/prod/Tartan/smart-home
docker compose down
cp -r ~/backup/backup_<date of creation>_<time of creation>/* ~/prod/Tartan/smart-home/
docker compose up -d
```

### Backups
backups are stored in the ~/backup/ folder \
they follow the format of backup_\<date of creation\>_\<time of creation\> \
please note that the format of the time and date follows UTC.

