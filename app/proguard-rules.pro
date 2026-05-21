# sherpa-onnx: JNI native callbacks harus tetap punya nama asli
-keep class com.k2fsa.sherpa.onnx.** { *; }

# MediaPipe tasks-genai: dipakai via reflection internal untuk load model
-keep class com.google.mediapipe.** { *; }
-keep class com.google.android.odml.** { *; }

# Room: nama kelas Database dan DAO dipakai saat runtime untuk cari *_Impl yang digenerate KSP
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep class * extends androidx.room.migration.Migration { *; }

# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keep class kotlinx.coroutines.android.AndroidExceptionPreHandler
-keep class kotlinx.coroutines.android.AndroidDispatcherFactory

# Data class dan enum milik app — dipakai di StateFlow dan Room
-keep class com.sumarize.voiceindo.data.** { *; }
-keep class com.sumarize.voiceindo.ml.TranscriptResult { *; }
-keep class com.sumarize.voiceindo.viewmodel.**State { *; }
-keep class com.sumarize.voiceindo.viewmodel.**State$* { *; }
-keep enum com.sumarize.voiceindo.viewmodel.ProcessingStep { *; }

-dontwarn org.tensorflow.**
-dontwarn com.google.flatbuffers.**
