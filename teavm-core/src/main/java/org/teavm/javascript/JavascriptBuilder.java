/*
 *  Copyright 2013 Alexey Andreev.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.teavm.javascript;

import java.io.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.teavm.codegen.*;
import org.teavm.common.FiniteExecutor;
import org.teavm.dependency.DependencyChecker;
import org.teavm.javascript.ast.ClassNode;
import org.teavm.model.*;
import org.teavm.model.util.*;
import org.teavm.optimization.ClassSetOptimizer;

/**
 *
 * @author Alexey Andreev
 */
public class JavascriptBuilder {
    private ClassHolderSource classSource;
    private DependencyChecker dependencyChecker;
    private FiniteExecutor executor;
    private ClassLoader classLoader;
    private boolean minifying = true;
    private boolean bytecodeLogging;
    private OutputStream logStream = System.out;
    private Map<String, JavascriptEntryPoint> entryPoints = new HashMap<>();
    private Map<String, String> exportedClasses = new HashMap<>();

    JavascriptBuilder(ClassHolderSource classSource, ClassLoader classLoader, FiniteExecutor executor) {
        this.classSource = new JavascriptProcessedClassSource(classSource);
        this.classLoader = classLoader;
        dependencyChecker = new DependencyChecker(this.classSource, classLoader, executor);
        this.executor = executor;
    }

    public boolean isMinifying() {
        return minifying;
    }

    public void setMinifying(boolean minifying) {
        this.minifying = minifying;
    }

    public boolean isBytecodeLogging() {
        return bytecodeLogging;
    }

    public void setBytecodeLogging(boolean bytecodeLogging) {
        this.bytecodeLogging = bytecodeLogging;
    }

    public JavascriptEntryPoint entryPoint(String name, MethodReference ref) {
        if (entryPoints.containsKey(name)) {
            throw new IllegalArgumentException("Entry point with public name `" + name + "' already defined " +
                    "for method " + ref);
        }
        JavascriptEntryPoint entryPoint = new JavascriptEntryPoint(name, ref,
                dependencyChecker.attachMethodGraph(ref));
        entryPoints.put(name, entryPoint);
        return entryPoint;
    }

    public void exportType(String name, String className) {
        if (exportedClasses.containsKey(name)) {
            throw new IllegalArgumentException("Class with public name `" + name + "' already defined for class " +
                    className);
        }
        exportedClasses.put(name, className);
    }

    public ClassHolderSource getClassSource() {
        return classSource;
    }

    public void build(Appendable writer) throws RenderingException {
        AliasProvider aliasProvider = minifying ? new MinifyingAliasProvider() : new DefaultAliasProvider();
        DefaultNamingStrategy naming = new DefaultNamingStrategy(aliasProvider, classSource);
        naming.setMinifying(minifying);
        SourceWriterBuilder builder = new SourceWriterBuilder(naming);
        builder.setMinified(minifying);
        SourceWriter sourceWriter = builder.build(writer);
        dependencyChecker.attachMethodGraph(new MethodReference("java.lang.Class", new MethodDescriptor("createNew",
                ValueType.object("java.lang.Class"))));
        dependencyChecker.attachMethodGraph(new MethodReference("java.lang.String", new MethodDescriptor("<init>",
                ValueType.arrayOf(ValueType.CHARACTER), ValueType.VOID)));
        executor.complete();
        ListableClassHolderSource classSet = dependencyChecker.cutUnachievableClasses();
        Decompiler decompiler = new Decompiler(classSet, classLoader, executor);
        Renderer renderer = new Renderer(sourceWriter, classSet, classLoader);
        ClassSetOptimizer optimizer = new ClassSetOptimizer(executor);
        optimizer.optimizeAll(classSet);
        executor.complete();
        allocateRegisters(classSet);
        executor.complete();
        if (bytecodeLogging) {
            try {
                logBytecode(new PrintWriter(new OutputStreamWriter(logStream, "UTF-8")), classSet);
            } catch (IOException e) {
                // Just don't do anything
            }
        }
        renderer.renderRuntime();
        List<ClassNode> clsNodes = decompiler.decompile(classSet.getClassNames());
        for (ClassNode clsNode : clsNodes) {
            renderer.render(clsNode);
        }
        try {
            for (Map.Entry<String, JavascriptEntryPoint> entry : entryPoints.entrySet()) {
                sourceWriter.append(entry.getKey()).ws().append("=").ws().appendMethodBody(entry.getValue().reference)
                        .append(";").softNewLine();
            }
            for (Map.Entry<String, String> entry : exportedClasses.entrySet()) {
                sourceWriter.append(entry.getKey()).ws().append("=").ws().appendClass(entry.getValue()).append(";")
                        .softNewLine();
            }
        } catch (IOException e) {
            throw new RenderingException("IO Error occured", e);
        }
    }

    private void allocateRegisters(ListableClassHolderSource classes) {
        for (String className : classes.getClassNames()) {
            ClassHolder cls = classes.getClassHolder(className);
            for (final MethodHolder method : cls.getMethods()) {
                if (method.getProgram() != null && method.getProgram().basicBlockCount() > 0) {
                    executor.execute(new Runnable() {
                        @Override public void run() {
                            RegisterAllocator allocator = new RegisterAllocator();
                            Program program = ProgramUtils.copy(method.getProgram());
                            allocator.allocateRegisters(method, program);
                            method.setProgram(program);
                        }
                    });
                }
            }
        }
    }

    private void logBytecode(PrintWriter writer, ListableClassHolderSource classes) {
        for (String className : classes.getClassNames()) {
            ClassHolder classHolder = classes.getClassHolder(className);
            printModifiers(writer, classHolder);
            writer.println("class " + className);
            for (MethodHolder method : classHolder.getMethods()) {
                logMethodBytecode(writer, method);
            }
        }
    }

    private void logMethodBytecode(PrintWriter writer, MethodHolder method) {
        writer.print("    ");
        printModifiers(writer, method);
        writer.print(method.getName() + "(");
        ValueType[] parameterTypes = method.getParameterTypes();
        for (int i = 0; i < parameterTypes.length; ++i) {
            if (i > 0) {
                writer.print(", ");
            }
            printType(writer, parameterTypes[i]);
        }
        writer.println(")");
        Program program = method.getProgram();
        if (program != null && program.basicBlockCount() > 0) {
            ListingBuilder builder = new ListingBuilder();
            writer.print(builder.buildListing(program, "        "));
            writer.print("        Register allocation:");
            for (int i = 0; i < program.variableCount(); ++i) {
                writer.print(i + ":" + program.variableAt(i).getRegister() + " ");
            }
            writer.println();
            writer.println();
            writer.flush();
        } else {
            writer.println();
        }
    }

    private void printType(PrintWriter writer, ValueType type) {
        if (type instanceof ValueType.Object) {
            writer.print(((ValueType.Object)type).getClassName());
        } else if (type instanceof ValueType.Array) {
            printType(writer, ((ValueType.Array)type).getItemType());
            writer.print("[]");
        } else if (type instanceof ValueType.Primitive) {
            switch (((ValueType.Primitive)type).getKind()) {
                case BOOLEAN:
                    writer.print("boolean");
                    break;
                case SHORT:
                    writer.print("short");
                    break;
                case BYTE:
                    writer.print("byte");
                    break;
                case CHARACTER:
                    writer.print("char");
                    break;
                case DOUBLE:
                    writer.print("double");
                    break;
                case FLOAT:
                    writer.print("float");
                    break;
                case INTEGER:
                    writer.print("int");
                    break;
                case LONG:
                    writer.print("long");
                    break;
            }
        }
    }

    private void printModifiers(PrintWriter writer, ElementHolder element) {
        switch (element.getLevel()) {
            case PRIVATE:
                writer.print("private ");
                break;
            case PUBLIC:
                writer.print("public ");
                break;
            case PROTECTED:
                writer.print("protected ");
                break;
            default:
                break;
        }
        Set<ElementModifier> modifiers = element.getModifiers();
        if (modifiers.contains(ElementModifier.ABSTRACT)) {
            writer.print("abstract ");
        }
        if (modifiers.contains(ElementModifier.FINAL)) {
            writer.print("final ");
        }
        if (modifiers.contains(ElementModifier.STATIC)) {
            writer.print("static ");
        }
        if (modifiers.contains(ElementModifier.NATIVE)) {
            writer.print("native ");
        }
    }

    public void build(File file) throws RenderingException {
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), "UTF-8")) {
            build(writer);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("Platform does not support UTF-8", e);
        } catch (IOException e) {
            throw new RenderingException("IO error occured", e);
        }
    }
}
