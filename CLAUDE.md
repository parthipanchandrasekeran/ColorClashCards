# Claude Code Guidelines for ColorClashCards

## Release Process

### IMPORTANT: Version Code Check
**ALWAYS ASK the user for the current Play Store version code before bumping versions.**

Last known Play Store versionCode: **11** (as of Feb 2, 2026)
Next versionCode should be: **12 or higher**

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

---

## Core Working Principles

### 1. Plan Mode Default
- Enter plan mode for ANY non-trivial task (3+ steps or architectural decisions)
- If something goes sideways, STOP and re-plan immediately — don't keep pushing
- Use plan mode for verification steps, not just building
- Write detailed specs upfront to reduce ambiguity

### 2. Subagent Strategy
- Use subagents liberally to keep main context window clean
- Offload research, exploration, and parallel analysis to subagents
- For complex problems, throw more compute at it via subagents
- One task per subagent for focused execution

### 3. Self-Improvement Loop
- After ANY correction from the user: update CLAUDE.md with the pattern
- Write rules for yourself that prevent the same mistake
- Ruthlessly iterate on these lessons until mistake rate drops
- Review lessons at session start for relevant project

### 4. Verification Before Done
- Never mark a task complete without proving it works
- Diff behavior between main and your changes when relevant
- Ask yourself: "Would a staff engineer approve this?"
- Run tests, check logs, demonstrate correctness

### 5. Demand Elegance (Balanced)
- For non-trivial changes: pause and ask "Is there a more elegant way?"
- If a fix feels hacky: "Knowing everything I know now, implement the elegant solution"
- Skip this for simple, obvious fixes — don't over-engineer
- Challenge your own work before presenting it

### 6. Autonomous Bug Fixing
- When given a bug report: just fix it. Don't ask for hand-holding
- Point at logs, errors, failing tests — then resolve them
- Zero context switching required from the user
- Go fix failing CI tests without being told how

---

## Task Management

### Workflow
1. **Plan First**: Write plan to tasks/todo.md with checkable items
2. **Verify Plan**: Check in before starting implementation
3. **Track Progress**: Mark items complete as you go
4. **Explain Changes**: High-level summary at each step
5. **Document Results**: Add review section to tasks/todo.md
6. **Capture Lessons**: Update CLAUDE.md after corrections

---

## Core Principles

- **Simplicity First**: Make every change as simple as possible. Impact minimal code.
- **No Laziness**: Find root causes. No temporary fixes. Senior developer standards.
- **Minimal Impact**: Changes should only touch what's necessary. Avoid introducing bugs.

---

## Lessons Learned

### Version Code Conflicts
- **Problem**: Assumed local versionCode was correct, but Play Store had higher version
- **Solution**: ALWAYS ask user for current Play Store version before bumping
- **Pattern**: Never trust local state for external system sync
