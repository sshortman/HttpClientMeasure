package com.santaba.agent.http;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created with Intellij IDEA.
 * User: Robin
 * Date: 7/23/15
 */
public class Metrics {

    public final static String STEP_1 = "step-1"; //initialize connection
    public final static String STEP_2 = "step-2"; //open connection, contain step-3 & step-4
    public final static String STEP_3 = "step-3"; //create ssl socket
    public final static String STEP_4 = "step-4"; //start handshake
    public final static String STEP_5 = "step-5"; //send the request and wait for response
    public final static String STEP_6 = "step-6"; //process response
    public final static String STEP_ALL = "step-all"; //all response time
    public final static String STEP_EXECUTE_ALL = "step-execute-all"; //execute request time, include step-1 ~ step-6

    private Metrics() {
    }

    private static final Metrics _INST = new Metrics();

    private final static Map<String, Step> _steps = new ConcurrentHashMap<String, Step>();

    public Set<String> keySet() {
        return _steps.keySet();
    }

    public Step getStep(String name) {
        return _steps.get(name);
    }

    public static Metrics getInstance() {
        return _INST;
    }

    public void startStep(String name) {
        Step step = _steps.get(name);
        if (step == null) {
            step = new Step(name);
            _steps.put(name, step);
        }
        step.start();
    }

    public void finishStep(String name) {
        Step step = _steps.get(name);
        step.finish();
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (String stepName : Metrics.getInstance().keySet()) {
            Metrics.Step step = Metrics.getInstance().getStep(stepName);
            sb.append(stepName).append("_ResponseTime=").append(step.responseTime).append("\n")
                    .append(stepName).append("_Count=").append(step.count).append("\n")
                    .append(stepName).append("_MaxTime=").append(step.maxTime).append("\n")
                    .append(stepName).append("_MinTime=").append(step.minTime).append("\n");
        }
        return sb.toString();
    }


    static class Step {
        final String name;

        long startEpoch;
        long responseTime = 0;
        int count;
        long maxTime = 0;
        long minTime = 0;

        Step(String name) {
            this.name = name;
        }

        void start() {
            startEpoch = System.currentTimeMillis();
        }
        void finish() {
            long tmp = System.currentTimeMillis() - startEpoch;
            responseTime += tmp;
            if (tmp > maxTime) {
                maxTime = tmp;
            }
            if (tmp < minTime) {
                minTime = tmp;
            }
            count++;
        }
    }
}
