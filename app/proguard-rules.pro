# DeciBoost release rules

-keep class com.deciboost.core.audio.policy.** { *; }
-keep class com.deciboost.core.domain.** { *; }

-keep class * extends android.app.Service {
    public <init>(...);
}

-keep class * extends android.content.BroadcastReceiver {
    public <init>(...);
}

-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}

-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod

-dontwarn android.media.audiofx.**