# Color Clash Cards

A fast-paced color and number matching card game for Android. Play online with friends, offline against computer opponents, or practice your skills in solo mode.

## Features

### Game Modes
- **Play vs Computer**: Challenge 1-3 AI opponents with adjustable difficulty (Easy, Normal, Hard)
- **Online Multiplayer**: Create or join rooms with unique room codes, play with friends in real-time
- **Guest Mode**: Play without creating an account

### Gameplay
- Classic color and number matching mechanics
- Special action cards: Skip, Reverse, Draw Two
- Wild cards: Change color or force opponents to draw
- "Last Card" call system - don't forget to call it when you have one card left!
- Smooth animations and intuitive touch controls

### Online Features
- Google Sign-In or Guest authentication
- Create private rooms with shareable codes
- Public room browser
- Real-time game state synchronization
- Automatic reconnection to active games
- Bot-fill for empty seats (host-controlled)

### User Experience
- Material Design 3 with dynamic colors
- Dark mode support
- Felt-style table background
- Original card designs with diagonal flash stripes
- Turn indicators and playable card highlights

## Technical Stack

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose with Material 3
- **Authentication**: Firebase Auth (Google Sign-In + Anonymous)
- **Database**: Cloud Firestore (real-time sync)
- **Image Loading**: Coil
- **Minimum SDK**: 24 (Android 7.0)
- **Target SDK**: 35 (Android 15)

## Firebase Setup

### Prerequisites
1. Create a Firebase project at [Firebase Console](https://console.firebase.google.com)
2. Enable Authentication with Google Sign-In and Anonymous providers
3. Create a Cloud Firestore database

### Configuration Steps

1. **Add Android App to Firebase**
   - Package name: `com.parthipan.colorclashcards`
   - Download `google-services.json`
   - Place it in `app/` directory

2. **Configure Google Sign-In**
   - In Firebase Console > Authentication > Sign-in method
   - Enable Google provider
   - Add your SHA-1 fingerprint (debug and release)

3. **Deploy Firestore Security Rules**
   ```bash
   firebase deploy --only firestore:rules
   ```

   The rules file (`firestore.rules`) is included in the project.

4. **Firestore Indexes** (auto-created on first query, or deploy manually)
   ```bash
   firebase deploy --only firestore:indexes
   ```

### Getting SHA-1 Fingerprint

Debug key:
```bash
keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
```

Release key:
```bash
keytool -list -v -keystore keystore/release.keystore -alias colorclashcards -storepass YOUR_PASSWORD
```

**Current Release Fingerprints** (add these to Firebase Console):
```
SHA-1:   F5:48:46:35:66:B2:22:83:67:9C:7E:27:F5:43:36:A6:D3:CE:A1:EF
SHA-256: 9D:AA:08:8A:49:63:C0:3B:E2:65:9D:BB:A1:C4:C7:70:69:34:29:66:E5:9C:BE:98:34:5A:91:26:7F:B0:F4:C8
```

## Building the App

### Debug Build
```bash
./gradlew assembleDebug
```
APK location: `app/build/outputs/apk/debug/app-debug.apk`

### Release Build
```bash
./gradlew assembleRelease
```

### Generate Signed AAB for Play Store

1. **Create a Keystore** (if you don't have one):
   ```bash
   keytool -genkey -v -keystore colorclashcards-release.keystore -alias colorclashcards -keyalg RSA -keysize 2048 -validity 10000
   ```

2. **Create `keystore.properties`** in project root (do not commit this file):
   ```properties
   storeFile=../colorclashcards-release.keystore
   storePassword=your_store_password
   keyAlias=colorclashcards
   keyPassword=your_key_password
   ```

3. **Build the AAB**:
   ```bash
   ./gradlew bundleRelease
   ```
   AAB location: `app/build/outputs/bundle/release/app-release.aab`

## Project Structure

```
app/src/main/java/com/parthipan/colorclashcards/
├── data/
│   ├── model/          # Data classes (User, Room, OnlineMatch)
│   └── repository/     # Firebase repositories
├── game/
│   ├── engine/         # Game logic (GameEngine, BotAgent, DeckBuilder)
│   └── model/          # Game models (Card, GameState, Player)
└── ui/
    ├── auth/           # Authentication screens
    ├── game/           # Gameplay screens (offline & online)
    ├── home/           # Main menu
    ├── howtoplay/      # Rules and instructions
    ├── online/         # Lobby and room screens
    ├── privacy/        # Privacy policy
    ├── settings/       # User settings
    ├── solo/           # Offline game setup
    ├── splash/         # Launch screen
    ├── navigation/     # App navigation
    ├── components/     # Reusable UI components
    └── theme/          # Material 3 theming
```

## Testing

### Unit Tests
```bash
./gradlew test
```

### Manual Testing Checklist
- [ ] Offline vs bots (all difficulty levels)
- [ ] Online with 2+ real players
- [ ] Online with bot-fill
- [ ] Guest user flow
- [ ] Google Sign-In flow
- [ ] App restart + reconnect to active game
- [ ] Network disconnection recovery

## License

Copyright 2025 Parthipan. All rights reserved.

## Contact

For support or inquiries: support@colorclashcards.com
