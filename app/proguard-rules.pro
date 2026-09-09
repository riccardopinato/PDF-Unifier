# ==============================================================================
# REGOLE PROGUARD PER PDF UNIFIER PRO
# ==============================================================================

# 1. Regole generali per preservare gli attributi essenziali delle librerie
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# 2. PDFBox-Android (Keep completo delle classi e delle risorse di manipolazione PDF)
# Poiché PDFBox usa pesantemente la riflessione e il caricamento dinamico dei font
-keep class com.tom_roush.pdfbox.** { *; }
-dontwarn com.tom_roush.pdfbox.**

# Ignora avvisi legati a classi e dipendenze opzionali di PDFBox-Android
# (Ad esempio il decodificatore JP2 o pacchetti crittografici non inclusi)
-dontwarn com.gemalto.jp2.JP2Decoder
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn javax.xml.bind.**

# 3. Apache Commons Logging (utilizzato internamente da PDFBox)
-keep class org.apache.commons.logging.** { *; }
-dontwarn org.apache.commons.logging.**

# 4. Google Play Services & Google AdMob
# AdMob di norma integra le proprie regole, ma queste garantiscono la stabilità delle chiamate
-keep class com.google.android.gms.ads.** { *; }
-dontwarn com.google.android.gms.ads.**
-keep class com.google.ads.mediation.** { *; }
-dontwarn com.google.ads.mediation.**
-keep class com.google.android.gms.common.** { *; }
-dontwarn com.google.android.gms.common.**

# Preserva le classi necessarie per il funzionamento degli Intent di condivisione di AndroidX
-keep class androidx.core.content.FileProvider { *; }
