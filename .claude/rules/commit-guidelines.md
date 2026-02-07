# Commit Message Guidelines

## Format

- Imperative mood, present tense ("Add feature" not "Added feature")
- First line: 50-60 characters max
- No module prefix — this project uses flat commit messages
- Optionally add a blank line and detailed description body

## Examples from History

```
Fix status bar color split in landscape mode on API 35+
Remove dead commented code from SyncWorker
Improve SyncWorker resilience with per-operation error handling
Remove non-debug unit test variants from CI
Add BATTERY_LOW and BATTERY_OKAY events to trigger immediate sync
Add high battery notification alert for charging devices
Sort devices alphabetically by label in widget and sync devices list
Fix navigation bar color in bottom sheets for light mode
Warn of stale devices
```

## Special Formats

- **Release commits**: `Release: {version}` (e.g., `Release: 0.14.0-rc0`)
- **Translation updates**: `Update translations`
- **Dependency upgrades**: `Upgrade {dependency} from {old} to {new}` (e.g., `Upgrade AGP from 8.12.2 to 8.13.2`)
