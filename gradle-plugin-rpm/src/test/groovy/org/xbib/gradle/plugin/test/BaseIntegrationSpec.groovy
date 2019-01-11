package org.xbib.gradle.plugin.test

import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import org.gradle.api.logging.LogLevel
import org.junit.Rule
import org.junit.rules.TestName
import spock.lang.Specification

abstract class BaseIntegrationSpec extends Specification {
    @Rule
    TestName testName = new TestName()
    File projectDir
    protected String moduleName
    protected LogLevel logLevel = LogLevel.LIFECYCLE
    protected List<File> initScripts = []

    private static final LOGGING_LEVEL_ENV_VARIABLE = "TEST_LOGGING_LEVEL"

    def setup() {
        projectDir = new File("build/xbibtest/${this.class.canonicalName}/${testName.methodName.replaceAll(/\W+/, '-')}").absoluteFile
        if (projectDir.exists()) {
            projectDir.deleteDir()
        }
        projectDir.mkdirs()
        moduleName = findModuleName()
    }

    /**
     * Override to alter its value
     * @return
     */
    protected LogLevel getLogLevel() {
        String levelFromEnv = System.getenv(LOGGING_LEVEL_ENV_VARIABLE)
        if(!levelFromEnv) {
            return logLevel
        }
        return LogLevel.valueOf(levelFromEnv.toUpperCase())
    }

    /* Setup */

    protected File directory(String path, File baseDir = getProjectDir()) {
        new File(baseDir, path).with {
            mkdirs()
            it
        }
    }

    protected File file(String path, File baseDir = getProjectDir()) {
        def splitted = path.split('/')
        def directory = splitted.size() > 1 ? directory(splitted[0..-2].join('/'), baseDir) : baseDir
        def file = new File(directory, splitted[-1])
        file.createNewFile()
        file
    }

    @CompileStatic(TypeCheckingMode.SKIP)
    protected File createFile(String path, File baseDir = getProjectDir()) {
        File file = file(path, baseDir)
        if (!file.exists()) {
            assert file.parentFile.mkdirs() || file.parentFile.exists()
            file.createNewFile()
        }
        file
    }

    protected static void checkForDeprecations(String output) {
        def deprecations = output.readLines().findAll {
            it.contains("has been deprecated and is scheduled to be removed in Gradle") ||
                    it.contains("Deprecated Gradle features were used in this build") ||
                    it.contains("has been deprecated. This is scheduled to be removed in Gradle") ||
                    it.contains("This behaviour has been deprecated and is scheduled to be removed in Gradle")
        }
        // temporary for known issue with overwriting task
        // overridden task expected to not be needed in future version
        if (deprecations.size() == 1 && deprecations.first().contains("Creating a custom task named 'dependencyInsight' has been deprecated and is scheduled to be removed in Gradle 5.0.")) {
            return
        }
        if (!System.getProperty("ignoreDeprecations") && !deprecations.isEmpty()) {
            throw new IllegalArgumentException("Deprecation warnings were found (Set the ignoreDeprecations system property during the test to ignore):\n" + deprecations.collect {
                " - $it"
            }.join("\n"))
        }
    }

    protected static void checkForMutableProjectState(String output) {
        def mutableProjectStateWarnings = output.readLines().findAll {
            it.contains("was resolved without accessing the project in a safe manner") ||
                    it.contains("This may happen when a configuration is resolved from a thread not managed by Gradle or from a different project")

        }

        if (!System.getProperty("ignoreMutableProjectStateWarnings") && !mutableProjectStateWarnings.isEmpty()) {
            throw new IllegalArgumentException("Mutable Project State warnings were found (Set the ignoreMutableProjectStateWarnings system property during the test to ignore):\n" + mutableProjectStateWarnings.collect {
                " - $it"
            }.join("\n"))
        }
    }

    protected void writeHelloWorld(String packageDotted, File baseDir = getProjectDir()) {
        def path = 'src/main/java/' + packageDotted.replace('.', '/') + '/HelloWorld.java'
        def javaFile = createFile(path, baseDir)
        javaFile << """\
            package ${packageDotted};
        
            public class HelloWorld {
                public static void main(String[] args) {
                    System.out.println("Hello Integration Test");
                }
            }
            """.stripIndent()
    }

    /**
     * Creates a unit test for testing your plugin.
     * @param failTest true if you want the test to fail, false if the test should pass
     * @param baseDir the directory to begin creation from, defaults to projectDir
     */
    protected void writeUnitTest(boolean failTest, File baseDir = getProjectDir()) {
        writeTest('src/test/java/', 'xbib', failTest, baseDir)
    }

    /**
     *
     * Creates a unit test for testing your plugin.
     * @param srcDir the directory in the project where the source file should be created.
     * @param packageDotted the package for the unit test class, written in dot notation
     * @param failTest true if you want the test to fail, false if the test should pass
     * @param baseDir the directory to begin creation from, defaults to projectDir
     */
    protected void writeTest(String srcDir, String packageDotted, boolean failTest, File baseDir = getProjectDir()) {
        def path = srcDir + packageDotted.replace('.', '/') + '/HelloWorldTest.java'
        def javaFile = createFile(path, baseDir)
        javaFile << """\
            package ${packageDotted};
            import org.junit.Test;
            import static org.junit.Assert.assertFalse;
    
            public class HelloWorldTest {
                @Test public void doesSomething() {
                    assertFalse( $failTest ); 
                }
            }
            """.stripIndent()
    }

    /**
     * Creates a properties file to included as project resource.
     * @param srcDir the directory in the project where the source file should be created.
     * @param fileName to be used for the file, sans extension.  The .properties extension will be added to the name.
     * @param baseDir the directory to begin creation from, defaults to projectDir
     */
    protected void writeResource(String srcDir, String fileName, File baseDir = getProjectDir()) {
        def path = "$srcDir/${fileName}.properties"
        def resourceFile = createFile(path, baseDir)
        resourceFile.text = "firstProperty=foo.bar"
    }

    protected void addResource(String srcDir, String filename, String contents, File baseDir = getProjectDir()) {
        def resourceFile = createFile("${srcDir}/${filename}", baseDir)
        resourceFile.text = contents
    }

    protected String findModuleName() {
        getProjectDir().getName().replaceAll(/_\d+/, '')
    }

    protected List<String> calculateArguments(String... args) {
        List<String> arguments = []
        // Gradle will use these files name from the PWD, instead of the project directory. It's easier to just leave
        // them out and let the default find them, since we're not changing their default names.
        //arguments += '--build-file'
        //arguments += (buildFile.canonicalPath - projectDir.canonicalPath).substring(1)
        //arguments += '--settings-file'
        //arguments += (settingsFile.canonicalPath - projectDir.canonicalPath).substring(1)
        //arguments += '--no-daemon'
        switch (getLogLevel()) {
            case LogLevel.INFO:
                arguments += '--info'
                break
            case LogLevel.DEBUG:
                arguments += '--debug'
                break
        }
        arguments += '--stacktrace'
        arguments += '-Dorg.gradle.warning.mode=all'
        arguments.addAll(args)
        arguments.addAll(initScripts.collect { file -> '-I' + file.absolutePath })
        arguments
    }
}