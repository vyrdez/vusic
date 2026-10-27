# Vusic release rules (R8, minify + resource shrink on).
#
# Libraries ship consumer rules (Retrofit, OkHttp, kotlinx.serialization,
# Hilt/Dagger, Room, Media3, WorkManager, Coil) so only app-specific keeps and
# reflective entry points need declaring here.

# --- Retrofit ---
# Generic signatures are read reflectively for suspend/Call return types.
-keepattributes Signature, InnerClasses, EnclosingMethod, RuntimeVisibleAnnotations, AnnotationDefault
-keep,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions**
-dontwarn retrofit2.KotlinExtensions$*

# --- kotlinx.serialization ---
# Generated serializers are looked up by name at runtime.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
# Domain models + DTOs are @Serializable and referenced reflectively.
-keep,includedescriptorclasses class com.feishin.mobile.domain.model.**$$serializer { *; }
-keepclassmembers class com.feishin.mobile.domain.model.** {
    *** Companion;
}
-keepclasseswithmembers class com.feishin.mobile.domain.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.feishin.mobile.data.remote.**$$serializer { *; }
-keepclassmembers class com.feishin.mobile.data.remote.** {
    *** Companion;
}
-keepclasseswithmembers class com.feishin.mobile.data.remote.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# --- Hilt / Dagger ---
# Generated components and @HiltViewModel constructors are resolved by Hilt.
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keepclasseswithmembers class * {
    @javax.inject.Inject <init>(...);
}
-keepclasseswithmembers class * {
    @dagger.hilt.android.lifecycle.HiltViewModel <init>(...);
}

# --- AndroidX WorkManager (workers are instantiated by name) ---
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class * extends androidx.work.CoroutineWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# --- Media3 session service / widget receiver (instantiated by the framework) ---
-keep class com.feishin.mobile.playback.PlayerService { *; }
-keep class com.feishin.mobile.widget.NowPlayingWidget { *; }
-keep class com.feishin.mobile.widget.WidgetActionReceiver { *; }
-keep class com.feishin.mobile.MainActivity { *; }

# --- Coil / OkHttp ---
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn coil.**

# --- kotlinx.coroutines ---
-dontwarn kotlinx.coroutines.**

# --- Kuromoji IPADIC ---
# Kuromoji loads dictionary .bin resources relative to its package name via getResourceAsStream
-keep class com.atilika.kuromoji.** { *; }
-dontwarn com.atilika.kuromoji.**
