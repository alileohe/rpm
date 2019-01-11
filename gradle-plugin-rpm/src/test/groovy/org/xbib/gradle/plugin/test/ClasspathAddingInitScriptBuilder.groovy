package org.xbib.gradle.plugin.test

import com.google.common.base.Function
import com.google.common.base.Predicate
import com.google.common.collect.FluentIterable
import org.gradle.internal.ErroringAction
import org.gradle.internal.IoActions
import org.gradle.internal.classloader.ClasspathUtil
import org.gradle.internal.classpath.ClassPath
import org.gradle.util.TextUtil

class ClasspathAddingInitScriptBuilder {

    private ClasspathAddingInitScriptBuilder() {
    }

    static void build(File initScriptFile, final ClassLoader classLoader, Predicate<URL> classpathFilter) {
        build(initScriptFile, getClasspathAsFiles(classLoader, classpathFilter))
    }

    static void build(File initScriptFile, final List<File> classpath) {
        IoActions.writeTextFile(initScriptFile, new ErroringAction<Writer>() {
            @Override
            protected void doExecute(Writer writer) throws Exception {
                writer.write("allprojects {\n")
                writer.write("  buildscript {\n")
                writer.write("    dependencies {\n")
                for (File file : classpath) {
                    writer.write(String.format("      classpath files('%s')\n", TextUtil.escapeString(file.getAbsolutePath())))
                }
                writer.write("    }\n")
                writer.write("  }\n")
                writer.write("}\n")
            }
        })
    }

    static List<File> getClasspathAsFiles(ClassLoader classLoader, Predicate<URL> classpathFilter) {
        List<URL> classpathUrls = getClasspathUrls(classLoader)
        return FluentIterable.from(classpathUrls).filter(classpathFilter).transform(new Function<URL, File>() {
            @Override
            File apply(URL url) {
                return new File(url.toURI())
            }
        }).toList()
    }

    private static List<URL> getClasspathUrls(ClassLoader classLoader) {
        Object cp = ClasspathUtil.getClasspath(classLoader)
        if (cp instanceof List<URL>) {
            return (List<URL>) cp
        }
        if (cp instanceof ClassPath) {
            return ((ClassPath) cp).asURLs
        }
        throw new IllegalStateException("Unable to extract classpath urls from type ${cp.class.canonicalName}")
    }
}