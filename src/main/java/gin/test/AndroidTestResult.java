package gin.test;




public class AndroidTestResult {
    private UnitTest test;
    private int repNumber;




    private double cpuTime;


    private boolean passed;

    public AndroidTestResult(UnitTest test, int rep){
        this.test = test;
        this.repNumber = rep;
        this.cpuTime = Double.MAX_VALUE;
    }

    public boolean isPassed() {
        return passed;
    }


    public void setPassed(boolean passed) {
        this.passed = passed;
    }



    public double getCPUTime() {
        return cpuTime;
    }



    public void setCPUTime(double cpuTime) {
        this.cpuTime = cpuTime;
    }

    public UnitTest getTest(){ return test;}


    @Override
    public String toString(){
        return  "Test result for: " + test + "\n" +
                "Success: " +  passed + "\n" +
                "CPU Time: " + cpuTime + "\n";

    }
}
