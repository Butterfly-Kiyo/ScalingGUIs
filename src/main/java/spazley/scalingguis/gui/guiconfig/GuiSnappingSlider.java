package spazley.scalingguis.gui.guiconfig;

import spazley.scalingguis.handlers.ConfigHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;

import javax.annotation.Nullable;

public class GuiSnappingSlider extends net.minecraftforge.fml.client.config.GuiSlider {

    private boolean snap = true;
    /** Value the slider snapped to, in scale units. */
    private double snappedValue;

    public GuiSnappingSlider(int id, int xPos, int yPos, int width, int height, String prefix, String suf, double minVal, double maxVal, double currentVal, boolean showDec, boolean drawStr)
    {
        this(id, xPos, yPos, width, height, prefix, suf, minVal, maxVal, currentVal, showDec, drawStr, null);
    }

    public GuiSnappingSlider(int id, int xPos, int yPos, int width, int height, String prefix, String suf, double minVal, double maxVal, double currentVal, boolean showDec, boolean drawStr, @Nullable ISlider par)
    {
        super(id, xPos, yPos, width, height, prefix, suf, minVal, maxVal, currentVal, showDec, drawStr, par);
        updateSlider();
    }

    @Override
    public int getHoverState(boolean par1)
    {
        return super.getHoverState(par1);
    }

    @Override
    protected void mouseDragged(Minecraft par1Minecraft, int par2, int par3)
    {
        super.mouseDragged(par1Minecraft, par2, par3);
    }

    @Override
    public boolean mousePressed(Minecraft par1Minecraft, int par2, int par3)
    {
        return super.mousePressed(par1Minecraft, par2, par3);
    }

    //Modified to snap the slider button to the middle of each selectable scale's region
    @Override
    public void updateSlider()
    {
        if (this.sliderValue < 0.0D)
        {
            this.sliderValue = 0.0D;
        }

        if (this.sliderValue > 1.0D)
        {
            this.sliderValue = 1.0D;
        }

        double range = maxValue - minValue;
        double raw = sliderValue * range + minValue;

        //Snap to 0.25 steps so the player can actually reach the in-between scales that make
        //high DPI displays look right (3.75x, 4.25x, ...) instead of only whole numbers.
        snappedValue = ConfigHandler.quantize(raw);

        if (snappedValue < minValue)
        {
            snappedValue = minValue;
        }

        if (snappedValue > maxValue)
        {
            snappedValue = maxValue;
        }

        if (snap && range > 0.0D)
        {
            sliderValue = (snappedValue - minValue) / range;
        }

        if (drawString) {
            displayString = dispString + getScaleString(snappedValue) + suffix;
        }

        if (parent != null)
        {
            parent.onChangeSliderValue(this);
        }
    }

    /**
     * @return a short, human readable description of a scale value.
     */
    public static String getScaleString(double scale)
    {
        if (scale <= ConfigHandler.AUTO_SCALE)
        {
            return I18n.format("scalingguis.slidertext.auto", formatScale(ConfigHandler.getAutoGuiScale()));
        }

        if (scale == 1.0D)
        {
            return I18n.format("scalingguis.slidertext.small");
        }

        if (scale == 2.0D)
        {
            return I18n.format("scalingguis.slidertext.normal");
        }

        if (scale == 3.0D)
        {
            return I18n.format("scalingguis.slidertext.large");
        }

        return formatScale(scale);
    }

    /** Formats 4.0 as "4" and 3.75 as "3.75". */
    public static String formatScale(double scale)
    {
        if (scale == Math.floor(scale))
        {
            return Long.toString((long) scale);
        }

        String text = Double.toString(Math.round(scale * 100.0D) / 100.0D);

        if (text.endsWith("0"))
        {
            text = text.substring(0, text.length() - 1);
        }

        return text;
    }

    public int getValueInt()
    {
        return (int) Math.round(getValue());
    }

    @Override
    public double getValue()
    {
        return snappedValue;
    }

    public void setValue(double d)
    {
        super.setValue(d);
        updateSlider();
    }

}
