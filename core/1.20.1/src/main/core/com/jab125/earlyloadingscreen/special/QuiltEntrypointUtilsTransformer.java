package com.jab125.earlyloadingscreen.special;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;
import java.util.Arrays;

// Quilt counterpart of FabricLoaderImplTransformer. Quilt Loader has no real FabricLoaderImpl, so patch its entrypoint loop.
public class QuiltEntrypointUtilsTransformer implements ClassFileTransformer {
    public static final String TARGET = "org/quiltmc/loader/impl/entrypoint/EntrypointUtils";

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
        if (!TARGET.equals(className)) return null;
        try {
            ClassReader classReader = new ClassReader(classfileBuffer);
            ClassNode node = new ClassNode(Opcodes.ASM9);
            classReader.accept(node, ClassReader.SKIP_DEBUG + ClassReader.SKIP_FRAMES);

            // invoke0(String key, Class type, Consumer<EntrypointContainer> consumer)
            MethodNode method = node.methods.stream()
                    .filter(a -> "invoke0".equals(a.name))
                    .findFirst().orElseThrow(NullPointerException::new);
            AbstractInsnNode[] insns = method.instructions.toArray();

            {
                InsnList list = new InsnList();
                list.add(new VarInsnNode(Opcodes.ALOAD, 0));
                list.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/jab125/earlyloadingscreen/needed/Hooks", "entrypoint", "(Ljava/lang/String;)V"));
                method.instructions.insert(list);
            }

            {
                // After the containers list is stored in local 5
                AbstractInsnNode store = Arrays.stream(insns).filter(a -> a instanceof VarInsnNode n && n.getOpcode() == Opcodes.ASTORE && n.var == 5).findFirst().orElseThrow();
                InsnList list = new InsnList();
                list.add(new VarInsnNode(Opcodes.ALOAD, 0));
                list.add(new VarInsnNode(Opcodes.ALOAD, 5));
                list.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/jab125/earlyloadingscreen/needed/Hooks", "setup", "(Ljava/lang/String;Ljava/util/Collection;)V"));
                method.instructions.insert(store, list);
            }

            {
                // After the current container is stored in local 7
                AbstractInsnNode store = Arrays.stream(insns).filter(a -> a instanceof VarInsnNode n && n.getOpcode() == Opcodes.ASTORE && n.var == 7).findFirst().orElseThrow();
                InsnList list = new InsnList();
                list.add(new VarInsnNode(Opcodes.ALOAD, 5));
                list.add(new VarInsnNode(Opcodes.ALOAD, 7));
                list.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/jab125/earlyloadingscreen/needed/Hooks", "ranQuilt", "(Ljava/util/Collection;Ljava/lang/Object;)V"));
                method.instructions.insert(store, list);
            }

            {
                // After the entrypoint consumer returns
                AbstractInsnNode accept = Arrays.stream(insns).filter(a -> a instanceof MethodInsnNode n && "accept".equals(n.name) && "java/util/function/Consumer".equals(n.owner)).findFirst().orElseThrow();
                InsnList list = new InsnList();
                list.add(new VarInsnNode(Opcodes.ALOAD, 5));
                list.add(new VarInsnNode(Opcodes.ALOAD, 7));
                list.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/jab125/earlyloadingscreen/needed/Hooks", "ran2Quilt", "(Ljava/util/Collection;Ljava/lang/Object;)V"));
                method.instructions.insert(accept, list);
            }

            for (AbstractInsnNode insn : Arrays.stream(insns).filter(a -> a.getOpcode() == Opcodes.RETURN).toList()) {
                InsnList list = new InsnList();
                list.add(new VarInsnNode(Opcodes.ALOAD, 0));
                list.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/jab125/earlyloadingscreen/needed/Hooks", "entrypointDone", "(Ljava/lang/String;)V"));
                method.instructions.insertBefore(insn, list);
            }

            ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES + ClassWriter.COMPUTE_MAXS);
            node.accept(classWriter);
            return classWriter.toByteArray();
        } catch (Throwable t) {
            // Leave Quilt untouched rather than break its entrypoints
            t.printStackTrace();
            return null;
        }
    }
}
