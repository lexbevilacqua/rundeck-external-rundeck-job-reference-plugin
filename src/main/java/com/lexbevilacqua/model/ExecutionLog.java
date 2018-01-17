package com.lexbevilacqua.model;

import java.util.Date;

public class ExecutionLog {

    private String log;
    private Date absoluteTime;
    private String level;
    private String time;

    public String getLog() {
        return log;
    }

    public void setLog(String log) {
        this.log = log;
    }

    public Date getAbsoluteTime() {
        return absoluteTime;
    }

    public void setAbsoluteTime(Date absoluteTime) {
        this.absoluteTime = absoluteTime;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }
}
