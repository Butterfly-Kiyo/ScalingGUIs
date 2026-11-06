package spazley.scalingguis.handlers;

import net.minecraftforge.common.config.ConfigElement;
import net.minecraftforge.fml.common.Loader;
import spazley.scalingguis.ScalingGUIs;
import spazley.scalingguis.config.CustomScales;
import spazley.scalingguis.config.JsonHelper;
import spazley.scalingguis.gui.guiconfig.ScaleConfigElement;
import spazley.scalingguis.gui.guiconfig.SnappingSliderEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;
import net.minecraftforge.fml.client.config.IConfigElement;

import java.io.*;
import java.util.*;

public class ConfigHandler
{
    public static Configuration config;
    public static CustomScales customScales;

    private static List<String> individualGuiClassNames;
    private static List<String> groupGuiClassNames;
    private static List<String> blacklistGuiClassNames;
    private static List<String> dynamicGuiClassNames;

    public static boolean logGuiClassNames = false;
    public static boolean persistentLog = true;
    public static boolean addDefaultBlacklist = true;
    public static boolean logGuiClassNamesChat = false;
    private static boolean sortLoggedAlphabetically = false;

    /** 1080p -> ~3x, 1440p -> 4x, 2160p -> 6x. */
    public static final double DEFAULT_REFERENCE_HEIGHT = 360.0D;

    /** A scale of 0 means AUTO: the scale is derived from the display resolution. */
    public static final double MIN_SCALE = 0.0D;

    /** Hard upper bound for the slider. The effective maximum also depends on the display resolution. */
    public static final double MAX_SCALE = 12.0D;

    /** Sliders snap to this step. */
    public static final double SCALE_STEP = 0.25D;

    public static final double AUTO_SCALE = 0.0D;

    /** Bumped whenever the meaning of stored values changes. */
    public static final int CONFIG_FORMAT_VERSION = 2;

    /** Enables fractional (non integer) GUI scales. Disable to fall back to vanilla 1x, 2x, 3x... behaviour. */
    public static boolean fractionalScaling = true;

    /** Logs every applied scale together with the resulting virtual resolution. Useful for support requests. */
    public static boolean logScaleChanges = false;

    /**
     * Height in GUI pixels that the AUTO scale aims for. Vanilla effectively targets 240-480 depending on
     * the display, this value is what makes a GUI look the same physical size across resolutions.
     */
    public static double referenceHeight = DEFAULT_REFERENCE_HEIGHT;

    //public static String configPath = "config/ScalingGUIs/ScalingGUIs.cfg";
    public static String scaleOverridesPath = "config/ScalingGUIs/ScalingGUIsCustomScales.json";

    public static void initConfigs()
    {
        if(config == null) {
            ScalingGUIs.logger.error("Attempted to load config before initialized.");
            return;
        }

        config.load();

        logGuiClassNames = config.getBoolean("logNames", Configuration.CATEGORY_GENERAL, false, "Enable logging of GUI class names in the Minecraft log.");
        persistentLog = config.getBoolean("persistentLog", Configuration.CATEGORY_GENERAL, true, "Maintain persistent log of GUI class names.");
        addDefaultBlacklist = config.getBoolean("updateBlacklist", Configuration.CATEGORY_GENERAL, true, "Update the blacklist with the current default blacklist. Will not remove custom blacklist entries.");
        logGuiClassNamesChat = config.getBoolean("logToChat", Configuration.CATEGORY_GENERAL, false, "Display opened GUI class names in the ingame chat.");
        sortLoggedAlphabetically = config.getBoolean("sortNames", Configuration.CATEGORY_GENERAL, false, "Diplay logged GUI class names alphabetically in select-value screens.");
        fractionalScaling = config.getBoolean("fractionalScaling", Configuration.CATEGORY_GENERAL, true,
                "Allow non integer GUI scales (for example 3.75x). This is what makes GUI size consistent across different resolutions. Disable to use vanilla integer scaling only.");
        logScaleChanges = config.getBoolean("logScaleChanges", Configuration.CATEGORY_GENERAL, false,
                "Write the scale that is being applied, and the resulting virtual resolution, to the log. Useful when reporting sizing problems.");
        referenceHeight = config.getFloat("referenceHeight", Configuration.CATEGORY_GENERAL, (float) DEFAULT_REFERENCE_HEIGHT, 200.0F, 1000.0F,
                "The height in GUI pixels that the AUTO scale targets. Raise it for smaller looking GUIs, lower it for bigger looking GUIs. " +
                        "360 is roughly what a 1080p screen shows at 3x.");

        config.save();


        File fileScaleOverrides = new File(scaleOverridesPath);
        customScales = JsonHelper.scalesFromJsonFile(fileScaleOverrides);
        migrateScales();
        customScales.checkCustomEntries();
        if (addDefaultBlacklist)
        {
            Map<String, List<String>> map = JsonHelper.defaultBlacklistsFromJsonFile();

            for (String key : map.keySet()) {
                if (Loader.isModLoaded(key)) {
                    customScales.blacklistGuiClassNames.addAll(map.get(key));
                }
            }
        }
        JsonHelper.scalesToJsonFile(fileScaleOverrides, customScales);

        individualGuiClassNames = JsonHelper.getKeyList(customScales.customIndividualGuiScales);
        groupGuiClassNames = JsonHelper.getKeyList(customScales.customGroupGuiScales);
        blacklistGuiClassNames = new ArrayList<>(customScales.blacklistGuiClassNames);
        dynamicGuiClassNames = new ArrayList<>(customScales.dynamicGuiScales);
    }

    /**
     * Converts scale values saved by older versions of the mod to the new meaning of "0".
     * <p>
     * Previously 9 was a sentinel meaning "use the main GUI scale" (and for the main GUI scale itself it
     * meant "let vanilla pick the biggest scale that fits", i.e. MAX). Now 0 is that sentinel, and AUTO is
     * a resolution based recommendation.
     */
    private static void migrateScales()
    {
        if (customScales.formatVersion >= CONFIG_FORMAT_VERSION) {
            return;
        }

        final double legacyDefault = 9.0D;

        if (customScales.guiScale == legacyDefault) {
            customScales.guiScale = AUTO_SCALE;
        }
        if (customScales.hudScale == legacyDefault) {
            customScales.hudScale = AUTO_SCALE;
        }
        if (customScales.tooltipScale == legacyDefault) {
            customScales.tooltipScale = AUTO_SCALE;
        }

        migrateEntries(customScales.customIndividualGuiScales, legacyDefault);
        migrateEntries(customScales.customGroupGuiScales, legacyDefault);

        customScales.formatVersion = CONFIG_FORMAT_VERSION;
    }

    private static void migrateEntries(com.google.gson.JsonObject json, double legacyDefault)
    {
        if (json == null) {
            return;
        }

        for (Map.Entry<String, com.google.gson.JsonElement> entry : json.entrySet()) {
            if (entry.getValue() == null || !entry.getValue().isJsonObject()) {
                continue;
            }

            com.google.gson.JsonObject obj = entry.getValue().getAsJsonObject();

            if (obj.has("scale") && obj.get("scale").isJsonPrimitive()
                    && obj.getAsJsonPrimitive("scale").isNumber()
                    && obj.getAsJsonPrimitive("scale").getAsDouble() == legacyDefault) {
                obj.addProperty("scale", AUTO_SCALE);
            }
        }
    }

    public static void saveConfigs()
    {
        ScalingGUIs.logger.info("Saving configs");
        File fileScaleOverrides = new File(scaleOverridesPath);

        Minecraft.getMinecraft().gameSettings.guiScale = (int) Math.round(getMainGuiScale());
        Minecraft.getMinecraft().gameSettings.saveOptions();
        config.save();
        JsonHelper.scalesToJsonFile(fileScaleOverrides, customScales);
        initConfigs();
    }

    public static boolean inIndividuals(String className)
    {
        return individualGuiClassNames.contains(className);
    }

    public static String inGroups(GuiScreen guiScreen)
    {
        for (String s : groupGuiClassNames) {
            try {
                Class<?> c = Class.forName(s);
                if (c.isInstance(guiScreen)) {
                    //ScalingGUIs.logger.info("Groups contains '" + guiScreen.getClass().getName() + "'.");
                    return s;
                }
            } catch(Exception e) {
                ScalingGUIs.logger.error("Unable to determine class for '" + s + "': ", e);
            }

        }

        return "NONE";
    }

    public static boolean inBlacklist(String className)
    {
        return blacklistGuiClassNames.contains(className);
    }

    public static boolean inDynamics(String className)
    {
        return dynamicGuiClassNames.contains(className);
    }

    public static double getIndividualScale(String className)
    {
        double scale = customScales.customIndividualGuiScales.getAsJsonObject(className).getAsJsonPrimitive("scale").getAsDouble();
        return scale == AUTO_SCALE ? getMainGuiScale() : scale;
    }

    public static double getGroupScale(String className)
    {
        double scale = customScales.customGroupGuiScales.getAsJsonObject(className).getAsJsonPrimitive("scale").getAsDouble();
        return scale == AUTO_SCALE ? getMainGuiScale() : scale;
    }

    /**
     * @return the scale configured for the main GUI, with 0 resolved to the resolution based recommendation.
     */
    public static double getMainGuiScale()
    {
        return customScales.guiScale == AUTO_SCALE ? getAutoGuiScale() : customScales.guiScale;
    }

    public static double getGuiScale(GuiScreen guiScreen)
    {
        if (inIndividuals(guiScreen.getClass().getName())) {
            return clampScale(getIndividualScale(guiScreen.getClass().getName()));
        } else if (!"NONE".equals(inGroups(guiScreen))) {
            return clampScale(getGroupScale(inGroups(guiScreen)));
        } else if (guiScreen instanceof GuiChat) {
            return getHudScale();
        } else {
            return clampScale(getMainGuiScale());
        }
    }

    public static double getTooltipScale()
    {
        return clampScale(customScales.tooltipScale == AUTO_SCALE ? getMainGuiScale() : customScales.tooltipScale);
    }

    public static double getHudScale()
    {
        return clampScale(customScales.hudScale == AUTO_SCALE ? getMainGuiScale() : customScales.hudScale);
    }

    /**
     * The scale that vanilla would have used on this display at most, i.e. the point where the screen would
     * become narrower than 320x240 GUI pixels. Scales above this are pointless because vanilla clamps them.
     */
    public static double getVanillaMaxScale()
    {
        Minecraft mc = Minecraft.getMinecraft();

        if (mc == null || mc.displayWidth <= 0 || mc.displayHeight <= 0) {
            return 1.0D;
        }

        double max = Math.min(mc.displayWidth / 320.0D, mc.displayHeight / 240.0D);
        return Math.max(1.0D, max);
    }

    /**
     * Upper bound shown on the sliders: never offer a scale vanilla would silently clamp.
     */
    public static double getMaxSelectableScale()
    {
        return Math.min(MAX_SCALE, getVanillaMaxScale());
    }

    /**
     * The AUTO scale: the display height divided by the reference height. On a 1080p screen this is 3x, on
     * 1440p it is 4x, on 2160p it is 6x. The reference height is configurable, so a player on an unusual
     * monitor or viewing distance can bias every AUTO value up or down at once.
     */
    public static double getAutoGuiScale()
    {
        Minecraft mc = Minecraft.getMinecraft();

        if (mc == null || mc.displayWidth <= 0 || mc.displayHeight <= 0) {
            return 1.0D;
        }

        double reference = referenceHeight > 0.0D ? referenceHeight : DEFAULT_REFERENCE_HEIGHT;
        return clampScale(quantize(mc.displayHeight / reference));
    }

    /**
     * Rounds a scale to the nearest {@link #SCALE_STEP}. Keeps {@link #AUTO_SCALE} intact.
     */
    public static double quantize(double scale)
    {
        if (scale <= 0.0D) {
            return AUTO_SCALE;
        }

        return Math.round(scale / SCALE_STEP) * SCALE_STEP;
    }

    /**
     * Clamps a scale into the range this display can actually use. When fractional scaling is disabled the
     * result is rounded to a whole number so the game behaves like vanilla again.
     */
    public static double clampScale(double scale)
    {
        if (scale <= 0.0D) {
            return AUTO_SCALE;
        }

        if (!fractionalScaling) {
            scale = Math.round(scale);
        }

        if (scale < 1.0D) {
            scale = 1.0D;
        }

        double max = getVanillaMaxScale();

        if (!fractionalScaling) {
            //Vanilla can only reach whole scale factors.
            max = Math.max(1.0D, Math.floor(max));
        }

        if (scale > max) {
            scale = max;
        }

        return scale;
    }


    public static List<IConfigElement> getMainsList()
    {
        List<IConfigElement> list = new ArrayList<>();
        double defaultScale = AUTO_SCALE; // Auto follows the main GUI scale / resolution
        double maxScale = getMaxSelectableScale();

        Property guiScaleProp = new Property("guiScale", String.valueOf(customScales.guiScale), Property.Type.DOUBLE, "scalingguis.config.main.guiscale");
        guiScaleProp.setDefaultValue(defaultScale);
        guiScaleProp.setMinValue(MIN_SCALE);
        guiScaleProp.setMaxValue(maxScale);
        list.add(new ScaleConfigElement(guiScaleProp).setCustomListEntryClass(SnappingSliderEntry.class));

        Property hudScaleProp = new Property("hudScale", String.valueOf(customScales.hudScale), Property.Type.DOUBLE, "scalingguis.config.main.hudscale");
        hudScaleProp.setDefaultValue(defaultScale);
        hudScaleProp.setMinValue(MIN_SCALE);
        hudScaleProp.setMaxValue(maxScale);
        list.add(new ScaleConfigElement(hudScaleProp).setCustomListEntryClass(SnappingSliderEntry.class));

        Property tooltipScaleProp = new Property("tooltipScale", String.valueOf(customScales.tooltipScale), Property.Type.DOUBLE, "scalingguis.config.main.tooltipscale");
        tooltipScaleProp.setDefaultValue(defaultScale);
        tooltipScaleProp.setMinValue(MIN_SCALE);
        tooltipScaleProp.setMaxValue(maxScale);
        list.add(new ScaleConfigElement(tooltipScaleProp).setCustomListEntryClass(SnappingSliderEntry.class));

        return list;
    }

    public static List<IConfigElement> getIndividualsList()
    {
        List<IConfigElement> list = new ArrayList<>();
        double defaultScale = AUTO_SCALE;
        double maxScale = getMaxSelectableScale();

        for (String s : JsonHelper.getKeyList(customScales.customIndividualGuiScales)) {
            double val = customScales.customIndividualGuiScales.getAsJsonObject(s).get("scale").getAsDouble();
            String name = customScales.customIndividualGuiScales.getAsJsonObject(s).get("name").getAsString();

            Property prop = new Property(name, String.valueOf(val), Property.Type.DOUBLE, "");
            prop.setDefaultValue(defaultScale);
            prop.setMinValue(MIN_SCALE);
            prop.setMaxValue(maxScale);
            prop.setComment(s);
            list.add(new ScaleConfigElement(prop).setCustomListEntryClass(SnappingSliderEntry.class));
        }

        return list;
    }

    public static List<IConfigElement> getGroupsList()
    {
        List<IConfigElement> list = new ArrayList<>();
        double defaultScale = AUTO_SCALE;
        double maxScale = getMaxSelectableScale();

        for (String s : JsonHelper.getKeyList(customScales.customGroupGuiScales)) {
            double val = customScales.customGroupGuiScales.getAsJsonObject(s).get("scale").getAsDouble();
            String name = customScales.customGroupGuiScales.getAsJsonObject(s).get("name").getAsString();

            Property prop = new Property(name, String.valueOf(val), Property.Type.DOUBLE);
            prop.setDefaultValue(defaultScale);
            prop.setMinValue(MIN_SCALE);
            prop.setMaxValue(maxScale);
            prop.setComment(s);
            list.add(new ScaleConfigElement(prop).setCustomListEntryClass(SnappingSliderEntry.class));
        }

        return list;
    }

    public static List<IConfigElement> getBlacklistElementsList()
    {
        List<IConfigElement> list = new ArrayList<>();

        Property prop = new Property("blacklist", blacklistGuiClassNames.toArray(new String[0]), Property.Type.STRING, "scalingguis.config.blacklist.title");

        list.add(new ConfigElement(prop));

        return list;
    }

    public static List<IConfigElement> getDynamicsList()
    {
        List<IConfigElement> list = new ArrayList<>();

        Property prop = new Property("dynamics", dynamicGuiClassNames.toArray(new String[0]), Property.Type.STRING, "scalingguis.config.dynamics.title");

        list.add(new ConfigElement(prop));

        return list;
    }

    public static Map<Object, String> getUnusedLoggedClassNames()
    {
        List<String> list = new ArrayList<>(customScales.loggedGuiClassNames);

        list.removeAll(individualGuiClassNames);
        list.removeAll(groupGuiClassNames);
        list.removeAll(blacklistGuiClassNames);
        list.removeAll(dynamicGuiClassNames);

        if (sortLoggedAlphabetically) {
            Collections.sort(list);
        } else {
            Collections.reverse(list);
        }

        Map<Object, String> map = new LinkedHashMap<>();

        for (String s : list) {
            map.put((Object)s,s);
        }

        return map;
    }

    public static void logClassName(String className)
    {
        customScales.loggedGuiClassNames.remove(className);
        customScales.loggedGuiClassNames.add(className);
    }

    public static void removeIndividualClassName(String className)
    {
        individualGuiClassNames.remove(className);
    }

    public static void removeGroupClassName(String className)
    {
        groupGuiClassNames.remove(className);
    }

}
