package spazley.scalingguis.asm;

import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;

import javax.annotation.Nullable;
import java.util.Map;

/**
 * Coremod entry point. Registered through the {@code FMLCorePlugin} manifest attribute, this plugin only
 * contributes {@link ScaledResolutionTransformer}; the mod itself is a normal {@code @Mod} living in the
 * same jar ({@code FMLCorePluginContainsFMLMod}).
 * <p>
 * No Mixin is used on purpose: a raw {@code IClassTransformer} works identically on Forge 1.12.2 and on
 * Cleanroom, without requiring MixinBooter or a refmap.
 */
@IFMLLoadingPlugin.Name("ScalingGUIs Core")
@IFMLLoadingPlugin.MCVersion("1.12.2")
@IFMLLoadingPlugin.TransformerExclusions({"spazley.scalingguis.asm"})
public class ScalingGUIsCorePlugin implements IFMLLoadingPlugin
{
    @Override
    public String[] getASMTransformerClass()
    {
        return new String[] { ScaledResolutionTransformer.class.getName() };
    }

    @Override
    public String getModContainerClass()
    {
        return null;
    }

    @Nullable
    @Override
    public String getSetupClass()
    {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data)
    {
    }

    @Override
    public String getAccessTransformerClass()
    {
        return null;
    }
}
