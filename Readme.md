# Rundeck external rundeck job reference

This plugin allow add a external rundeck job as a "job reference" using the rundeck API (version 14).

## Parameters

 - Remote Rundeck URL
    - URL to which to make the request.
 - Token
    - Token with permission for request
 - Job UUID
    - Job UUID
 - Seconds wait
    - Seconds between checks
 - Arguments
    - Commandline arguments for the script

## Build

 - Require:
    - Java (version 8)
    - Maven (minimum version 3.3.9)

    mvn clean install

## Usage

Install the plugin in your $RDECK_BASE/libext directory:

    mv target/rundeck-external-rundeck-job-reference-plugin.jar $RDECK_BASE/libext

