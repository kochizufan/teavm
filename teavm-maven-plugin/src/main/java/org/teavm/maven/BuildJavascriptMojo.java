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
package org.teavm.maven;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import org.apache.commons.io.IOUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.teavm.common.ThreadPoolFiniteExecutor;
import org.teavm.javascript.JavascriptBuilder;
import org.teavm.javascript.JavascriptBuilderFactory;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodReference;
import org.teavm.model.ValueType;
import org.teavm.parsing.ClasspathClassHolderSource;

/**
 *
 * @author Alexey Andreev
 */
@Mojo(name = "build-javascript", requiresDependencyResolution = ResolutionScope.COMPILE,
        requiresDependencyCollection = ResolutionScope.COMPILE)
public class BuildJavascriptMojo extends AbstractMojo {
    private static Set<String> compileScopes = new HashSet<>(Arrays.asList(
            Artifact.SCOPE_COMPILE, Artifact.SCOPE_PROVIDED, Artifact.SCOPE_SYSTEM));

    @Component
    private MavenProject project;

    @Parameter(defaultValue = "${project.build.directory}/javascript")
    private File targetDirectory;

    @Parameter(defaultValue = "${project.build.outputDirectory}")
    private File classFiles;

    private String targetFileName = "classes.js";

    @Parameter
    private boolean minifying = true;

    @Parameter
    private String mainClass;

    @Parameter
    private boolean runtimeSuppressed;

    @Parameter
    private boolean mainPageIncluded;

    @Parameter
    private boolean bytecodeLogging;

    @Parameter(required = false)
    private int numThreads = 1;

    public void setProject(MavenProject project) {
        this.project = project;
    }

    public void setTargetDirectory(File targetDirectory) {
        this.targetDirectory = targetDirectory;
    }

    public void setClassFiles(File classFiles) {
        this.classFiles = classFiles;
    }

    public void setMinifying(boolean minifying) {
        this.minifying = minifying;
    }

    public void setBytecodeLogging(boolean bytecodeLogging) {
        this.bytecodeLogging = bytecodeLogging;
    }

    public void setRuntimeSuppressed(boolean runtimeSuppressed) {
        this.runtimeSuppressed = runtimeSuppressed;
    }

    public void setMainPageIncluded(boolean mainPageIncluded) {
        this.mainPageIncluded = mainPageIncluded;
    }

    public void setNumThreads(int numThreads) {
        this.numThreads = numThreads;
    }

    @Override
    public void execute() throws MojoExecutionException {
        Log log = getLog();
        Runnable finalizer = null;
        try {
            ClassLoader classLoader = prepareClassLoader();
            log.info("Building JavaScript file");
            JavascriptBuilderFactory builderFactory = new JavascriptBuilderFactory();
            builderFactory.setClassLoader(classLoader);
            builderFactory.setClassSource(new ClasspathClassHolderSource(classLoader));
            if (numThreads != 1) {
                int threads = numThreads != 0 ? numThreads : Runtime.getRuntime().availableProcessors();
                final ThreadPoolFiniteExecutor executor = new ThreadPoolFiniteExecutor(threads);
                finalizer = new Runnable() {
                    @Override public void run() {
                        executor.stop();
                    }
                };
                builderFactory.setExecutor(executor);
            }
            JavascriptBuilder builder = builderFactory.create();
            builder.setMinifying(minifying);
            builder.setBytecodeLogging(bytecodeLogging);
            MethodDescriptor mainMethodDesc = new MethodDescriptor("main", ValueType.arrayOf(
                    ValueType.object("java.lang.String")), ValueType.VOID);
            builder.entryPoint("main", new MethodReference(mainClass, mainMethodDesc))
                    .withValue(1, "java.lang.String");
            targetDirectory.mkdirs();
            builder.build(new File(targetDirectory, targetFileName));
            log.info("JavaScript file successfully built");
            if (!runtimeSuppressed) {
                resourceToFile("org/teavm/javascript/runtime.js", "runtime.js");
            }
            if (mainPageIncluded) {
                String text;
                try (Reader reader = new InputStreamReader(classLoader.getResourceAsStream(
                        "org/teavm/maven/main.html"), "UTF-8")) {
                    text = IOUtils.toString(reader).replace("${classes.js}", targetFileName);
                }
                File mainPageFile = new File(targetDirectory, "main.html");
                try (Writer writer = new OutputStreamWriter(new FileOutputStream(mainPageFile), "UTF-8")) {
                    writer.append(text);
                }
            }
        } catch (RuntimeException e) {
            throw new MojoExecutionException("Unexpected error occured", e);
        } catch (IOException e) {
            throw new MojoExecutionException("IO error occured", e);
        } finally {
            if (finalizer != null) {
                finalizer.run();
            }
        }
    }

    private ClassLoader prepareClassLoader() throws MojoExecutionException {
        try {
            Log log = getLog();
            log.info("Preparing classpath for JavaScript generation");
            List<URL> urls = new ArrayList<>();
            StringBuilder classpath = new StringBuilder();
            for (Artifact artifact : project.getArtifacts()) {
                if (!compileScopes.contains(artifact.getScope())) {
                    continue;
                }
                File file = artifact.getFile();
                if (classpath.length() > 0) {
                    classpath.append(':');
                }
                classpath.append(file.getPath());
                urls.add(file.toURI().toURL());
            }
            if (classpath.length() > 0) {
                classpath.append(':');
            }
            classpath.append(classFiles.getPath());
            urls.add(classFiles.toURI().toURL());
            log.info("Using the following classpath for JavaScript generation: " + classpath);
            return new URLClassLoader(urls.toArray(new URL[urls.size()]), BuildJavascriptMojo.class.getClassLoader());
        } catch (MalformedURLException e) {
            throw new MojoExecutionException("Error gathering classpath information", e);
        }
    }

    private void resourceToFile(String resource, String fileName) throws IOException {
        try (InputStream input = BuildJavascriptJUnitMojo.class.getClassLoader().getResourceAsStream(resource)) {
            try (OutputStream output = new FileOutputStream(new File(targetDirectory, fileName))) {
                IOUtils.copy(input, output);
            }
        }
    }
}
