package gin.test;

import gin.Patch;
import gin.util.*;
import org.apache.commons.io.FileUtils;
import org.gradle.internal.time.Time;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AndroidTestRunner {

    private AndroidDebugBridge adb;
    private  AndroidProject project;
    private List<AndroidTest> tests;
    private String apk;
    private String testapk;
    private String activity;
    private String fileName;
    private String testAppId;
    private String testRunner;
    private String appName;
    public String UID;


    public AndroidTestRunner(AndroidProject project, AndroidConfig config){

        String adbPath = config.getAdbPath();
        String deviceId = config.getDeviceName();
        testAppId = config.getTestAppName();
        adb = new AndroidDebugBridge(deviceId, testAppId, adbPath);
        appName = config.getAppName();
        apk = config.getApkPath();
        fileName = config.getFilePath();
        this.project = project;
        testRunner = config.getTestRunner();
        String[] tokens= config.getTargetMethods().get(0).split("\\.");
        testapk= config.getTestApkPath();
        activity =String.join(".", Arrays.copyOfRange(tokens, 0, tokens.length-1));

    }


    //install app, reset all gfxinfo
    public void setUp(){

    }



    public void installTestAPK() {
        adb.installApp(testapk);
    }


    public AndroidUnitTestResultSet runTestsLocally(Patch patch, int runs, boolean breakOnFail){
        ArrayList<AndroidTestResult> results = new ArrayList<>();
        if(TestRunner.isPatchedSourceSame(patch.getSourceFile().getSource(), patch.apply())) {
            if (!patch.toString().equals("|")) {
                return new AndroidUnitTestResultSet(patch, false, results);
            }
        }
        patch.writePatchedSourceToFile(fileName);
        if (project.buildUnitTests()!= 0){
            return new AndroidUnitTestResultSet(patch, false, results);
        }

        AndroidTestResult result = project.runLocalTests((ArrayList<AndroidTest>) project.unitTests, runs);
        if (! result.isPassed()) {
            results.add(result);
            System.out.println("test Failed");
            return new AndroidUnitTestResultSet(patch, false, results);
        }
        results.add(result);
        patch.undoWrite(fileName);
        return new AndroidUnitTestResultSet(patch, true, results);
    }



    public double timeLaunch(Patch patch) throws IOException, InterruptedException {
        ArrayList<AndroidTestResult> results = new ArrayList<>();
        if (!(patch.getEdits().size() == 0)) {
            patch.writePatchedSourceToFile(fileName);

            System.out.println("compiling app");
            int buildExit = project.buildApp();

            patch.undoWrite(fileName);
            if (buildExit != 0) {
                System.out.println("Failed to build App");
                return Long.MAX_VALUE;
            }
        }

        //System.out.println("Installing App");
        adb.installApp(apk);
        AndroidTestResult res =  runInstrumentedTest(project.instrumentedTests.get(0));
        return res.getCPUTime();
    }


    public static boolean parseResult(String result){
        for(String line : result.split("\n")){
            if (line.startsWith("OK ")){
                return true;
            }
        }
        return false;
    }


    public AndroidTestResult runInstrumentedTest(AndroidTest test){
        //adbMemorySampler.resetStats();
        System.out.println("Running test: " + test);
        String cmd = "am instrument -w  --no_window_animation -e class " + test.getModuleName()
                + "." + test.getFullClassName() + "#" + test.getMethodName() +
                " " + testAppId + "/" + testRunner;
        System.out.println(cmd);
        long startTime = System.currentTimeMillis();
        adb.runShellCommand(cmd, true);
        long endTime = System.currentTimeMillis();
        String out = adb.output;
        boolean passed = parseResult(out);
        System.out.println(passed);

        //TestExecutionMemory executionMemory = adbMemorySampler.getExecutionMemory();
        AndroidTestResult result = new AndroidTestResult(test, 1);
        //result.setExecutionMemory(executionMemory.medianReading().longValue());
        result.setPassed(passed);
        result.setCPUTime(endTime-startTime);
        return result;
    }

    public AndroidTestResult runUnitTest(AndroidTest test){
        int passed = project.runLocaltest(test);
        AndroidTestResult result = new AndroidTestResult(test, 1);
        result.setPassed(passed==0);
        return result;
    }




}
