package com.lexbevilacqua.services;

import com.lexbevilacqua.model.ExecutionLog;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.Consts;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONArray;
import org.json.JSONObject;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.config.SocketConfig;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.ssl.SSLContextBuilder;

import javax.xml.bind.DatatypeConverter;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.GeneralSecurityException;
import java.io.IOException;
import java.util.*;

public class RundeckService {

    private static final Log log = LogFactory.getLog(RundeckService.class);

    private String rundeckURL;
    private String token;
    private String asUser;
    private final String API_VERSION = "20";
    private boolean ignoreSSL;
    private int timeout;


    public RundeckService(String rundeckURL, String token, String asUser, boolean ignoreSSL, int timeout) {
        this.rundeckURL = rundeckURL;
        this.token = token;
        this.ignoreSSL = ignoreSSL;
        this.timeout = timeout;
        this.asUser = asUser;
    }

    public RundeckService(String rundeckURL, String token, String asUser) {
        this(rundeckURL, token, asUser, true, 30*1000);
    }

    public long executeJob (String jobID) throws GeneralSecurityException, IOException {
        return executeJob(jobID, null);
    }

    public long executeJob (String jobID, String arguments) throws GeneralSecurityException, IOException {

        String url = this.rundeckURL + "/api/" + API_VERSION + "/job/" + jobID + "/executions";


        Map<String, String> form = new HashMap<>();
        if (null != arguments && !"".equalsIgnoreCase(arguments.trim())) {
            form.put("argString",arguments);
        }

        if (null != this.asUser && !"".equalsIgnoreCase(this.asUser)) {
            form.put("asUser",this.asUser);
        }

        JSONObject res = callRundeckJSON(url, "POST", form);

        return res.getLong("id");
    }

    public boolean isRunning(long executionID) throws GeneralSecurityException, IOException {

        String url = this.rundeckURL + "/api/" + API_VERSION + "/execution/" + executionID + "/state";

        JSONObject res = callRundeckJSON(url, "GET");

        return res.getBoolean("completed");
    }

    public String executionState(long executionID) throws GeneralSecurityException, IOException {

        String url = this.rundeckURL + "/api/" + API_VERSION + "/execution/" + executionID + "/output";

        JSONObject res = callRundeckJSON(url, "GET");

        return res.getString("execState");


    }

    public List<ExecutionLog> executionLog(long executionID, Date lastDateVerified) throws GeneralSecurityException, IOException {

        List<ExecutionLog> log  = new ArrayList<>();

        String url = this.rundeckURL + "/api/" + API_VERSION + "/execution/" + executionID + "/output";

        JSONObject res = callRundeckJSON(url, "GET");
        JSONArray arr  = res.getJSONArray("entries");

        for (int i = 0; i < arr.length(); i++) {

            Date absoluteTime = DatatypeConverter.parseDateTime(arr.getJSONObject(i).getString("absolute_time")).getTime();
            if (absoluteTime.compareTo(lastDateVerified) > 0 ) {
                ExecutionLog el = new ExecutionLog();
                el.setAbsoluteTime(absoluteTime);
                el.setLevel(arr.getJSONObject(i).getString("level"));
                el.setTime(arr.getJSONObject(i).getString("time"));
                el.setLog(arr.getJSONObject(i).getString("log"));

                log.add(el);
            }
        }

        return log;
    }

    private HttpClient getHttpClient() throws GeneralSecurityException {
        SocketConfig socketConfig = SocketConfig.custom().setSoKeepAlive(true).build();

        HttpClientBuilder httpClientBuilder = HttpClientBuilder.create();

        httpClientBuilder.setDefaultSocketConfig(socketConfig);
        httpClientBuilder.disableAuthCaching();
        httpClientBuilder.disableAutomaticRetries();

        if(this.ignoreSSL) {
            log.debug("Disabling all SSL certificate verification.");
            SSLContextBuilder sslContextBuilder = new SSLContextBuilder();
            sslContextBuilder.loadTrustMaterial(null, (TrustStrategy) (x509Certificates, s) -> true);

            httpClientBuilder.setSSLHostnameVerifier(new NoopHostnameVerifier());
            httpClientBuilder.setSSLContext(sslContextBuilder.build());
        }

        return httpClientBuilder.build();
    }


    private JSONObject callRundeckJSON(String url, String method) throws  GeneralSecurityException, IOException {
        return callRundeckJSON(url, method, null);
    }

    private JSONObject callRundeckJSON(String url, String method, Map<String,String> params) throws GeneralSecurityException, IOException {

        RequestBuilder request = RequestBuilder.create(method)
                .setUri(url)
                .setConfig(RequestConfig.custom()
                        .setConnectionRequestTimeout(this.timeout)
                        .setConnectTimeout(this.timeout)
                        .setSocketTimeout(this.timeout)
                        .build());

        request.setHeader("Accept","application/json");
        request.setHeader("X-Rundeck-Auth-Token",this.token);

        if (null != params && "POST".equalsIgnoreCase(method)) {
            List<NameValuePair> form = new ArrayList<>();
            for (String key : params.keySet()) {
                form.add( new BasicNameValuePair(key, params.get(key)));
                System.out.println(key + " - " + params.get(key));
            }
            request.setEntity(new UrlEncodedFormEntity(form, Consts.UTF_8));
        }

        log.debug("Creating HTTP " + request.getMethod() + " request to " + request.getUri());

        HttpResponse response = this.getHttpClient().execute(request.build());

        return new JSONObject(convertStreamToString(response.getEntity().getContent()));

    }

    private static String convertStreamToString(InputStream is) {

        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();

        String line;
        try {
            while ((line = reader.readLine()) != null) {
                sb.append(line + "\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                is.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return sb.toString();
    }

    public static void main (String[] args) {

        String rundeckURL = args[0];
        String token = args[1];
        String jobid = args[2];
        String asUser = args[3];

        try {

            RundeckService rd = new RundeckService(rundeckURL,token,asUser);
            long id;
            id = rd.executeJob(jobid);
            System.out.println(id);
            boolean completed = false;
            Date lastDateVerified = new Date(0);
            while (!completed) {
                Thread.sleep (2000);
                completed = rd.isRunning(id);
                List<ExecutionLog> listExecutionLog = rd.executionLog(id,lastDateVerified);
                for (ExecutionLog aListExecutionLog : listExecutionLog) {
                    System.out.println(aListExecutionLog.getTime() + " - [" + aListExecutionLog.getLevel() + "] " + aListExecutionLog.getLog());
                    lastDateVerified = aListExecutionLog.getAbsoluteTime();
                }
            }
            System.out.println("Final state: " + rd.executionState(id));
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}



