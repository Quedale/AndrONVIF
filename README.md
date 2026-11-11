# AndrONVIF
Android Onvif Manager for Android

# Description
Very simple android client implementation using OnvifSoapLib with JNI wrapper.

I kinda lost interest in that project the moment Google announced the change related to development mode on Android device.
Although I may still invest time in it if the AOSP community has interest in it. (e.g. GrapheneOS, LineageOS or Waydroid users)

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
## Step 1 : Environment config
Create "local.properties" files and set your android sdk location.
Example:
'''
sdk.dir=/home/awesomeuser/AndroidSdk
'''

## Using AndroidStudio
If you already have a functionnal Android Studio setup, you can simply open the project and assemble it.
Configuring Android Studio can be a pain depending on the distro, so I won't bother trying to create a guide.

