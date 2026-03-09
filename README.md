# w26-tartan-m007

| Item                  | Details                                                         |
| --------------------- | --------------------------------------------------------------- |
| **Group Number**      | w26-m007                                                        |
| **Course**            | CMPUT 402                                                       |
| **Project**           | Group Project – Tartan Smart Home System                        |
| **GitHub Repository** | https://github.com/cmput402/w26-tartan-m007                                                               |


## TEAM MEMBERS

| Name                  | CCID         |
| --------------------- | ------------ |
| Seerat Kaur           | seeratpr     |
| Andrew Harris         | ajharris     |
| Gunish Sharma         | gunish       |
| Abdulrahman Khafagy   | akhafagy     |

## Production Server
- **IP Address 4**: 10.2.4.125 (will also need a vpn)
- **IP Address 6**: 2605:fd00:4:1001:f816:3eff:fe45:d1ba
- **Location**: Cybera Edmonton

## Runner
- **IP Address 4**: 10.2.6.68 (will also need a vpn)
- **IP Address 6**: 2605:fd00:4:1001:f816:3eff:fea5:c626
- **Location**: Cybera Edmonton

## Automated Deployment

The system automatically deploys when code is pushed to the repository and passes all tests.

### How Automated Deployment Works
1. Push code to GitHub
2. GitHub Actions runs build and test
3. If tests pass, code is automatically deployed to production server
    1. build-and-test - compiles code and runs unit test
    2. system-test - runs docker containers and executes the system test
    3. deploy - deploys to production server (only if tests pass)
4. Previous version is automatically backed up before deployment
5. Only the platform container is rebuilt and restarted; house simulators and MySQL keep running


### How to launch containers

If you need to deploy manually, SSH into the production server:
```bash
ssh -i ~/.ssh/id_rsa ubuntu@[PRODUCTION-IP-ADDRESS]
cd ~/prod/Tartan/smart-home
docker compose up -d
```
This starts six containers:
1. smart-home-mysql-container-1 - mySQL database
2. smart-home-platform-1 - Java/Dropwizard Rest API
3. smart-home-house-sci-1, smart-home-house-mse-1, smart-home-house-eng-1, smart-home-house-cmu-1  
   (Use `docker compose up -d --build` for a full restart of all services.)

Note: The platform waits for MySQL to be healthy before starting (may take 30-60 seconds).

To verify that the containers are running
```bash
docker compose ps
```
then all services show status "up".

### How to access when system is running
open in web browser:  http://[production-IP-ADDRESS]:8080/smarthome/state/[house name] \
if using ip4 not ip6, you will need a vpn to access. \
otherwise the house names are:
1. mse
   2. admin : 1234
2. cmu: 
   3. admin : 5678
3. eng: 
   4. eng : 1234
4. sci:
   5. sci : 5678

### Manual deployment
if you need to manually update the platform after a build:
```bash
ssh -i ~/.ssh/id_rsa ubuntu@[IP-ADDRESS]
cd ~/prod/Tartan/smart-home
docker compose up -d --build platform
```
This command:
1. Rebuilds only the platform container
2. Restarts only the platform
3. Keeps house simulators and MySQL running

## Rollback System
The platform includes a rollback mechanism to quickly recover from failed deployments \
This logic is covereed in the script rollback.sh

### How Rollback works:
before each deployment the system automatically:
1. Saves the current version to ~/backup/latest_working
2. Moves the previous latest_working to ~/backup/previous_working
3. Creates a timestamped backup for history

Backup structure:
1. previous_working - The rollback target (version before current)
2. latest_working - Current production version (saved before new deployment)
3. backup_YYYYMMDD_HHMMSS - Historical snapshots (last 5 kept)

### Performing a standard rollback
To revert to the previous version:
```bash
ssh -i ~/.ssh/id_rsa ubuntu@[PRODUCTION-IP-ADDRESS]
cd ~/prod/Tartan/smart-home
./rollback.sh
```
Has an expected time of 1-2 minutes \
What this does:
1. Stops the platform container 
2. Restores code from ~/backup/previous_working 
3. Rebuilds and restarts the platform container 
4. House simulators and MySQL continue running

### Rollback to specific version
To rollback up to 5 previous versions from a saved timestamped backup :
```bash
# List available backups
ls -lt ~/backup/backup_*
```
Backups are named: backup_<YYYYMMDD_HHMMSS> (in UTC time)

To restore a specific backup

```bash
# Restore specific version
ssh -i ~/.ssh/id_rsa ubuntu@[PRODUCTION-IP-ADDRESS]
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

### how the backup script works
Saves current version to ~/backup/latest_working \
Moves previous latest_working to ~/backup/previous_working\
Creates timestamped backup ~/backup/backup_YYYYMMDD_HHMMSS\
Keeps last 5 timestamped backups

### Backup management:
#### Backups are stored:::   ~/backup/
#### Naming convention:
They follow the format of backup_\<date of creation\>_\<time of creation\> \
The format of the time and date follows UTC. \

So as an example: backup_20260308_143022 (March 8, 2026 at 14:30:22 UTC)

### View available backups:
```bash
ssh -i ~/.ssh/id_rsa ubuntu@[PRODUCTION-IP-ADDRESS]
ls -lth ~/backup/
```
### Troubleshooting:
"No previous version found" 
```
caused by no previous_working backup existing, either due to a first deployment or backup deletion. \
To solve, you have to restore from a timestamped backup, or fix the current deployment.
```

Check container status
```
docker compose ps
docker compose logs platform
```

Platform won't start
```
docker compose down -v
docker compose up --build -d

or

docker compose restart platform

```

