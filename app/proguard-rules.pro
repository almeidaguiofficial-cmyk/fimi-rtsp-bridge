# Manter classes da RootEncoder e do MediaProjection.
-keep class com.pedro.** { *; }
-keep class android.media.projection.** { *; }
-dontwarn com.pedro.**

# Se minify for habilitado um dia: o rtmp-client (usado pelo media3-datasource-rtmp)
# acessa net.butterflytv.rtmp_client.RtmpClient por JNI e não pode ser ofuscado.
# -keep class net.butterflytv.rtmp_client.** { *; }
