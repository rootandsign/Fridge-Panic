# Fridge Panic! 🧊😱

**Free Android food tracker — Public Beta v0.4**

Keep track of food in your fridge, freezer, and pantry. Fridge Panic! shows what to use first, with no account, ads, or subscription.

## Features
- Add food using a use-by date or a number of days
- Edit food names, dates, and storage locations
- See **Eat Me First!** sorted by soonest use-by date
- Mark food as used
- Scan product barcodes to look up product names (internet required; not every product is in the database)
- Store your list on your device; normal use does not require a server
- Light and dark themes

**Important:** Scanning a barcode does **not** determine the expiration date. Enter and verify dates yourself. Date reminders are not food-safety guarantees.

## Install the beta
1. Open the [Releases page](https://github.com/rootandsign/Fridge-Panic/releases).
2. Download the APK from the latest **beta** release once available.
3. Open the APK on your Android phone and follow Android's prompts to allow installation from that source. Only install APKs you trust.

If no release APK is available yet, see **Build** below. GitHub Actions artifacts are development builds, not permanent public downloads.

## Build
Open **Actions**, choose **Build Android APK**, and run the workflow. Download the APK artifact from the completed run and extract its ZIP. A debug APK is for testing only.

## Data and privacy
Food entries are stored locally on the device using Android SharedPreferences. Barcode lookups send scanned product codes to Open Food Facts. No login or cloud syncing is provided.

**Back up your list manually before uninstalling, clearing app data, or switching phones.** These actions may remove your entries. Android backups can vary by device and settings.

## Beta feedback
Please report bugs under [Issues](https://github.com/rootandsign/Fridge-Panic/issues). Helpful details: Android version, steps to reproduce, expected result, and actual result. Avoid including private information.

## Release checklist
- [ ] Confirm app builds successfully on GitHub Actions
- [ ] Test add/edit/use, rotation, reopening, midnight date rollover, and barcode failure handling
- [ ] Capture and add screenshots to this README
- [ ] Create and securely back up a **private release signing key**; never commit it to GitHub
- [ ] Configure release signing and build a signed release APK
- [ ] Publish the signed APK under GitHub Releases as a **pre-release**
- [ ] Verify a future APK signed with the same key installs as an update without losing saved food

## Notes
The countdown follows the phone's local date. An open screen may need to refresh after midnight. This app is a beta; please report unexpected behavior.
