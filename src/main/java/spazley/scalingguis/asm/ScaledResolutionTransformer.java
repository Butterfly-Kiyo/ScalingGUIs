package spazley.scalingguis.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.ArrayList;
import java.util.List;

/**
 * Rewrites {@code net.minecraft.client.gui.ScaledResolution}'s constructor so that the computed
 * width/height/scale fields can be replaced with fractional values coming from {@link ScaleHook}.
 * <p>
 * The patch is deliberately defensive:
 * <ul>
 * <li>member names are never looked up by name (they differ between MCP (dev) and SRG (production)
 *     namings, and would also change if MCP mappings were updated), instead the fields are identified by
 *     the order in which the constructor writes them, which is fixed by the vanilla source;</li>
 * <li>only stack-neutral code with no jumps is appended right before the constructor's {@code RETURN},
 *     so no stack map frames have to be recomputed ({@code ClassWriter.COMPUTE_MAXS} only, which never
 *     needs to load classes);</li>
 * <li>any unexpected shape makes the transformer return the class untouched, so a broken patch can never
 *     prevent the game from starting.</li>
 * </ul>
 */
public class ScaledResolutionTransformer implements IClassTransformer
{
    private static final String TARGET_CLASS = "net.minecraft.client.gui.ScaledResolution";
    private static final String TARGET_DESC = "(Lnet/minecraft/client/Minecraft;)V";
    private static final String HOOK_OWNER = "spazley/scalingguis/asm/ScaleHook";
    private static final String HOOK_NAME = "resolveScaledResolution";
    private static final String HOOK_DESC = "(DDI)[D";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass)
    {
        if (basicClass == null) {
            return null;
        }
        if (!TARGET_CLASS.equals(transformedName) && !TARGET_CLASS.equals(name)) {
            return basicClass;
        }

        try {
            byte[] patched = patch(basicClass);
            if (patched != null) {
                log("Patched " + TARGET_CLASS + " for fractional GUI scaling.");
                return patched;
            }
            log("WARNING: " + TARGET_CLASS + " did not have the expected shape; fractional GUI scaling is disabled.");
        } catch (Throwable t) {
            log("WARNING: failed to patch " + TARGET_CLASS + ", fractional GUI scaling is disabled: " + t);
        }

        return basicClass;
    }

    private static byte[] patch(byte[] basicClass)
    {
        ClassReader reader = new ClassReader(basicClass);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        Patcher patcher = new Patcher(writer, reader.getClassName());
        reader.accept(patcher, 0);
        return patcher.patched ? writer.toByteArray() : null;
    }

    private static void log(String message)
    {
        System.out.println("[ScalingGUIs] " + message);
    }

    private static final class Patcher extends ClassVisitor
    {
        private final String owner;
        private boolean visitedConstructor;
        boolean patched;

        Patcher(ClassVisitor cv, String owner)
        {
            super(Opcodes.ASM5, cv);
            this.owner = owner;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions)
        {
            MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);

            if (visitedConstructor || !"<init>".equals(name) || !TARGET_DESC.equals(desc)) {
                return mv;
            }

            visitedConstructor = true;
            return new ConstructorVisitor(mv, owner, this);
        }
    }

    private static final class ConstructorVisitor extends MethodVisitor
    {
        /** Local variable slot used to hold the double[] returned by the hook. Vanilla uses 0..2 only. */
        private static final int RESULT_SLOT = 5;

        private final String owner;
        private final Patcher parent;

        private final List<String> intFields = new ArrayList<>(3);
        private final List<String> doubleFields = new ArrayList<>(2);

        private boolean injected;

        ConstructorVisitor(MethodVisitor mv, String owner, Patcher parent)
        {
            super(Opcodes.ASM5, mv);
            this.owner = owner;
            this.parent = parent;
        }

        @Override
        public void visitFieldInsn(int opcode, String fieldOwner, String fieldName, String fieldDesc)
        {
            if (opcode == Opcodes.PUTFIELD) {
                // Vanilla writes: scaledWidth (I), scaledHeight (I), scaleFactor (I),
                // scaledWidthD (D), scaledHeightD (D), scaledWidth (I), scaledHeight (I)
                if ("I".equals(fieldDesc)) {
                    if (intFields.size() < 3 && !intFields.contains(fieldName)) {
                        intFields.add(fieldName);
                    }
                } else if ("D".equals(fieldDesc)) {
                    if (doubleFields.size() < 2 && !doubleFields.contains(fieldName)) {
                        doubleFields.add(fieldName);
                    }
                }
            }

            super.visitFieldInsn(opcode, fieldOwner, fieldName, fieldDesc);
        }

        @Override
        public void visitInsn(int opcode)
        {
            if (opcode == Opcodes.RETURN && !injected) {
                injected = true;
                if (inject()) {
                    parent.patched = true;
                }
            }

            super.visitInsn(opcode);
        }

        private boolean inject()
        {
            if (intFields.size() < 3 || doubleFields.size() < 2) {
                log("WARNING: unexpected field write order in " + owner + ".<init>, not patching.");
                return false;
            }

            String scaledWidth = intFields.get(0);
            String scaledHeight = intFields.get(1);
            String scaleFactor = intFields.get(2);
            String scaledWidthD = doubleFields.get(0);
            String scaledHeightD = doubleFields.get(1);

            // double[] result = ScaleHook.resolveScaledResolution(this.scaledWidthD, this.scaledHeightD, this.scaleFactor);
            visitVarInsn(Opcodes.ALOAD, 0);
            visitFieldInsn(Opcodes.GETFIELD, owner, scaledWidthD, "D");
            visitVarInsn(Opcodes.ALOAD, 0);
            visitFieldInsn(Opcodes.GETFIELD, owner, scaledHeightD, "D");
            visitVarInsn(Opcodes.ALOAD, 0);
            visitFieldInsn(Opcodes.GETFIELD, owner, scaleFactor, "I");
            visitMethodInsn(Opcodes.INVOKESTATIC, HOOK_OWNER, HOOK_NAME, HOOK_DESC, false);
            visitVarInsn(Opcodes.ASTORE, RESULT_SLOT);

            putDoubleField(scaledWidthD, 0);
            putDoubleField(scaledHeightD, 1);
            putIntField(scaledWidth, 2);
            putIntField(scaledHeight, 3);
            putIntField(scaleFactor, 4);

            return true;
        }

        private void pushIndex(int index)
        {
            // All indices used here are in 0..5, so ICONST_n is always available.
            visitInsn(Opcodes.ICONST_0 + index);
        }

        private void putDoubleField(String fieldName, int index)
        {
            // this.<field> = result[index];
            visitVarInsn(Opcodes.ALOAD, 0);
            visitVarInsn(Opcodes.ALOAD, RESULT_SLOT);
            pushIndex(index);
            visitInsn(Opcodes.DALOAD);
            visitFieldInsn(Opcodes.PUTFIELD, owner, fieldName, "D");
        }

        private void putIntField(String fieldName, int index)
        {
            // this.<field> = (int) result[index];
            visitVarInsn(Opcodes.ALOAD, 0);
            visitVarInsn(Opcodes.ALOAD, RESULT_SLOT);
            pushIndex(index);
            visitInsn(Opcodes.DALOAD);
            visitInsn(Opcodes.D2I);
            visitFieldInsn(Opcodes.PUTFIELD, owner, fieldName, "I");
        }
    }
}
