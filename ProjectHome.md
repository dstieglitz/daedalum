Dadealum is an experimental pure Java video player framework built on top of the excellent Xuggler ffmpeg wrapper.

## Prerequisites ##
```
Java 1.6
Ant
Xuggler
```

## Getting Started ##
**This project has a lot of experimental code**, and might not work out-of-the-box for you. Included with the project is an ant build file that uses Apache Ivy to resolve and download dependencies. To run, you'll need to get a working version of Xuggler for your system and create a build properties file as such:

```
#Daedalum build.properties
xuggle.home=/path/to/your/xuggle/installation
xuggle.jar.dir=/path/to/your/xuggle-xuggler.jar/file
```

In addition, you'll need to point your LD\_LIBRARY\_PATH, DYLD\_LIBRARY\_PATH and/or java.library.path (depending on your system's OS and configuration) to your Xuggler runtime.

You can get the Xuggler runtime from http://code.google.com/p/xuggle

To run a test player, simply execute `ant run` from the project root.

## Known Issues ##
Synchronizing audio and video, for any media player, presents a huge challenge. When working with Java this challenge is even greater due to Java's interpreted nature and limited ability to deal with realtime processing. Add to this differences in audio hardware, operating system idiosyncrasies, etc. and it's not any easier.

Daedalum presents a framework to deal with a/v sync but it's far from perfect. Currently, the audio subsystem uses JavaSound but it needs quite a bit of improvement.

## About the Project's Name ##
The Daedalum (lit. "Devil's Wheel") was the original name for the Zoetrope, which was a device that produced a brief animation from a set of still photographs. It was the "proof-of-concept," if you will, of the modern film camera.

http://en.wikipedia.org/wiki/Zoetrope
