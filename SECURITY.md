# Security Policy

## Supported versions

Only the latest published release receives security fixes. Please upgrade to the
newest tag in [Releases](https://github.com/vyrdez/vusic/releases).

## Reporting a vulnerability

**Please do not open a public issue for a security vulnerability.**

Use GitHub's private vulnerability reporting so the details stay confidential
until a fix is released:

**Security → Report a vulnerability** on this repository
(<https://github.com/vyrdez/vusic/security/advisories/new>).

Include the app version, Android version, your Navidrome server version, and a
reproduction. You should get an acknowledgement within a few days. If private
advisory reporting is unavailable on the account, open a regular issue that says
only "security report available on request" with no technical detail, and we will
open a private channel.

## How Vusic handles your data

- Server credentials are stored in Android's `EncryptedSharedPreferences`
  (AES-256, keys in the Android keystore) and are never written to logs.
- Release builds compile out all HTTP logging; debug builds redact the
  `u`/`t`/`s` API credential parameters before anything is printed.
- Nothing is uploaded anywhere except to the Navidrome server you configure.
- The app requests no analytics, advertising or crash-reporting permissions.
