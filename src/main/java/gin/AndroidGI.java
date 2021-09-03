package gin;

import com.opencsv.CSVWriter;
import gin.edit.Edit;
import gin.edit.line.*;
import gin.edit.statement.*;
import gin.test.AndroidTestResult;
import gin.test.AndroidTestRunner;
import gin.test.AndroidUnitTestResultSet;
import gin.test.UnitTest;
import gin.util.*;
import org.apache.commons.io.FileUtils;
import org.apache.commons.rng.simple.JDKRandomBridge;
import org.apache.commons.rng.simple.RandomSource;
import org.pmw.tinylog.Logger;
import org.zeroturnaround.exec.ProcessExecutor;

import java.io.*;
import java.lang.reflect.Array;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.TimeoutException;

public class AndroidGI {
    AndroidTestRunner testRunner;
    SourceFile sourceFile;
    List<Class<? extends Edit>> editTypes;
    JDKRandomBridge rng;
    int indNumber = 20;
    int genNumber = 10;
    int NO_RUNS = 5;
    private double tournamentPercentage = 0.2;
    private double mutateProbability = 0.5;
    private File outputFile;
    AndroidProject project;
    CSVWriter outputFileWriter;
    Map<String, AndroidUnitTestResultSet> book;

    public AndroidGI(AndroidTestRunner testRunner, SourceFileTree sourceFile, List<Class<? extends Edit>> editTypes, AndroidProject project) {
        long seed = Instant.now().getEpochSecond();
        this.testRunner = testRunner;
        this.sourceFile = sourceFile;
        this.editTypes = editTypes;
        this.rng = new JDKRandomBridge(RandomSource.MT, seed);
        outputFile = new File("log.txt");
        this.project = project;

    }

    public static void main(String[] args) {

        AndroidConfigReader configReader = new AndroidConfigReader("config.properties");
        AndroidConfig config = configReader.readConfig();
        String fileName = config.getFilePath();
        AndroidProject androidProject = new AndroidProject(config);
        AndroidTestRunner testRunner = new AndroidTestRunner(androidProject, config);
        List<Class<? extends Edit>> editTypes = Edit.parseEditClassesFromString(Edit.EditType.STATEMENT.toString());
        List<String> targetMethod = config.getTargetMethods();
        SourceFileTree sourceFile =  (SourceFileTree) SourceFile.makeSourceFileForEditTypes(editTypes, fileName, targetMethod);
        AndroidGI androidGI = new AndroidGI(testRunner, sourceFile, editTypes, androidProject);

        // Perform local search
        androidGI.localSearch();

        // CPU validation
        //androidGI.testPatches("patches.txt");


        // On device validation
        //testRunner.installTestAPK();
        //androidGI.timeLaunches("patches.txt");
        System.exit(0);
    }

    public void localSearch() {
        Map<String, AndroidUnitTestResultSet> tested = new HashMap();
        writeHeader();
        Patch patch = new Patch(sourceFile);
        // baseline
        AndroidUnitTestResultSet origRes = testPatchLocally(patch, true, 2);
        AndroidUnitTestResultSet bestresults = null;
        double originalTime = origRes.getExecutionTime();
        if (!origRes.isPatchValid()){
            System.out.println("Tests failed on original app");
            System.exit(1);
        }

        //search


        Patch bestPatch = new Patch(this.sourceFile);
        writePatch(bestPatch, origRes, -1);
        tested.put(bestPatch.toString(), origRes);

        double bestTime = originalTime;
        for (int step = 0; step < 200; step++) {

            try {
                //needed for gradle clean up
                if (step % 5 == 0) {
                    project.killDaemon();
                }
                AndroidUnitTestResultSet results;

                //gen candidate
                Patch neighbour = neighbour(bestPatch);
                if (!tested.containsKey(neighbour.toString())) {
                    //test candidate
                    results = testPatchLocally(neighbour, true, 2);

                } else {
                    results = tested.get(neighbour.toString());
                }
                //output
                writePatch(neighbour, results, step);
                if (!results.isPatchValid()) {

                } else if (results.getExecutionTime() < bestTime) {
                    bestPatch = neighbour;
                    bestTime = results.getExecutionTime();
                    bestresults = results;
                }
            } catch (Exception e) {
                bestPatch.undoWrite(sourceFile.getFilename());
                e.printStackTrace();
                System.exit(1);
            }

        }
        System.out.println(bestPatch);
        System.out.println(originalTime);
        System.out.println(bestTime);
        if(bestresults == null){
            bestresults = origRes;
        }
        writePatch(bestPatch, bestresults, -2);

    }

    // get candidate
    Patch neighbour(Patch patch) {

        Patch neighbour = patch.clone();
        neighbour.addRandomEditOfClasses(rng, editTypes);
        return neighbour;

    }


    // Compare two fitness values, newFitness better if result > 0
    protected double compareFitness(double newFitness, double oldFitness) {

        return oldFitness - newFitness;
    }


    // Adds a random edit of the given type with equal probability among allowed types
    protected Patch mutate(Patch oldPatch) {
        Patch patch = oldPatch.clone();
        patch.addRandomEditOfClasses(rng, editTypes);
        return patch;
    }

    // Test pacth with only local tests
    private AndroidUnitTestResultSet testPatchLocally(Patch patch, boolean breakOnFail, int runs) {
        AndroidUnitTestResultSet result = new AndroidUnitTestResultSet(patch, false, new ArrayList<>());
        try {
            result = testRunner.runTestsLocally(patch, runs, breakOnFail);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
        return result;
    }


    public void writeHeader() {
        String entry = "Gen, Ind, Patch, Valid, Fitness, Time\n";
        try {
            FileWriter writer = new FileWriter(outputFile, true);
            writer.write(entry);
            writer.close();

        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }

    }


    public void writePatch(Patch patch, AndroidUnitTestResultSet resultSet, int gen) {
        ZonedDateTime now = ZonedDateTime.now();
        now = now.withZoneSameInstant(ZoneId.of("UTC"));
        String entry =
                gen + ", " + patch.toString() + ", " +
                        Boolean.toString(resultSet.isPatchValid()) + ", " +
                        Double.toString(resultSet.getExecutionTime()) + ", " + now.toString() + "\n";
        try {
            FileWriter writer = new FileWriter(outputFile, true);
            writer.write(entry);
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }




    public void evaluatePatch(String patchString) {
        Patch patch = getPatch(patchString);
        System.out.println(patchString);
        System.out.println(patch);
        try {
            for (int i =0; i< 10; i++) {

                AndroidUnitTestResultSet results = testPatchLocally(patch, false, 5);
                ZonedDateTime now = ZonedDateTime.now();
                FileWriter writer = new FileWriter("Validation.txt", true);
                String line = patchString + " " + results.getExecutionTime() + " " + now;
                writer.write(line);
                writer.write("\n");
                writer.close();
            }
        }
        catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private void getDiff(String patch, int i) {
        Patch origPatch = new Patch(sourceFile);
        String origString = origPatch.apply();
        Patch newPatch = getPatch(patch);
        String newString = newPatch.apply();
        try {
            FileUtils.writeStringToFile(new File("source.original"), origString, Charset.defaultCharset());
        } catch (IOException e) {
            Logger.error("Could not write original source.");
            Logger.trace(e);
            System.exit(-1);
        }


        try {
            FileUtils.writeStringToFile(new File("source.patched"), newString, Charset.defaultCharset());
        } catch (IOException ex) {
            Logger.error("Could not write patched source.");
            Logger.trace(ex);
            System.exit(-1);
        }

        try {
            String output = new ProcessExecutor().command("diff", "source.original", "source.patched", "--side-by-side")
                    .readOutput(true).execute()
                    .outputUTF8();
            String filename = "diffs/" + project.getProjectName() + i;
            try {
                FileUtils.writeStringToFile(new File(filename), patch.toString(), Charset.defaultCharset());
                FileUtils.writeStringToFile(new File(filename), output, Charset.defaultCharset());
            } catch (IOException e) {
                Logger.error("Could not write original source.");
                Logger.trace(e);
                System.exit(-1);
            }
        } catch (IOException ex) {
            Logger.trace(ex);
            System.exit(-1);
        } catch (InterruptedException ex) {
            Logger.trace(ex);
            System.exit(-1);
        } catch (TimeoutException ex) {
            Logger.trace(ex);
            System.exit(-1);
        }


    }

    private Patch getPatch(String patch) {
        Patch origPatch = new Patch(sourceFile);
        if(patch.replaceAll("\\s+","").equals("|")){
            return origPatch;
        }
        String[] edits = patch.split("\\|");
        for (String edit : edits) {
            if (edit.equals("")) {
                continue;
            }
            String[] editTokens = edit.split(" ");
            String cls = editTokens[1];
            edit = edit.substring(1);
            String[] sourceTokens;
            String[] destTokens;
            switch (cls) {
                case "gin.edit.statement.CopyStatement":
                    origPatch.add(CopyStatement.fromString(edit));
                    break;
                case "gin.edit.statement.DeleteStatement":
                    origPatch.add(DeleteStatement.fromString(edit));
                    break;
                case "gin.edit.statement.ReplaceStatement":
                    origPatch.add(ReplaceStatement.fromString(edit));
                    break;
                case "gin.edit.statement.MoveStatement":
                    origPatch.add(MoveStatement.fromString(edit));
                    break;
                case "gin.edit.statement.SwapStatement":
                    origPatch.add(SwapStatement.fromString(edit));
                    break;
            }

        }
        return origPatch;
    }

    public void writeTest(AndroidTest test){
        String entry =
                test.fileName +  ": " + test.toString() + "\n";
        try {
            FileWriter writer = new FileWriter(outputFile, true);
            writer.write(entry);
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    public void testPatches(String patchFile){
        try {
            Scanner scan = new Scanner(new File(patchFile));
            while (scan.hasNextLine()) {
                String patchStr = scan.nextLine();
                evaluatePatch(patchStr);

            }
        } catch (Exception e){
            e.printStackTrace();
            return;
        }
    }


    public void timeLaunch(String patch){
        Patch newPatch = getPatch(patch);
        try {
            double time = testRunner.timeLaunch(newPatch);
            writeLaunch(patch, time);
        } catch (InterruptedException e){
            e.printStackTrace();
            System.exit(2);
        }catch (IOException e){
            e.printStackTrace();
            System.exit(2);
        }

    }
    public void timeLaunches(String patchFile){
        try {
            Scanner scan = new Scanner(new File(patchFile));
            while (scan.hasNextLine()) {
                String patchStr = scan.nextLine();
                for (int i = 0; i<10; i++) {
                    timeLaunch(patchStr);
                }

            }
        } catch (Exception e){
            e.printStackTrace();
            return;
        }
    }

    public void writeLaunch(String patch, double time){
        String entry =
                patch +  "," + time + "\n";
        try {
            FileWriter writer = new FileWriter("launches.csv", true);
            writer.write(entry);
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
