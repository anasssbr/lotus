# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

-dontobfuscate

-keep class org.jaudiotagger.** { *; }

# yt-dlp wrapper: it is loaded reflectively from Python and uses Jackson internally.
-keep class com.yausername.** { *; }
-dontwarn com.yausername.**
-keep class com.fasterxml.jackson.** { *; }
-dontwarn com.fasterxml.jackson.**
-keepclassmembers class * {
    @com.fasterxml.jackson.annotation.* <fields>;
}
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod

# Commons Compress (used by youtubedl-android to unzip Python) registers its
# ExtraField implementations reflectively; R8 merging them breaks the static
# initializer with "AsiExtraField is not a concrete class".
-keep class org.apache.commons.compress.** { *; }
-dontwarn org.apache.commons.compress.**
-dontwarn org.tukaani.xz.**

# NewPipeExtractor parses YouTube pages with jsoup and evaluates player JS with Rhino.
-keep class org.schabi.newpipe.** { *; }
-dontwarn org.schabi.newpipe.**
-keep class org.mozilla.javascript.** { *; }
-dontwarn org.mozilla.javascript.**
-dontwarn org.jsoup.**
-dontwarn javax.annotation.**
-dontwarn com.grack.nanojson.**
-dontwarn org.intellij.lang.annotations.**

-dontwarn java.awt.Graphics2D
-dontwarn java.awt.Image
-dontwarn java.awt.geom.AffineTransform
-dontwarn java.awt.image.BufferedImage
-dontwarn java.awt.image.ImageObserver
-dontwarn java.awt.image.RenderedImage
-dontwarn javax.imageio.ImageIO
-dontwarn javax.imageio.ImageWriter
-dontwarn javax.imageio.stream.ImageInputStream
-dontwarn javax.imageio.stream.ImageOutputStream
-dontwarn javax.swing.filechooser.FileFilter