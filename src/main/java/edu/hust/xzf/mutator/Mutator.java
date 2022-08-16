package edu.hust.xzf.mutator;

import edu.hust.xzf.entity.Pair;
import edu.hust.xzf.jdt.tree.ITree;
import edu.hust.xzf.mutator.config.Configuration;
import edu.hust.xzf.mutator.context.ContextReader;
import edu.hust.xzf.mutator.context.Dictionary;
import edu.hust.xzf.mutator.fixpatterns.*;
import edu.hust.xzf.mutator.fixtemplate.FixTemplate;
import edu.hust.xzf.mutator.info.Patch;
import edu.hust.xzf.mutator.utils.*;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Mutator {
    private static Logger log = LoggerFactory.getLogger(Mutator.class);
    protected Dictionary dic = null;
    protected int patchId = 0;
    protected int comparablePatches = 0;

    Configuration config;


    public Mutator(Configuration config) {
        this.config = config;
    }

    public void doMutate() {
        log.info("=======TBar: Start to mutate code======");
        List<CodeNode> scns = parseCode(config);
        List<CodeNode> triedSuspNode = new ArrayList<>();
        for (CodeNode scn : scns) {
            if (triedSuspNode.contains(scn)) continue;
            triedSuspNode.add(scn);
            List<Integer> contextInfoList = readAllNodeTypes(scn.suspCodeAstNode);
            List<Integer> distinctContextInfo = new ArrayList<>();
            for (Integer contInfo : contextInfoList) {
                if (!distinctContextInfo.contains(contInfo) && !Checker.isBlock(contInfo)) {
                    distinctContextInfo.add(contInfo);
                }
            }
//				List<Integer> distinctContextInfo = contextInfoList.stream().distinct().collect(Collectors.toList());

            // Match fix templates for this suspicious code with its context information.
            mutateWithTemplates(scn, distinctContextInfo);
        }

    }

    public void mutateWithTemplates(CodeNode scn, List<Integer> distinctContextInfo) {
        // generate patches with fix templates of TBar.
        FixTemplate ft = null;

        if (!Checker.isMethodDeclaration(scn.suspCodeAstNode.getType())) {
            boolean nullChecked = false;
            boolean typeChanged = false;
            boolean methodChanged = false;
            boolean operator = false;

            for (Integer contextInfo : distinctContextInfo) {
                if (Checker.isCastExpression(contextInfo)) {
                    ft = new ClassCastChecker();

                    if (!typeChanged) {
                        generateAndValidatePatches(ft, scn);
                        typeChanged = true;
                        ft = new DataTypeReplacer();
                    }
                } else if (Checker.isClassInstanceCreation(contextInfo)) {
//					ft = new CNIdiomNoSuperCall();
                    if (!methodChanged) {
//						generateAndValidatePatches(ft, scn);
                        methodChanged = true;
                        ft = new MethodInvocationMutator();
                    }
                } else if (Checker.isIfStatement(contextInfo) || Checker.isDoStatement(contextInfo) || Checker.isWhileStatement(contextInfo)) {
                    if (Checker.isInfixExpression(scn.suspCodeAstNode.getChild(0).getType()) && !operator) {
                        operator = true;
                        ft = new OperatorMutator(0);
                        generateAndValidatePatches(ft, scn);
                    }
                    ft = new ConditionalExpressionMutator(2);
                } else if (Checker.isConditionalExpression(contextInfo)) {
                    ft = new ConditionalExpressionMutator(0);
                } else if (Checker.isCatchClause(contextInfo) || Checker.isVariableDeclarationStatement(contextInfo)) {
                    if (!typeChanged) {
                        ft = new DataTypeReplacer();
                        typeChanged = true;
                    }
                } else if (Checker.isInfixExpression(contextInfo)) {
                    ft = new ICASTIdivCastToDouble();
                    generateAndValidatePatches(ft, scn);

                    if (!operator) {
                        operator = true;
                        ft = new OperatorMutator(0);
                        generateAndValidatePatches(ft, scn);
                    }

                    ft = new ConditionalExpressionMutator(1);
                    generateAndValidatePatches(ft, scn);

                    ft = new OperatorMutator(4);
                } else if (Checker.isBooleanLiteral(contextInfo) || Checker.isNumberLiteral(contextInfo) || Checker.isCharacterLiteral(contextInfo) || Checker.isStringLiteral(contextInfo)) {
                    ft = new LiteralExpressionMutator();
                } else if (Checker.isMethodInvocation(contextInfo) || Checker.isConstructorInvocation(contextInfo) || Checker.isSuperConstructorInvocation(contextInfo)) {
                    if (!methodChanged) {
                        ft = new MethodInvocationMutator();
                        methodChanged = true;
                    }

                    if (Checker.isMethodInvocation(contextInfo)) {
                        if (ft != null) {
                            generateAndValidatePatches(ft, scn);
                        }
                        ft = new NPEqualsShouldHandleNullArgument();
                        generateAndValidatePatches(ft, scn);

                        ft = new RangeChecker(false);
                    }
                } else if (Checker.isAssignment(contextInfo)) {
                    ft = new OperatorMutator(2);
                } else if (Checker.isInstanceofExpression(contextInfo)) {
                    ft = new OperatorMutator(5);
                } else if (Checker.isArrayAccess(contextInfo)) {
                    ft = new RangeChecker(true);
                } else if (Checker.isReturnStatement(contextInfo)) {
                    String returnType = ContextReader.readMethodReturnType(scn.suspCodeAstNode);
                    if ("boolean".equalsIgnoreCase(returnType)) {
                        ft = new ConditionalExpressionMutator(2);
                    } else {
                        ft = new ReturnStatementMutator(returnType);
                    }
                } else if (Checker.isSimpleName(contextInfo) || Checker.isQualifiedName(contextInfo)) {
                    ft = new VariableReplacer();

                    if (!nullChecked) {
                        generateAndValidatePatches(ft, scn);
                        nullChecked = true;
                        ft = new NullPointerChecker();
                    }
                }
                if (ft != null) {
                    generateAndValidatePatches(ft, scn);
                }
                ft = null;
                if (this.patchId >= 10000) break;
            }

            if (!nullChecked) {
                nullChecked = true;
                ft = new NullPointerChecker();
                generateAndValidatePatches(ft, scn);
            }

            ft = new StatementMover();
            generateAndValidatePatches(ft, scn);

            ft = new StatementRemover();
            generateAndValidatePatches(ft, scn);

            ft = new StatementInserter();
            generateAndValidatePatches(ft, scn);
        } else {
            ft = new StatementRemover();
            generateAndValidatePatches(ft, scn);
        }
    }


    protected void generateAndValidatePatches(FixTemplate ft, CodeNode scn) {
        ft.setSuspiciousCodeStr(scn.suspCodeStr);
        ft.setSuspiciousCodeTree(scn.suspCodeAstNode);
        if (scn.javaBackup == null) ft.setSourceCodePath(config.projectPath + config.srcPrefix);
        else ft.setSourceCodePath(config.projectPath + config.srcPrefix, scn.javaBackup);
        ft.setDictionary(dic);
        ft.generatePatches();
        List<Patch> patchCandidates = ft.getPatches();
//		System.out.println(dataType + " ====== " + patchCandidates.size());

        // Test generated patches.
        if (patchCandidates.isEmpty()) return;
        testGeneratedPatches(patchCandidates, scn);
    }

    public List<Integer> readAllNodeTypes(ITree suspCodeAstNode) {
        List<Integer> nodeTypes = new ArrayList<>();
        nodeTypes.add(suspCodeAstNode.getType());
        List<ITree> children = suspCodeAstNode.getChildren();
        for (ITree child : children) {
            int childType = child.getType();
            if (Checker.isFieldDeclaration(childType) ||
                    Checker.isMethodDeclaration(childType) ||
                    Checker.isTypeDeclaration(childType) ||
                    Checker.isStatement(childType)) break;
            nodeTypes.addAll(readAllNodeTypes(child));
        }
        return nodeTypes;
    }

    protected List<Patch> triedPatchCandidates = new ArrayList<>();


    protected void testGeneratedPatches(List<Patch> patchCandidates, CodeNode scn) {
        // Testing generated patches.
        for (Patch patch : patchCandidates) {
            patch.buggyFileName = scn.suspiciousJavaFile;
            addPatchCodeToFile(scn, patch);// Insert the patch.
            if (this.triedPatchCandidates.contains(patch)) continue;
            patchId++;
            if (patchId > 10000) return;
            this.triedPatchCandidates.add(patch);

            String buggyCode = patch.getBuggyCodeStr();
            if ("===StringIndexOutOfBoundsException===".equals(buggyCode)) continue;
            String patchCode = patch.getFixedCodeStr1();
            scn.targetClassFile.delete();

            log.debug("Compiling");
            try {// Compile patched file.
                ShellUtils.shellRun(Arrays.asList("javac -Xlint:unchecked -source 1.8 -target 1.8 -cp "
                        + config.projectPath + "/" + config.srcPrefix + StringUtils.join(config.libPaths, System.getProperty("path.separator"))
                        + " -d " + config.projectPath + "/" + config.binPrefix + " " + scn.targetJavaFile.getAbsolutePath()), config.projectPath, 1);
            } catch (IOException e) {
                log.debug(config.projectPath + " ---Fixer: fix fail because of javac exception! ");
                continue;
            }
            if (!scn.targetClassFile.exists()) { // fail to compile
                log.debug(config.projectPath + " ---Fixer: fix fail because of failed compiling! ");
                continue;
            }
            comparablePatches++;

            log.debug("Testing.");
            try {
                String results = ShellUtils.shellRun(Arrays.asList("java -cp "
                        + config.projectPath + "/" + config.srcPrefix + StringUtils.join(config.libPaths, System.getProperty("path.separator"))
                        + " "+config.classPath), config.projectPath, 2);

                if (results.isEmpty()) {
//					System.err.println(scn.suspiciousJavaFile + "@" + scn.buggyLine);
//					System.err.println("Bug: " + buggyCode);
//					System.err.println("Patch: " + patchCode);
                    continue;
                } else {
                    if (results.contains("java.lang.NoClassDefFoundError")) {
                        log.debug("java.lang.NoClassDefFoundError");
                    }
                }
                log.debug("successfully testing!");
            } catch (IOException e) {
                log.debug(config.projectPath + " ---Fixer: fix fail because of faile passing previously failed test cases! ");
            }
        }
        try {
            scn.targetJavaFile.delete();
            scn.targetClassFile.delete();
            Files.copy(scn.javaBackup.toPath(), scn.targetJavaFile.toPath());
            Files.copy(scn.classBackup.toPath(), scn.targetClassFile.toPath());
        } catch (IOException e1) {
            e1.printStackTrace();
        }
    }

    private void addPatchCodeToFile(CodeNode scn, Patch patch) {
        String javaCode = FileHelper.readFile(scn.javaBackup);

        String fixedCodeStr1 = patch.getFixedCodeStr1();
        String fixedCodeStr2 = patch.getFixedCodeStr2();
        int exactBuggyCodeStartPos = patch.getBuggyCodeStartPos();
        int exactBuggyCodeEndPos = patch.getBuggyCodeEndPos();
        String patchCode = fixedCodeStr1;
        boolean needBuggyCode = false;
        if (exactBuggyCodeEndPos > exactBuggyCodeStartPos) {
            if ("MOVE-BUGGY-STATEMENT".equals(fixedCodeStr2)) {
                // move statement position.
            } else if (exactBuggyCodeStartPos != -1 && exactBuggyCodeStartPos < scn.startPos) {
                // Remove the buggy method declaration.
            } else {
                needBuggyCode = true;
                if (exactBuggyCodeStartPos == 0) {
                    // Insert the missing override method, the buggy node is TypeDeclaration.
                    int pos = scn.suspCodeAstNode.getPos() + scn.suspCodeAstNode.getLength() - 1;
                    for (int i = pos; i >= 0; i--) {
                        if (javaCode.charAt(i) == '}') {
                            exactBuggyCodeStartPos = i;
                            exactBuggyCodeEndPos = i + 1;
                            break;
                        }
                    }
                } else if (exactBuggyCodeStartPos == -1) {
                    // Insert generated patch code before the buggy code.
                    exactBuggyCodeStartPos = scn.startPos;
                    exactBuggyCodeEndPos = scn.endPos;
                } else {
                    // Insert a block-held statement to surround the buggy code
                }
            }
        } else if (exactBuggyCodeStartPos == -1 && exactBuggyCodeEndPos == -1) {
            // Replace the buggy code with the generated patch code.
            exactBuggyCodeStartPos = scn.startPos;
            exactBuggyCodeEndPos = scn.endPos;
        } else if (exactBuggyCodeStartPos == exactBuggyCodeEndPos) {
            // Remove buggy variable declaration statement.
            exactBuggyCodeStartPos = scn.startPos;
        }

        patch.setBuggyCodeStartPos(exactBuggyCodeStartPos);
        patch.setBuggyCodeEndPos(exactBuggyCodeEndPos);
        String buggyCode;
        try {
            buggyCode = javaCode.substring(exactBuggyCodeStartPos, exactBuggyCodeEndPos);
            if (needBuggyCode) {
                patchCode += buggyCode;
                if (fixedCodeStr2 != null) {
                    patchCode += fixedCodeStr2;
                }
            }

            File newFile = new File(scn.targetJavaFile.getAbsolutePath() + ".temp");
            String patchedJavaFile = javaCode.substring(0, exactBuggyCodeStartPos) + patchCode + javaCode.substring(exactBuggyCodeEndPos);
            FileHelper.outputToFile(newFile, patchedJavaFile, false);
            scn.targetJavaFile.delete();
            newFile.renameTo(scn.targetJavaFile);
        } catch (StringIndexOutOfBoundsException e) {
            log.debug(exactBuggyCodeStartPos + " ==> " + exactBuggyCodeEndPos + " : " + javaCode.length());
            e.printStackTrace();
            buggyCode = "===StringIndexOutOfBoundsException===";
        }

        patch.setBuggyCodeStr(buggyCode);
        patch.setFixedCodeStr1(patchCode);
    }


    public List<CodeNode> parseCode(Configuration config) {
        SuspiciousCodeParser scp = new SuspiciousCodeParser();
        log.debug(config.classPath + " === " + config.lineNumber);

        scp.parseSuspiciousCode(new File(config.javaFilePath), config.lineNumber);

        List<Pair<ITree, String>> suspiciousCodePairs = scp.getSuspiciousCode();
        if (suspiciousCodePairs.isEmpty()) {
            log.debug("Failed to identify the statement in: " + config.javaFilePath + " --- " + config.lineNumber);
            return null;
        }

        File targetJavaFile = new File(config.javaFilePath);
        File targetClassFile = new File(config.javaFilePath.replace(".java", ".class"));
        String ClassName = targetJavaFile.getName().replace(".java", "");
        File javaBackup = new File(FileUtils.tempJavaPath(ClassName, config.projectPath));
        File classBackup = new File(FileUtils.tempClassPath(ClassName, config.projectPath));
        try {
            if (!targetClassFile.exists()) return null;
            if (javaBackup.exists()) javaBackup.delete();
            if (classBackup.exists()) classBackup.delete();
            Files.copy(targetJavaFile.toPath(), javaBackup.toPath());
            Files.copy(targetClassFile.toPath(), classBackup.toPath());
        } catch (IOException e) {
            e.printStackTrace();
        }

        List<CodeNode> scns = new ArrayList<>();
        for (Pair<ITree, String> suspCodePair : suspiciousCodePairs) {
            ITree suspCodeAstNode = suspCodePair.getFirst(); //scp.getSuspiciousCodeAstNode();
            String suspCodeStr = suspCodePair.getSecond(); //scp.getSuspiciousCodeStr();
            log.debug("Suspicious Code: \n" + suspCodeStr);

            int startPos = suspCodeAstNode.getPos();
            int endPos = startPos + suspCodeAstNode.getLength();
            CodeNode scn = new CodeNode(javaBackup, classBackup, targetJavaFile, targetClassFile,
                    startPos, endPos, suspCodeAstNode, suspCodeStr, config.javaFilePath, config.lineNumber);
            scns.add(scn);
        }
        return scns;
    }


    class CodeNode {
        public File javaBackup;
        public File classBackup;
        public File targetJavaFile;
        public File targetClassFile;
        public int startPos;
        public int endPos;
        public ITree suspCodeAstNode;
        public String suspCodeStr;
        public String suspiciousJavaFile;
        public int buggyLine;

        public CodeNode(File javaBackup, File classBackup, File targetJavaFile, File targetClassFile, int startPos,
                        int endPos, ITree suspCodeAstNode, String suspCodeStr, String suspiciousJavaFile, int buggyLine) {
            this.javaBackup = javaBackup;
            this.classBackup = classBackup;
            this.targetJavaFile = targetJavaFile;
            this.targetClassFile = targetClassFile;
            this.startPos = startPos;
            this.endPos = endPos;
            this.suspCodeAstNode = suspCodeAstNode;
            this.suspCodeStr = suspCodeStr;
            this.suspiciousJavaFile = suspiciousJavaFile;
            this.buggyLine = buggyLine;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) return false;
            if (obj instanceof CodeNode) {
                CodeNode suspN = (CodeNode) obj;
                if (startPos != suspN.startPos) return false;
                if (endPos != suspN.endPos) return false;
                if (suspiciousJavaFile.equals(suspN.suspiciousJavaFile)) return true;
            }
            return false;
        }
    }
}
