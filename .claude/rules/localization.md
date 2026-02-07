# Localization Guidelines

## String Extraction

- All user-facing texts must be in `strings.xml` resource files
- Use the `strings.xml` belonging to the respective feature module
- General texts used across modules go in `app-common/src/main/res/values/strings.xml`
- Before creating a new entry, check if `app-common` already has a reusable version

## String ID Naming

Format: `{feature}_{itemtype}_{descriptor}`

- Prefix with the feature/module name
- Re-used strings should be prefixed with `general_` or `common_`
- Avoid implementation details in IDs (use `_action` postfix instead of `button_` prefix)

## String Format Conventions

- Use ordered placeholders for multiple arguments: `%1$s is %2$d`
- Use ellipsis character (`…`) instead of three dots (`...`)

## Per-Module String Files

Each module has its own `strings.xml` managed by Crowdin:
- `app-common/src/main/res/values/strings.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/foss/res/values/strings.xml`
- `app/src/gplay/res/values/strings.xml`
- `module-core/src/main/res/values/strings.xml`
- `modules-power/src/main/res/values/strings.xml`
- `modules-meta/src/main/res/values/strings.xml`
- `modules-wifi/src/main/res/values/strings.xml`
- `modules-apps/src/main/res/values/strings.xml`
- `modules-clipboard/src/main/res/values/strings.xml`
- `sync-core/src/main/res/values/strings.xml`
- `syncs-gdrive/src/main/res/values/strings.xml`
- `syncs-kserver/src/main/res/values/strings.xml`

## Crowdin

- Project ID: `741615`
- Config: `crowdin.yaml`
- Only edit `values/strings.xml` (base English). Never edit translated `values-*/strings.xml` files — those are managed by Crowdin.
