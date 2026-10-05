# Retrofit, kotlinx.serialization, Room and Media3 ship their own consumer rules.
# Those rules retain service signatures/annotations, serializer lookup and generated
# database construction while allowing the rest of the app to be optimized.

# Nextlib 1.10.1-0.13.0 protects its native entry points, but FFmpeg's audio JNI
# also looks up this Java callback by its original name and parameter signature.
# Keep the callback and descriptor class names; no package-wide keep is needed.
# https://github.com/anilbeesetti/nextlib/blob/main/media3ext/src/main/cpp/ffaudio.cpp
-keepclassmembers,includedescriptorclasses class io.github.anilbeesetti.nextlib.media3ext.ffdecoder.FfmpegAudioDecoder {
    private java.nio.ByteBuffer growOutputBuffer(androidx.media3.decoder.SimpleDecoderOutputBuffer, int);
}
