package gin.test;

import gin.Patch;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

public class AndroidUnitTestResultSet {

    private List<AndroidTestResult> results;
    private Patch patch;
    private boolean patchValid;


    public AndroidUnitTestResultSet(Patch patch, boolean patchValid, List<AndroidTestResult> results){
        this.patch = patch;
        this.results = results;
        this.patchValid = patchValid;

    }
    public boolean isPatchValid() {
        return patchValid;
    }

    public Double getExecutionTime(){
        ArrayList<Double> execTimes = new ArrayList<>();
        for(AndroidTestResult result: results){
            execTimes.add(result.getCPUTime());
        }
        return Double.valueOf(median(execTimes));
    }

    public List<AndroidTestResult> getResults() {
        return results;
    }



    public static Double median(ArrayList<Double> values) {
        Collections.sort(values);
        if (values.size() == 0){return 0d;}
        if (values.size() % 2 == 1)
            return values.get((values.size() + 1) / 2 - 1);
        else {
            double lower = values.get(values.size() / 2 - 1);
            double upper = values.get(values.size() / 2);

            return (lower + upper) / 2.0f;
        }
    }
}
