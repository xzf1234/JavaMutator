package edu.hust.xzf.mutator.config;

import java.io.File;
import java.util.ArrayList;

public class Configuration {

    public String projectPath;
    public String srcPrefix;

    public String javaFilePath;

    public String binPrefix;

    public String classPath;

    public String JDK_level = "1.8";

    public int lineNumber;
    public ArrayList<String> libPaths = new ArrayList<>();

    public static String outputPath = "OUTPUT/";

    public static final String TEMP_FILES_PATH = ".temp/";

    public static final String TEMP_OUTER_PATH = "outer";
    public static final long SHELL_RUN_TIMEOUT = 300L;
    public static final long TEST_SHELL_RUN_TIMEOUT = 600L;

    public Configuration(String projectPath, String srcPrefix, String binPrefix, String classPath,
                         int lineNumber, String JDK, ArrayList<String> libPaths) {
        this.projectPath = projectPath;
        this.srcPrefix = srcPrefix;
        this.binPrefix = binPrefix;
        this.classPath = classPath;
        this.lineNumber = lineNumber;
        this.libPaths = libPaths;
        int jdk = Integer.parseInt(JDK);
        if (jdk <= 9)
            JDK_level = "1." + jdk;
        else
            JDK_level = JDK;

        if (classPath.contains("$"))
            classPath = classPath.substring(0, classPath.indexOf("$"));
        javaFilePath = projectPath + "/" + srcPrefix + "/" + classPath.replace(".", "/") + ".java";

    }

    public Configuration(String projectPath, String classPath, int lineNumber, String JDK, ArrayList<String> libPaths) {
        this.projectPath = projectPath;
        this.srcPrefix = "";
        this.binPrefix = "";
        this.classPath = classPath;
        this.lineNumber = lineNumber;
        this.libPaths = libPaths;

        if (JDK.equals("8"))
            JDK_level = "1.8";
        else
            JDK_level = JDK;

        if (classPath.contains("$"))
            classPath = classPath.substring(0, classPath.indexOf("$"));
        javaFilePath = projectPath + "/" + srcPrefix + "/" + classPath.replace(".", "/") + ".java";

    }

//    public Configuration(String srcPath, String fileName, String classPath, int lineNumber) {
//        this.srcPath = srcPath;
//        this.fileName = fileName;
//        this.lineNumber = lineNumber;
//        this.classPath = classPath;
//        this.libPaths = new ArrayList<>();
//    }


}
