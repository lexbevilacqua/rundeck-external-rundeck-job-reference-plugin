package com.lexbevilacqua;

import com.lexbevilacqua.model.ExecutionLog;
import com.lexbevilacqua.services.RundeckService;
import com.dtolabs.rundeck.core.common.INodeEntry;
import com.dtolabs.rundeck.core.execution.workflow.steps.FailureReason;
import com.dtolabs.rundeck.core.execution.workflow.steps.node.NodeStepException;
import com.dtolabs.rundeck.core.plugins.Plugin;
import com.dtolabs.rundeck.core.plugins.configuration.Describable;
import com.dtolabs.rundeck.core.plugins.configuration.Description;
import com.dtolabs.rundeck.plugins.PluginLogger;
import com.dtolabs.rundeck.plugins.ServiceNameConstants;
import com.dtolabs.rundeck.plugins.step.NodeStepPlugin;
import com.dtolabs.rundeck.plugins.step.PluginStepContext;
import com.dtolabs.rundeck.plugins.util.DescriptionBuilder;
import com.dtolabs.rundeck.plugins.util.PropertyBuilder;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Plugin(name = ExternalRundeckJobReferencePlugin.SERVICE_PROVIDER_NAME, service = ServiceNameConstants.WorkflowNodeStep)
public class ExternalRundeckJobReferencePlugin implements NodeStepPlugin, Describable {

    static final String SERVICE_PROVIDER_NAME = "ExternalRundeckJobReferencePlugin";

    private enum Reason implements FailureReason {
        JobFail
    }

    private static final Map<String,Integer> LOG_LEVEL;

    static {
        LOG_LEVEL = new HashMap<>();
        LOG_LEVEL.put("ERROR", 0);
        LOG_LEVEL.put("SEVERAL", 0);
        LOG_LEVEL.put("WARNING", 1);
        LOG_LEVEL.put("NORMAL", 2);
    }


    @Override
    public Description getDescription() {
        return DescriptionBuilder.builder()
                .name(SERVICE_PROVIDER_NAME)
                .title("External rundeck job reference")
                .description("References a external Rundeck job")
                .property(PropertyBuilder.builder()
                        .string("rundeckURL")
                        .title("Remote Rundeck URL")
                        .description("URL to which to make the request.")
                        .required(true)
                        .build())
                .property(PropertyBuilder.builder()
                        .string("token")
                        .title("Token")
                        .description("Token with permission for request.")
                        .required(true)
                        .build())
                .property(PropertyBuilder.builder()
                        .string("asUser")
                        .title("Run as")
                        .description("User responsable for execution")
                        .required(false)
                        .build())
                .property(PropertyBuilder.builder()
                        .string("jobID")
                        .title("Job UUID")
                        .description("Job UUID")
                        .required(true)
                        .build())
                .property(PropertyBuilder.builder()
                        .integer("secondsWait")
                        .title("Seconds wait")
                        .description("Seconds between checks")
                        .required(true)
                        .build())
                .property(PropertyBuilder.builder()
                        .string("arguments")
                        .title("Arguments")
                        .description("Enter the commandline arguments for the script")
                        .required(false)
                        .build())
                .build();
    }

    public void executeNodeStep(PluginStepContext pluginStepContext, Map<String, Object> options, INodeEntry ine) throws NodeStepException {

        String rundeckURL = options.containsKey("rundeckURL") ? options.get("rundeckURL").toString() : null;
        String token = options.containsKey("token") ? options.get("token").toString() : null;
        String asUser = options.containsKey("asUser") ? options.get("asUser").toString() : null;
        String jobID = options.containsKey("jobID") ? options.get("jobID").toString() : null;
        int secondsWait = options.containsKey("secondsWait") ? Integer.parseInt(options.get("secondsWait").toString())*1000 : 30000;
        String arguments = options.containsKey("arguments") ? options.get("arguments").toString() : null;

        PluginLogger log = pluginStepContext.getLogger();

        try {

            RundeckService rd = new RundeckService(rundeckURL,token,asUser);
            long id;
            id = rd.executeJob(jobID,arguments);
            log.log(2,"ExecutionID: " + id);
            boolean completed = false;
            Date lastDateVerified = new Date(0);
            while (!completed) {
                Thread.sleep (secondsWait);
                completed = rd.isRunning(id);
                List<ExecutionLog> listExecutionLog = rd.executionLog(id,lastDateVerified);
                for (ExecutionLog aListExecutionLog : listExecutionLog) {
                    log.log(LOG_LEVEL.get(aListExecutionLog.getLevel())!=null?LOG_LEVEL.get(aListExecutionLog.getLevel()):2,aListExecutionLog.getLog());
                    lastDateVerified = aListExecutionLog.getAbsoluteTime();
                }
            }
            Thread.sleep (secondsWait);
            String finalState = rd.executionState(id);

            if ("failed".equalsIgnoreCase(finalState) || "aborted".equalsIgnoreCase(finalState) ) {
                log.log(LOG_LEVEL.get("ERROR"),"Error, job: " + jobID + " - Rundeck: " + rundeckURL + " - execution: " + id);
                throw new NodeStepException("Final job state: " + finalState,Reason.JobFail,"");
            }

        } catch (IOException | GeneralSecurityException | InterruptedException io) {
            throw new NodeStepException(io.getMessage(),Reason.JobFail,"");
        }


    }
}
