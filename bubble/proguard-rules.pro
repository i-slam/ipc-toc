# Readable stack traces are worth the few KB in a tool this small.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# The platform instantiates these by name.
-keep class com.example.bubble.BubbleActivity { *; }
-keep class com.example.bubble.BubbleOverlayService { *; }
-keep class com.example.bubble.BubbleBootReceiver { *; }
