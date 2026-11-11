# AndrONVIF
Android Onvif Manager for Android

![Application Capture](res/Images/AppScreenshot.png?raw=true "AndrONVIF Default Screen")

# Description
Very simple android client implementation using [OnvifSoapLib](https://github.com/Quedale/OnvifSoapLib) with JNI wrapper.
This app is far from being finished. It is riddled with bugs and missing features.
At this point, I would consider this pre-Alpha.

I kinda lost interest in that project the moment Google announced the change related to development mode on Android device.
Although I may still invest time in it if the AOSP community is interested in it or if I find myself simply bored. (e.g. GrapheneOS, LineageOS or Waydroid users)

# Working
- Onvif WS-Discovery and WS-Security (gsoap)
- Soap Client for Onvif Device and Media service
- Vew RTSP Stream
- Support Multiple ONVIF Media Profiles
- HTTP authentication challenge (Basic, Digest and NTLM)
- Windowed mode

# TODO
- Not available yet

# Tested with
- [rpos](https://github.com/Quedale/rpos)
- [v4l2onvif](https://github.com/mpromonet/v4l2onvif)
- Merit LILIN (PSR5024EX30 & SR7424/8)
- Reolink (RLC-820A)
- Imou (Ranger 2C)
- TP-Link (Tapo C200)
- [Yi-Hack Allwinner V2](https://github.com/roleoroleo/yi-hack-Allwinner-v2) (Pro 2K Home)

# How to build 
## Step 1 : Download Android SDK
You can either use Android Studio SDK Manager GUI or the CLI tool.

### Using Android Studio
Configuring Android Studio can be a pain depending on the distro, so I won't bother trying to create a guide.
You can search for appropriate instructions online for your specific distro.

### Using the CLI:
You will need to download [sdkmanager](https://developer.android.com/tools/sdkmanager) and use it to download Android SDK 17 and Android NDK 29.

The following script assume a default location `~/Android/Sdk`.
Change this to whatever you wish.
```
mkdir -p ~/Android/Sdk/cmdline-tools && cd ~/Android/Sdk/cmdline-tools
wget https://dl.google.com/android/repository/commandlinetools-linux-15859902_latest.zip
unzip commandlinetools-linux-15859902_latest.zip && mv cmdline-tools latest
export ANDROID_HOME="$HOME/Android/Sdk"
export PATH="$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools"
yes | sdkmanager --licenses
```

Use the sdkmanager to download the SDK and NDK
```
sdkmanager "platforms;android-17"
sdkmanager --install "ndk;29.0.14206865" --channel=0
```

## Step 2 : Environment config
Create "local.properties" file at the root of the project and set your Android SDK location.
Example:
```
sdk.dir=/home/awesomeusername/Android/Sdk
```

## Step 3 : Build and Assemble APK
### Using AndroidStudio
If you already have a functionnal Android Studio setup, you can simply open the project and assemble it.

### Gradle CLI

Assemble project
```
./gradlew assembleRelease
```

The APK should be available at the following location:
```
AndrONVIF/build/outputs/apk/release/AndrONVIF-release-unsigned.apk
```