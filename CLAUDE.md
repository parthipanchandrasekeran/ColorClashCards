# Claude Code Guidelines for ColorClashCards

## Release Process

### IMPORTANT: Version Code Check
**ALWAYS check the current version code on Play Store before creating a release build.**

The Play Store rejects uploads with duplicate version codes. Before bumping versions:
1. Check Play Console for the latest uploaded version code
2. Ensure the new versionCode is higher than what's on Play Store
3. Don't assume the local version code is correct - it may be out of sync

### Version Bump Checklist
- [ ] Check Play Console for current version code
- [ ] Bump versionCode in `app/build.gradle.kts`
- [ ] Bump versionName in `app/build.gradle.kts`
- [ ] Build release AAB: `./gradlew bundleRelease`
- [ ] Commit version bump
- [ ] Push to remote

## Ludo Game Rules

### Bonus Turns
Players get another turn when:
- Rolling a 6
- Capturing an opponent's token
- Getting a token to the finish/home

### Token Stacking
When multiple tokens occupy the same cell:
- Tokens are offset (side-by-side for 2, triangle for 3, grid for 4+)
- Each token remains individually clickable
- Works across different players' tokens on same position
