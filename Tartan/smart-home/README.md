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
4. Docker containers are rebuilt and restarted

### Manual Deployment

If you need to deploy manually, SSH into the production server:
```bash
ssh -i ~/.ssh/id_rsa ubuntu@10.2.4.125
cd ~/prod/Tartan/smart-home
docker compose down
docker compose up --build -d
```

## Rollback

To revert to the previous version:
```bash
ssh -i ~/.ssh/id_rsa ubuntu@10.2.4.125
cd ~/prod/Tartan/smart-home
./rollback.sh
```
