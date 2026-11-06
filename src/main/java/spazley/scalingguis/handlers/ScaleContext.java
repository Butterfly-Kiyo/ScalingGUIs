package spazley.scalingguis.handlers;

import net.minecraft.client.Minecraft;
import spazley.scalingguis.ScalingGUIs;
import spazley.scalingguis.asm.ScaleHook;

/**
 * Holds the fractional scale that should currently be applied to anything that asks for a
 * {@code ScaledResolution}: the open GUI, the HUD, or a tooltip.
 * <p>
 * The value is pushed down to {@link ScaleHook}, which is called from the patched
 * {@code ScaledResolution} constructor. A value &lt;= 0 means "use vanilla integer scaling".
 */
public final class ScaleContext
{
    private static double currentScale = -1.0D;

    /** The value gameSettings.guiScale had before this mod started overriding it, -1 when unknown. */
    private static int vanillaGuiScaleBackup = -1;

    /** Guards the optional diagnostic log so it only fires when something actually changed. */
    private static double lastLoggedScale = -2.0D;

    private ScaleContext()
    {
    }

    /** @return the scale currently in effect, or a value &lt;= 0 when vanilla scaling is used. */
    public static double get()
    {
        return currentScale;
    }

    public static boolean isActive()
    {
        return currentScale > 0.0D;
    }

    /**
     * Makes every {@code ScaledResolution} created from now on use {@code scale} real pixels per GUI pixel.
     */
    public static void set(double scale)
    {
        if (!(scale > 0.0D) || Double.isNaN(scale) || Double.isInfinite(scale)) {
            clear();
            return;
        }

        currentScale = scale;
        ScaleHook.setActiveScale(scale);

        // Keep the vanilla field roughly in sync. Plenty of mods (and ResourceLoader style code) still
        // read gameSettings.guiScale to work out their own sizes; the nearest integer is the best we can do.
        Minecraft mc = Minecraft.getMinecraft();

        if (mc != null && mc.gameSettings != null) {
            if (vanillaGuiScaleBackup < 0) {
                vanillaGuiScaleBackup = mc.gameSettings.guiScale;
            }

            int rounded = (int) Math.round(scale);

            if (rounded < 1) {
                rounded = 1;
            }

            mc.gameSettings.guiScale = rounded;
        }

        logApplied(scale, mc);
    }

    /** Restores vanilla integer scaling. */
    public static void clear()
    {
        boolean wasActive = currentScale > 0.0D;

        currentScale = -1.0D;
        ScaleHook.setActiveScale(-1.0D);

        Minecraft mc = Minecraft.getMinecraft();

        if (mc != null && mc.gameSettings != null && vanillaGuiScaleBackup >= 0) {
            mc.gameSettings.guiScale = vanillaGuiScaleBackup;
        }

        if (wasActive) {
            logRestored(mc);
        }
    }

    private static void logApplied(double scale, Minecraft mc)
    {
        if (!ConfigHandler.logScaleChanges || lastLoggedScale == scale) {
            return;
        }

        lastLoggedScale = scale;

        if (ScalingGUIs.logger == null || mc == null || mc.displayWidth <= 0 || mc.displayHeight <= 0) {
            return;
        }

        ScalingGUIs.logger.info("Applied GUI scale {}x -> virtual resolution {}x{} (display {}x{}, reference height {})",
                scale,
                (int) Math.ceil(mc.displayWidth / scale),
                (int) Math.ceil(mc.displayHeight / scale),
                mc.displayWidth,
                mc.displayHeight,
                ConfigHandler.referenceHeight);
    }

    private static void logRestored(Minecraft mc)
    {
        lastLoggedScale = -2.0D;

        if (!ConfigHandler.logScaleChanges || ScalingGUIs.logger == null) {
            return;
        }

        ScalingGUIs.logger.info("Restored vanilla GUI scaling (guiScale={})",
                mc != null && mc.gameSettings != null ? mc.gameSettings.guiScale : -1);
    }
}
