# Ktor, OkHttp, kotlinx.serialization and Compose ship their own consumer rules; add app rules here only when a
# release build proves them necessary.

# ML Kit instantiates the component registrars named in the manifest by reflection; R8 full mode keeps only
# their class names, so no ML Kit component is registered and GmsBarcodeScanning.getClient() fails with a
# NullPointerException (SharedPrefManager resolves to null).
-keep class * implements com.google.firebase.components.ComponentRegistrar { <init>(); }
