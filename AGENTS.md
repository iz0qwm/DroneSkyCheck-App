# Drone Sky Check Codex Notes

- When `versionName` changes in `app/build.gradle.kts`, reset `AppReleaseNotes.Current` in `app/src/main/java/it/droneskycheck/app/data/AppLegalContent.kt` for the new version. After the reset, collect only the changes made for that version until the next `versionName` change.
