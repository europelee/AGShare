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

2014-5-18 add GstreamerUtil module, implement audio streaming
by alljoyn(Communication capability) and Gstreamer(media han-
dling technology), you can try it with TestUPnP, enjoy multi-
media sharing.    

Now support audio streaming, in future, there will be a proto-
system, of course, include video streaming.
