# BOOKS — Library App (MVP)

This module implements an MVP library app for managing and reading books (PDF/EPUB).

MVP features implemented:
- Room data model for `Book` (title, author, genre, file path, cover, last page)
- Add Book flow: pick file (SAF) -> enter metadata -> file copied into app storage
- Main screen (Jetpack Compose) showing list of books with cover/title/author
- Book detail screen to view metadata and open reader
- PDF reader using PdfRenderer (prev/next + last page saved)
- EPUB viewing using `epublib` + WebView (basic)
- Basic backup export to ZIP (metadata.json + files) via `BackupManager` (restore & Drive upload TODO)

Planned / TODO:
- Improve EPUB rendering and navigation
- Implement Google Drive backup & restore using OAuth 2.0
  - Add Sign-In UI and request Drive scopes
  - Add upload / restore flows using Drive REST API (multipart upload)
  - Persist tokens securely (or rely on Google Play Sign-In session) and handle consent revocation
  - Acceptance: user can upload a ZIP backup and restore it from Drive with clear error messages
  - Implementation note: initial skeleton caches access tokens in `DriveAuthManager` (SharedPreferences). For production, consider `EncryptedSharedPreferences` or `AccountManager` and handle token refresh/revocation robustly.
- Add bookmarks and better resume logic
- Tests and edge-case handling
- UI polish

Developer notes:
- Create an OAuth client in Google Cloud Console and add any required SHA fingerprints to enable sign-in on device.
- The app currently includes a basic Drive upload skeleton (`DriveBackupActivity`) that performs sign-in and multipart upload; production needs robust token handling and retries.

How to run:
- Open the project in Android Studio and sync Gradle.
- Build and run on a device with Android 8.0+ (minSdk 24)

Notes:
- The implementation favors clarity over completeness for the MVP. Drive integration is scaffolded and needs OAuth details.
