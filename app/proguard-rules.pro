# Project-specific R8 rules.
#
# kotlinx.serialization, OkHttp, CameraX, ML Kit and Navigation ship their own
# consumer rules, so no extra keep rules are needed for them.
#
# Keep line numbers so release crash stack traces stay readable.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
