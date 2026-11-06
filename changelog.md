Changelog

1.12.2-2.0.0
* Added fractional (non-integer) scaling in 0.25 steps for GUI, HUD and Tooltip scales
  * Patches ScaledResolution through an embedded coremod, so the projection, HUD, mouse coordinates and GUI layouts all agree
  * Auto scale is now adaptive: display height divided by a configurable reference height (default 360 = the 1080p @ 3x look)
* Changed scale settings from integer to double; slider maximum is now the highest scale the current resolution can actually use
* Changed the old "default (max scale)" option into "follow main GUI scale"; existing configs are migrated automatically
* Added fractionalScaling config (set to false to fall back to the vanilla integer behaviour)
* Added logScaleChanges config for troubleshooting sizing problems
* Bug Fix - Scale sliders no longer offer settings that vanilla would silently clamp on high resolution displays
* Updated build to RetroFuturamaGradle 1.4.2 and the GTNH maven repository

1.12.2-1.0.3.1
* Bug Fix - Fixes ConcurrentModificationException (#1). Thanks to Srdjan-V.

1.12.2-1.0.3.0
* Added dynamic scaling for whitelisted GuiContainers
* Bug Fix - Button click no longer playing twice on Gui Scales when Optifine is installed

1.12.2-1.0.2.1
* Bug Fix - Video Settings now scales correctly after changing the main GUI scale settings

1.12.2-1.0.2.0
* Added compatibility with Optifine

1.12.2-1.0.1.0
* Added delete function to custom entry screens

1.12.2-1.0.0.1
* Select-value screens now return to parent screen on ESC press

1.12.2-1.0.0.0
* Initial release

1.12.2-0.0.3.4
* Prep for initial release.

1.12.2-0.0.3.3
* Bug Fix - Incorrect import statement. (scala Array instead of java.util)

1.12.2-0.0.3.2
* Changed RenderTooltipEvent.PostText method priority to catch Apple Skin tooltip info

1.12.2-0.0.3.1
* Bug Fix - New GUI entry screen now goes back when "Done" is pressed and the gui name field is blank

1.12.2-0.0.3.0
* Changed select-value screens to list GUI names in order from most recent to least
  * Added config to display in alphabetical order

1.12.2-0.0.2.0

* Added blacklist to handle screens consisting of multiple GUIs (specifically Tinkers Construct)
  * Added default blacklist values to handle known cases (based on mods present)
  * Added config to update blacklist with default values (default true)
* Added config to log GUI class names to chat (default false)
* Rearranged settings to be a little cleaner due to additon of blacklist
  * General settings now have their own category screen
* Changed tooltip scaling to lowest event priority to prevent firing before More Overlays draws its JEI item search overlay