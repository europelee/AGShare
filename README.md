#AGShare  
AGShare includes three projects:   
upnp library : everything connected and communicating;  
GstreamerUtil library : support audio/video streaming;  
WeShareDemo : a funny android app developed with upnp and GstreamerUtil, for multimedia sharing like airplay,dlna,etc.

##upnp 
It is an [alljoyn](https://allseenalliance.org/) wrapper, it supports different android devices finding each other and communicating with each other on the same LAN, all for developing quickly and easily, could care alljoyn less, and the app developer only need care (un)serializing data(use any their own serialization format, such as json, messagepack, protobuf, etc), their app-layer data model and logic.   

##GstreamerUtil
It can receives the raw multimedia data from outer, and save back into file based on linux shared memory(by mmap) , use [Gstreamer](https://gstreamer.freedesktop.org/) to play video, audio, piture at the same time.

##Dependencies:

1. alljoyn, it already was included in AGShare's upnp project.
2. gstreamer:
notice: you need to download gstreamer sdk for building gstutil.so from 
gstreamerutil, link: http://gstreamer.freedesktop.org/data/pkg/android/ 
the project uses 1.2.4 version.Then you should set environment var GSTREAMER_SDK_ROOT_ANDROID, its value:
the folder path where you unzipped the above SDK, at last, you just ndk-build, and libgstutil.so and
libgstreamer_android.so would be generated.

