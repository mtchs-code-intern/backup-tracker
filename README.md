# Backup Tracker

## Description
Application for making a backup of a folder and tracking changes to any files or folders within the tracked folder.

## Features
- Fully functioning CLI tool
- Backup creation
- Backup change tracking

## Prerequisites
- Java 26
- Gradle 9.4.1

## Installation
1. Clone the repository:
   ```
   git clone https://github.com/mtchs-code-intern/backup-tracker.git
   ```
2. Navigate to the project directory:
   ```
   cd backup-tracker
   ```
3. Build the project:
   ```
   ./gradlew build
   ```
Alternatively you can download the installer [here](https://github.com/mtchs-code-intern/backup-tracker-installer).
## Usage
```
./gradlew run --args="-help"
```
OR if on PATH
```
backuptracker -help
```
## Building
To build the project:
```
./gradlew build
```

## Testing
To run tests:
```
./gradlew test
```
