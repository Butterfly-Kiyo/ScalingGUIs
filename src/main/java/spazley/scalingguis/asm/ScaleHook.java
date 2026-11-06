package spazley.scalingguis.asm;

/**
 * Runtime bridge between the mod code and the patched {@code net.minecraft.client.gui.ScaledResolution}.
 * <p>
 * This class is injected into {@code ScaledResolution}'s constructor by
 * {@link ScaledResolutionTransformer}, therefore it intentionally has <b>no</b> dependency on any
 * Minecraft class, so it can be loaded as early as the vanilla class is being transformed.
 * <p>
 * The whole fractional scaling feature rests on a single idea: vanilla {@code ScaledResolution} can only
 * use an integer scale factor (1..min(width/320, height/240)) because {@code getScaleFactor()} returns an
 * {@code int}. On high DPI displays the "ideal" scale usually sits between two integers (e.g. 3.5x on a
 * 1440p screen), which is why vanilla (and every mod that just pokes {@code gameSettings.guiScale}) can
 * only offer "too big" or "too small".
 * <p>
 * By rewriting the width/height/scale fields of every {@code ScaledResolution} instance after vanilla
 * computed them, the virtual resolution becomes fractional. Everything downstream (the ortho projection in
 * {@code EntityRenderer.setupOverlayRendering}, HUD layout, GUI layout, mouse mapping) is derived from
 * those values, so a single hook is enough to give the whole client fractional GUI scaling.
 */
public final class ScaleHook
{
    /** Scale currently in effect, or a value &lt;= 0 to fall back to vanilla behaviour. */
    private static volatile double activeScale = -1.0D;

    private ScaleHook()
    {
    }

    /**
     * @param scale the fractional scale factor to use (real pixels per GUI pixel) or a value &lt;= 0
     *              to restore vanilla integer scaling.
     */
    public static void setActiveScale(double scale)
    {
        activeScale = (scale > 0.0D && !Double.isNaN(scale) && !Double.isInfinite(scale)) ? scale : -1.0D;
    }

    public static double getActiveScale()
    {
        return activeScale;
    }

    public static boolean isActive()
    {
        return activeScale > 0.0D;
    }

    /**
     * Called (via injected bytecode) at the end of {@code ScaledResolution(Minecraft)}.
     *
     * @param scaledWidthD   width in GUI pixels vanilla computed (displayWidth / scaleFactor)
     * @param scaledHeightD  height in GUI pixels vanilla computed (displayHeight / scaleFactor)
     * @param scaleFactor    integer scale factor vanilla computed
     * @return {widthD, heightD, width, height, scaleFactor} with fractional scaling applied
     */
    public static double[] resolveScaledResolution(double scaledWidthD, double scaledHeightD, int scaleFactor)
    {
        double scale = activeScale;

        if (scale <= 0.0D || scaleFactor <= 0 || scaledWidthD <= 0.0D || scaledHeightD <= 0.0D) {
            return new double[] {
                    scaledWidthD,
                    scaledHeightD,
                    Math.ceil(scaledWidthD),
                    Math.ceil(scaledHeightD),
                    scaleFactor
            };
        }

        // Recover the real framebuffer size. Rounding removes the floating point error introduced by
        // vanilla's division (2560/6*6 == 2559.9999999999995 for example).
        double displayWidth = Math.round(scaledWidthD * (double) scaleFactor);
        double displayHeight = Math.round(scaledHeightD * (double) scaleFactor);

        double widthD = displayWidth / scale;
        double heightD = displayHeight / scale;

        // getScaleFactor() can only report an integer; report the nearest one so that third party code
        // using it for its own maths stays as close as possible to the truth.
        double reportedScale = Math.round(scale);
        if (reportedScale < 1.0D) {
            reportedScale = 1.0D;
        }

        return new double[] {
                widthD,
                heightD,
                Math.ceil(widthD),
                Math.ceil(heightD),
                reportedScale
        };
    }
}
