# Ibis Wallet

A self-custody Bitcoin wallet for Android, inspired by [Sparrow Wallet](https://sparrowwallet.com/) but built for mobile. 

Designed for experienced users - no hand-holding, no training wheels.

![screen](https://github.com/user-attachments/assets/9d0a5712-320b-45ca-9e75-1cef60dc0e7e)

[<img src="https://user-images.githubusercontent.com/663460/26973090-f8fdc986-4d14-11e7-995a-e7c5e79ed925.png" alt="Get APK from GitHub" height="80">](https://github.com/aeonBTC/ibiswallet/releases)

## Key Features

- **Multi-Wallet** - Create, import, export, and switch between multiple wallets
- **Multi-Seed** - Supports BIP39 or Electrum seed phrases for importing
- **Watch-only Wallets** - Import xpub/zpub, output descriptors, or single address
- **Import Private Key** - Sweep or import private keys (WIF format)
- **Hardware Wallet Signing** - Use animated QR codes or .psbt files for air gapped key signing
- **Built-in Tor** - Native Tor integreation, no need for Orbot or external Tor proxies
- **Custom Servers** - Connect to your own Electrum, block explorer, and fee estimation servers
- **Coin Control** - Select specific UTXOs, freeze/unfreeze, send from individual outputs
- **RBF & CPFP** - Bump fees on unconfirmed transactions, both outgoing and incoming
- **PIN & Biometrics** - With configurable lock timing
- **Duress Pin** - Configure a secondary PIN that opens a decoy wallet
- **Auto-Wipe** - Set a threshold for failed unlock that automatically and irreversibly wipes all app data
- **Cloak Mode** - Disguise Ibis as a calculator app
- **Manual Broadcast Raw Transactions** - Broadcast any signed transaction directly to the Bitcoin network
- **Batch Sending** - Send to multiple recipients in a single transaction
- **Encrypted Backups** - AES-256 encryption with optional label and custom server backup, import/export via file
- **Built with** [BDK](https://bitcoindevkit.org/)
## Building

Requires Android Studio with JDK 17.

```bash
./gradlew :app:assembleDebug      # Debug
./gradlew :app:assembleRelease    # Release
./gradlew testDebugUnitTest       # Tests
./gradlew jacocoUnitTestReport    # Generate coverage report
```

**Min SDK:** 26 (Android 8.0) | **Target SDK:** 35 | **ARM only** (armeabi-v7a, arm64-v8a)

## License

Open source. See [LICENSE](LICENSE) for details.
