# Restaurant Bill & QR Tips — Android starter project

This is a native Kotlin + Jetpack Compose Android Studio project based on the supplied Clean & Minimal green/white UI reference.

## Included in this starter
- Dashboard with sales, bill count, and tip totals
- Add menu items and remove menu items
- New bill with quantities, table number, GST calculation
- Tip percentage and custom tip amount
- Local UPI payment QR generation (ZXing)
- Receipt preview and local bill history
- Sales / bill / tips summary reports
- Restaurant name, UPI VPA, and GST settings
- Paired Bluetooth device listing and ESC/POS test print attempt

## Open and run
1. Install Android Studio (current stable).
2. Choose **Open** and select this project folder.
3. Allow Gradle sync to finish.
4. Connect an Android phone with USB debugging enabled, or use an emulator.
5. Run the `app` configuration.

## Important integration notes
- The initial UPI VPA is set to `jubairhussain41527@ybl` as supplied by the user; it remains editable in Settings.
- A UPI QR only creates a payment intent. It does **not** verify payment success. For automatic payment confirmation, integrate a payment gateway/provider with server-side webhook verification.
- Bluetooth thermal printing currently targets paired Classic Bluetooth ESC/POS printers using the common SPP UUID. Select 58mm or 80mm paper width in Printer Settings and run a test print. Printer models differ; test with the exact device and adjust encoding/commands if required.
- Android 12+ Bluetooth permissions are requested at runtime. Pair the printer first in Android Settings.
- Bills are stored locally in app preferences in this starter. For production deployment, migrate to Room database, add export/backup, authentication, robust error handling, and automated tests.
- This source project is a starter implementation, not a signed APK. Build a release APK from Android Studio after configuring your app signing key.

## Before production
1. Confirm restaurant UPI ID and merchant name.
2. Confirm printer brand/model and paper width (58mm or 80mm).
3. Confirm GST rules and invoice fields required for the business.
4. Add payment gateway verification if you need payment status in-app.


## Build an APK on GitHub (without Android Studio)
1. Create a new GitHub repository (for example `restaurant-bill-qr-tip`).
2. Extract this ZIP on your PC.
3. Upload the **contents** of this project folder to the repository root (the `.github` folder must also be uploaded; enable viewing hidden files if needed).
4. Commit the files to the `main` branch.
5. Open the repository's **Actions** tab.
6. Select **Build Restaurant Android APK** and click **Run workflow** (or let the first push start it).
7. Wait for the workflow to finish successfully.
8. Open the completed workflow run, scroll to **Artifacts**, and download `RestaurantBillQRTip-debug-apk`.
9. Extract the artifact ZIP; it contains `app-debug.apk`. Transfer it to your Android phone and install it.

GitHub Actions usage is subject to the free plan's included minutes and repository limits. This workflow builds a debug APK for testing; it is not a signed Play Store release.
