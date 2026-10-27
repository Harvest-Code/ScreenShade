# Proguard rules for ScreenShade
-keepattributes *Annotation*
-dontwarn java.lang.invoke.**

# Keep Android Components
-keep class com.screenshade.app.MainActivity { *; }
-keep class com.screenshade.app.ScreenShadeApp { *; }
-keep class com.screenshade.app.service.OverlayService { *; }
-keep class com.screenshade.app.service.OverlayService$* { *; }
-keep class com.screenshade.app.service.BootWatchdogJobService { *; }
-keep class com.screenshade.app.receiver.BootReceiver { *; }
-keep class com.screenshade.app.receiver.AutomationReceiver { *; }

# Keep UI, Dialogs, Data, Engine and Utils
-keep class com.screenshade.app.ui.** { *; }
-keep class com.screenshade.app.solar.** { *; }
-keep class com.screenshade.app.data.** { *; }
-keep class com.screenshade.app.util.** { *; }
