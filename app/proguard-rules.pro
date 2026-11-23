# Open-source app: keep names so crash traces point to real classes and lines.
-dontobfuscate
-keepattributes SourceFile,LineNumberTable,Signature,InnerClasses,EnclosingMethod,*Annotation*

# Saved data (decks and cards) is read and written with Gson by reflection.
-keep class app.mantra.cards.** { *; }

-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn org.slf4j.**
