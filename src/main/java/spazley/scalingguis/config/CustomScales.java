package spazley.scalingguis.config;

import com.google.gson.JsonObject;

import java.util.*;

public class CustomScales {
    /** 0 means AUTO: the scale is derived from the display resolution (main GUI) or follows it (others). */
    public double guiScale = 0.0D;
    public double hudScale = 0.0D;
    public double tooltipScale = 0.0D;
    /** Bumped when the meaning of the stored values changes, used to migrate old configs. */
    public int formatVersion = 0;
    public JsonObject customIndividualGuiScales = new JsonObject();
    public JsonObject customGroupGuiScales = new JsonObject();
    //public SortedSet<String> loggedGuiClassNames = new TreeSet<>();
    public Set<String> loggedGuiClassNames = new LinkedHashSet<>();
    //public List<String> loggedGuiClassNames = new ArrayList<>();
    public SortedSet<String> blacklistGuiClassNames = new TreeSet<>();// blacklistDefault();
    public SortedSet<String> dynamicGuiScales = new TreeSet<>();


    public void checkCustomEntries()
    {
        for (String s : JsonHelper.getKeyList(customIndividualGuiScales)) {
            if (!customIndividualGuiScales.getAsJsonObject(s).has("scale")) {
                customIndividualGuiScales.getAsJsonObject(s).addProperty("scale", 0.0D);
            }
            if (!customIndividualGuiScales.getAsJsonObject(s).has("name")) {
                customIndividualGuiScales.getAsJsonObject(s).addProperty("name", s);
            }
        }
        for (String s : JsonHelper.getKeyList(customGroupGuiScales)) {
            if (!customGroupGuiScales.getAsJsonObject(s).has("scale")) {
                customGroupGuiScales.getAsJsonObject(s).addProperty("scale", 0.0D);
            }
            if (!customGroupGuiScales.getAsJsonObject(s).has("name")) {
                customGroupGuiScales.getAsJsonObject(s).addProperty("name", s);
            }
        }
    }

}
