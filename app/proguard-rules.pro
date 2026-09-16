# Add project-specific ProGuard rules here.
# PdfBox-Android and AndroidX libraries ship their own consumer rules, but
# PdfBox-Android in particular does a lot of reflective/dynamic class loading
# (font subsetting, filters, COS object graph) that R8 can't see statically —
# keep it wholesale rather than risk a runtime crash from an over-eager strip.
-keep class com.tom_roush.** { *; }
-dontwarn com.tom_roush.**
