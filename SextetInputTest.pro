
# This doesn't work yet. If you know how to fix it, do tell!

-injars build/jar/SextetInputTest.jar
-injars lib/guava-19.0.jar
-libraryjars proguard-context-lib/jsr305-3.0.0.jar
-outjars build/jar/SextetInputTest-dist.jar

-dontoptimize
-dontobfuscate
-dontwarn sun.misc.Unsafe
-dontwarn com.google.common.collect.MinMaxPriorityQueue
-dontwarn com.google.common.util.concurrent.MoreExecutors


-dontwarn com.google.common.base.Throwables
-dontwarn com.google.common.base.internal.Finalizer

-keepclasseswithmembers public class * {
	public static void main(java.lang.String[]);
}

-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes InnerClasses,EnclosingMethod

