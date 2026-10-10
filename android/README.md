# RPi Pico Bluetooth Car - Android App

An Android application built with **Java**, **Material Design 3**, and **Bluetooth Serial Port Profile (SPP)** for controlling a Raspberry Pi Pico based Bluetooth car.

## Building the Release APK

To build the release APK and copy it to the `releases/` directory, run the following command from the `android/` directory:

```bash
./gradlew assembleRelease &&
mkdir -p ../releases &&
cp -f app/build/outputs/apk/release/app-release.apk \
  ../releases/Bluetooth_Car.apk
```

### APK Signing

By default, the release build uses the local Android debug key, allowing the APK to be installed for testing.

To sign the APK with a production key, you need a Java keystore (`.jks` file) and its valid credentials. The keystore must be available at either `android/release-keystore.jks` or `release-keystore.jks`, depending on the directory from which the build is configured to locate it.

Set the following environment variables before building:

```bash
export SIGNING_STORE_PASSWORD='your-real-keystore-password'
export SIGNING_KEY_ALIAS='your-real-key-alias'
export SIGNING_KEY_PASSWORD='your-real-key-password'
```

Then run the release build command shown above.

**Important:**

- Replace the example values with your actual keystore credentials.
- Keep your keystore file and passwords private. Never commit them to version control.
- A production-signed APK can update an existing installation only if it is signed with the same signing key, subject to Android's signing rules.
- If the production keystore or credentials are unavailable, unset the three environment variables to use the local debug key instead, provided the project is configured to support this fallback.

The APK is copied to `releases/Bluetooth_Car.apk` when the build succeeds.
