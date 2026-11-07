package spazley.scalingguis.handlers;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.FMLClientHandler;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import spazley.scalingguis.ScalingGUIs;
import spazley.scalingguis.gui.videosettings.GuiVideoSettingsButton;

import java.util.List;
import java.util.ListIterator;

public class ClientEventHandler {
    public ClientEventHandler()
    {
        MinecraftForge.EVENT_BUS.register(this);
    }


    private String lastGui = null;

    private boolean renderGuiTakenOver = false;
    private boolean ingameRenderTakenOver = false;

    private static boolean cancelGuiVideoSettings = false; //Prevent GuiVideoSettings from changing scale after opening GuiConfigSG


    //Update game scale to reflect setting for opened GUI.
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void guiPreInit(GuiScreenEvent.InitGuiEvent.Pre e)
    {
        if (e.getGui() != null) {
            try{
                String name = e.getGui().getClass().getName();

                if (renderGuiTakenOver) {
                    renderGuiTakenOver = false;
                    return;
                }
                //Prevent GuiVideoSettings from changing scale after opening GuiConfigSG
                if ((e.getGui() instanceof GuiVideoSettings) && cancelGuiVideoSettings) {
                    e.setCanceled(true);
                    setCancelGuiVideoSettings(false);
                    return;
                }

                if (ConfigHandler.logGuiClassNames && !name.equals(lastGui)) {
                    ScalingGUIs.logger.info("Opened GUI: " + name);
                }
                if (ConfigHandler.logGuiClassNamesChat && !name.equals(lastGui) && Minecraft.getMinecraft().world != null)
                {
                    Minecraft.getMinecraft().player.sendMessage(new TextComponentString("Opened GUI: " + name));
                }
                if (ConfigHandler.persistentLog && !name.equals(lastGui)) {
                    ConfigHandler.logClassName(name);
                }

                lastGui = name;

                GuiScreen gui = e.getGui();
                double newScale;

                if (ConfigHandler.inBlacklist(name)) {
                    //This GUI cannot cope with a rescaled world, so let vanilla handle it.
                    ScaleContext.clear();
                    newScale = -1.0D;
                } else if (ConfigHandler.inDynamics(name) && gui instanceof GuiContainer) {
                    newScale = getDynamicScale((GuiContainer) gui);
                    ScaleContext.set(newScale);
                } else {
                    newScale = ConfigHandler.getGuiScale(gui);
                    ScaleContext.set(newScale);
                }

                int w;
                int h;
                {
                    ScaledResolution scaledResolution = new ScaledResolution(Minecraft.getMinecraft());
                    w = scaledResolution.getScaledWidth();
                    h = scaledResolution.getScaledHeight();
                }

                if (gui.width == w && gui.height == h) {
                    //Already laid out for this scale, nothing to do.
                    return;
                }

                renderGuiTakenOver = true;
                e.setCanceled(true);
                gui.setWorldAndResolution(Minecraft.getMinecraft(), w, h);

            } catch(Exception ex) {
                renderGuiTakenOver = false;
                ScaleContext.clear();

                if (ScalingGUIs.logger != null) {
                    ScalingGUIs.logger.error("Unable to apply a GUI scale to " + e.getGui().getClass().getName(), ex);
                }
            }
        } else {
            lastGui = null;
        }
    }

    /**
     * Finds the biggest scale (in slider steps) at which the container still fits on screen. Formerly this
     * was an integer loop over vanilla scale factors.
     */
    private double getDynamicScale(GuiContainer container)
    {
        int xSize = container.getXSize();
        int ySize = container.getYSize();
        Minecraft mc = Minecraft.getMinecraft();

        double step = ConfigHandler.scaleStep > 0.0D ? ConfigHandler.scaleStep : ConfigHandler.DEFAULT_SCALE_STEP;
        int steps = (int) Math.max(1L, Math.round(ConfigHandler.getMaxSelectableScale() / step));

        for (int i = steps; i >= 1; i--) {
            double scale = ConfigHandler.quantize(i * step);
            double width = Math.floor(mc.displayWidth / scale);
            double height = Math.floor(mc.displayHeight / scale);

            if (width > xSize && height > ySize) {
                return scale;
            }
        }

        return 1.0D;
    }

    //Copied from Vise and modified to fit the context of ScalingGUIs. See license.
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public void onPreRenderGameOverlay(RenderGameOverlayEvent.Pre e)
    {
        if (e.getType() == RenderGameOverlayEvent.ElementType.ALL) {
            if (ingameRenderTakenOver) {
                return;
            }

            Minecraft mc = Minecraft.getMinecraft();
            double previousScale = ScaleContext.get();

            try {
                ingameRenderTakenOver = true;
                ScaleContext.set(ConfigHandler.getHudScale());
                //The HUD is positioned from its own ScaledResolution, so the ortho projection has to be
                //rebuilt for the HUD scale before rendering it.
                mc.entityRenderer.setupOverlayRendering();
                e.setCanceled(true);
                mc.ingameGUI.renderGameOverlay(e.getPartialTicks());
            } finally {
                ingameRenderTakenOver = false;

                if (previousScale > 0.0D) {
                    ScaleContext.set(previousScale);
                } else {
                    ScaleContext.clear();
                }

                mc.entityRenderer.setupOverlayRendering();
            }
        }
    }

    //Causes toasts to scale with the HUD while no GUI is open
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onClientTick(TickEvent.ClientTickEvent e)
    {
        Minecraft minecraft = Minecraft.getMinecraft();

        if (minecraft.currentScreen == null)
        {
            //With no screen open the HUD (and anything else drawn as an overlay) is what needs the scale.
            ScaleContext.set(ConfigHandler.getHudScale());
        }
    }


    //Copied from Vise and modified to fit the context of ScalingGUIs. See license.
    //Set to LOWEST to ensure the scale is set after More Overlays renders its itemsearch overlay.
    //May need to play around with priority if other mods render things with the RenderTooltipEvent.Pre event
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public void onPreRenderTooltip(RenderTooltipEvent.Pre e) {
        double newScale = ConfigHandler.getTooltipScale();
        double currentScale = ScaleContext.isActive()
                ? ScaleContext.get()
                : new ScaledResolution(Minecraft.getMinecraft()).getScaleFactor();

        GlStateManager.pushMatrix();

        if (newScale > 0.0D && currentScale > 0.0D) {
            float f = (float) (newScale / currentScale);
            GlStateManager.scale(f, f, f);
            e.setX((int)(e.getX()/f));
            e.setY((int)(e.getY()/f));
            e.setScreenWidth((int)(e.getScreenWidth()/f));
            e.setScreenHeight((int)(e.getScreenHeight()/f));
            e.setMaxWidth((int)(e.getMaxWidth()/f));
        }
    }

    //Copied from Vise. See license.
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public void onPostRenderTooltip(RenderTooltipEvent.PostText e) {
        GlStateManager.popMatrix();
    }


    // Alter the GuiVideoSettings to set the scale button to open a custom SG scales GUI
    @SubscribeEvent
    public void onPostInit(GuiScreenEvent.InitGuiEvent.Post e)
    {
        if (ConfigHandler.logScaleChanges && ScalingGUIs.logger != null) {
            GuiScreen gui = e.getGui();

            if (gui != null) {
                ScalingGUIs.logger.info("Initialized {} at {}x{} (scale {}, {} buttons)", gui.getClass().getSimpleName(),
                        gui.width, gui.height, ScaleContext.get(), e.getButtonList().size());
            }
        }

        if (e.getGui() instanceof GuiVideoSettings) {
            GuiVideoSettings gvs = (GuiVideoSettings) e.getGui();
            if (FMLClientHandler.instance().hasOptifine()) {
                //replaceGuiScaleOptionOptifine(gvs);
                List<GuiButton> buttonList = e.getButtonList();
                e.setButtonList(replaceGuiScaleOptionOptifine(buttonList));
            } else {
                replaceGuiScaleOption(gvs);
            }
        }
    }

    public void replaceGuiScaleOption(GuiVideoSettings gvs)
    {
        List<GuiOptionsRowList.Row> options = ((GuiOptionsRowList)gvs.optionsRowList).options;

        int i = 0;
        for (GuiOptionsRowList.Row row : options) {
            GuiButton buttonA = row.buttonA;
            GuiButton buttonB = row.buttonB;
            boolean found = false;
            if (buttonA instanceof GuiOptionButton && ((GuiOptionButton)buttonA).getOption() == GameSettings.Options.GUI_SCALE) {
                buttonA = new GuiVideoSettingsButton(row.buttonA.id, buttonA.x, buttonA.y, buttonA.width, buttonA.height, I18n.format("scalingguis.videosettings.button"));
                found = true;
            }
            if (buttonB instanceof GuiOptionButton && ((GuiOptionButton)buttonB).getOption() == GameSettings.Options.GUI_SCALE) {
                buttonB = new GuiVideoSettingsButton(row.buttonB.id, buttonB.x, buttonB.y, buttonB.width, buttonB.height, I18n.format("scalingguis.videosettings.button"));
                found = true;
            }
            if (found) {
                options.set(i, new GuiOptionsRowList.Row(buttonA, buttonB));
                break;
            }
            i++;
        }
    }

    //public void replaceGuiScaleOptionOptifine(GuiVideoSettings gvs)
    public List<GuiButton> replaceGuiScaleOptionOptifine(List<GuiButton> buttonList)
    {
       ListIterator<GuiButton> iter = buttonList.listIterator();

       while (iter.hasNext()) {
           int index = iter.nextIndex();
           GuiButton guiButton = iter.next();

           if (guiButton instanceof GuiOptionButton && ((GuiOptionButton)guiButton).getOption() == GameSettings.Options.GUI_SCALE) {
               GuiButton newGuiButton = new GuiVideoSettingsButton(guiButton.id, guiButton.x, guiButton.y, guiButton.width, guiButton.height, I18n.format("scalingguis.videosettings.button"));
               buttonList.set(index, newGuiButton);
               break;
           }
       }

       return buttonList;
    }

    public static void setCancelGuiVideoSettings(boolean bool)
    {
        cancelGuiVideoSettings = bool;
    }
}
