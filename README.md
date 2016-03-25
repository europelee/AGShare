AjUtil
======

alljoyn_gstreamer library:everything connected and communicating,   
and support audio/video streaming.  


alljoyn wrapper(just support android now), and some demo.
the wrapper provides a android service named EndPtService,
and DeviceInterace/DeviceService used to send/recv data,
all for developing quickly and easily, could care alljoyn
less, and the developer only need care (un)serializing data
from DeviceInterface/DeviceService, yes, the wrapper does 
one thing well, let app layer parsing data, modular programing,
low coupling.   

Dependencies:

1. alljoyn, it already was included AjUtil upnp project.
2. gstreamer:
notice: you need to download gstreamer sdk for building gstutil.so from 
gstreamerutil, link: http://gstreamer.freedesktop.org/data/pkg/android/ 
the project uses 1.2.4 version.Then you should set environment var GSTREAMER_SDK_ROOT_ANDROID, its value:
the folder path where you unzipped the above SDK, at last, you just ndk-build, and libgstutil.so and
libgstreamer_android.so would be generated.

2014-5-18 add GstreamerUtil module, implement audio streaming
by alljoyn(Communication capability) and Gstreamer(media han-
dling technology), you can try it with TestUPnP, enjoy multi-
media sharing.    
   
2014-6-13 the master branch use gstreamer0.10.36, when video playing, 720p   
is ok, but 1080p is not good, so I will create a branch named HDIm   
with latest gstreamer, try to improve 1080p playing.   

2014-8-5 there are many change on upnp, and TestUPnP need modified,   
I already start coding a app with new AjUtil for video/audio streaming.

2014-12-26 WeShareDemo for show multimedia sharing like airplay,dlna,etc.
