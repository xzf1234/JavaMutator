package edu.hust.xzf.mutator;

import edu.hust.xzf.mutator.config.Configuration;

import java.io.File;
import java.util.ArrayList;

public class Main {
    public static void main(String[] args) {
        Configuration config = null;
        if (args.length == 6) {
            String projectPath = args[0]; // source code root path. e.g. /home/kui/Desktop/buggyProject
            String srcPrefix = args[1]; // src prefix. e.g. src/main/java/
            String binPrefix = args[2]; // bin prefix. e.g. target/classes/
            String classPath = args[3]; // target file. e.g. a.b or a.b.c.d$abc
            int lineNumber = Integer.parseInt(args[4]); // line number of target file to be mutated. e.g. 10
            String JDKLevel = args[5]; // JDK level. eg, 8, 11, 17
            config = new Configuration(projectPath, srcPrefix, binPrefix, classPath, lineNumber, JDKLevel, new ArrayList<>());
        } else if (args.length == 4) {
            String projectPath = args[0]; // source code root path. e.g. /home/kui/Desktop/buggyProject
            String classPath = args[1]; // target file. e.g. a.b.c.Main$abc
            int lineNumber = Integer.parseInt(args[2]); // line number of target file to be mutated. e.g. 10
            String JDKLevel = args[3]; // JDK level. eg, 8, 11, 17
            config = new Configuration(projectPath, classPath, lineNumber, JDKLevel, new ArrayList<>());
        }
        Mutator mutator = new Mutator(config);
        mutator.doMutate();
    }
}
