# Add project specific ProGuard rules here.
# Kept minimal for now — Hilt, kotlinx.serialization, ONNX Runtime and
# Retrofit each ship consumer rules via their AARs, so no manual keep
# rules are needed yet. Add entries here if release-build crashes point
# to something being stripped.

# Native (JNI) libraries — their Java classes are looked up from C++ by name,
# which R8 can't see, so stripping/renaming them crashes at runtime.
-keep class ai.onnxruntime.** { *; }
-keep class com.google.ai.edge.litertlm.** { *; }
-dontwarn com.google.ai.edge.litertlm.**
