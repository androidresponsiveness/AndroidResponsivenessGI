# Reproduction Repository for the paper: Optimising Responsiveness of Android apps via Genetic Improvement

## Data

The Data collected during our experiments and analysis, is available in the Data dirctory

## Tool

This is a version of the GIN tool (https://github.com/gintool/gin) reporpoused to run on Android Application

### Installing and Buildin

Ensure you have java-11 installed.
Install and open Android Studio with application to get dependencies.
First clone the repo, then build using gradle:

```
gradle assemble
```

the build.gradle file can be used to change the build process
This will build the tool, and output the gin.jar file

### Using the tool

In order to use the compiled tool, you need two things:

An application.
Simply make an Apps folder and clone the source code of an application into it. You can then find the part of the app you wish to improve and the test suites you wish to utilise.
Make sure that the app can be built and that the tests will run before attempting to use the tool or it is unlikely to function correctly.

Enter the applications directory and call 
```
./gradlew check 
```
to ensure the application can be tested


A config file
There are a selection of config files for the applications used in our paper in the Configs directory
It is broken down as follows:
```
appName=name of application package

appPath=Relative path to main directory of the app

filePath=Relative path to the file being improved

apkPath=Relative path to the apps main apk*

testApkPath=Relative path to the applications test apk*

testRunner= Name of the test runner*

testAppName= Name of the test application package*

adbPath= Path to the adb executable*

deviceName= Serial number of the device to run on (use adb devices command)*

tests= Name of tests (format: TestClass.TestMethod or TestClass.* for all methods separated by a comma (,))

perfTests= Name of tests on which frame rate will be measured (same format as above)

targetMethods= A list of fully qualified method names, including argument types (seperated by ! not ,)

flavour= flavour of the application being improved (may be left blank)

module= Specify the module of the application  (can be left blank if module = app)
```

*Only needed if measuring the launch time of patches on a device

Finally run 
```
java -jar gin.jar
```
to run the tool.

### Important Files

AndroidGI.java:

This is the main file to run GI from, it contains code to run GP and Local Search to improve applications frame rate

AndroidTestRunner.java:
This File executes all tests on the android device and collects the results and properties of test executions.

AndroidProject.Java:
This File parses the android source code, finding testcases and calling gradle commands