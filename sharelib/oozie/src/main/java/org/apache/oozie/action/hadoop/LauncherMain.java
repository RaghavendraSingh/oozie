/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.oozie.action.hadoop;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Collection;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.util.Shell;
import org.apache.commons.lang.StringUtils;

public abstract class LauncherMain {

    public static final String HADOOP_JOBS = "hadoopJobs";
    public static final String CHILD_MAPREDUCE_JOB_TAGS = "child.mapreduce.job.tags";

    protected static void run(Class<? extends LauncherMain> klass, String[] args) throws Exception {
        LauncherMain main = klass.newInstance();
        main.run(args);
    }

    public static Properties getHadoopJobIds(String logFile, Pattern[] patterns) throws IOException {
        Properties props = new Properties();
        StringBuffer sb = new StringBuffer(100);
        if (!new File(logFile).exists()) {
            System.err.println("Log file: " + logFile + "  not present. Therefore no Hadoop jobids found");
            props.setProperty(HADOOP_JOBS, "");
        }
        else {
            BufferedReader br = new BufferedReader(new FileReader(logFile));
            String line = br.readLine();
            String separator = "";
            while (line != null) {
                for (Pattern pattern : patterns) {
                    Matcher matcher = pattern.matcher(line);
                    if (matcher.find()) {
                        String jobId = matcher.group(1);
                        if (StringUtils.isEmpty(jobId) || jobId.equalsIgnoreCase("NULL")) {
                            continue;
                        }

                        sb.append(separator).append(jobId);
                        separator = ",";
                    }
                }
                line = br.readLine();
            }
            br.close();
            props.setProperty(HADOOP_JOBS, sb.toString());
        }
        return props;
    }

    protected abstract void run(String[] args) throws Exception;

    /**
     * Write to STDOUT (the task log) the Configuration/Properties values. All properties that contain
     * any of the strings in the maskSet will be masked when writting it to STDOUT.
     *
     * @param header message for the beginning of the Configuration/Properties dump.
     * @param maskSet set with substrings of property names to mask.
     * @param conf Configuration/Properties object to dump to STDOUT
     * @throws IOException thrown if an IO error ocurred.
     */
    @SuppressWarnings("unchecked")
    protected static void logMasking(String header, Collection<String> maskSet, Iterable conf) throws IOException {
        StringWriter writer = new StringWriter();
        writer.write(header + "\n");
        writer.write("--------------------\n");
        for (Map.Entry entry : (Iterable<Map.Entry>) conf) {
            String name = (String) entry.getKey();
            String value = (String) entry.getValue();
            for (String mask : maskSet) {
                if (name.contains(mask)) {
                    value = "*MASKED*";
                }
            }
            writer.write(" " + name + " : " + value + "\n");
        }
        writer.write("--------------------\n");
        writer.close();
        System.out.println(writer.toString());
        System.out.flush();
    }

    /**
     * Get file path from the given environment
     */
    protected static String getFilePathFromEnv(String env) {
        String path = System.getenv(env);
        if (path != null && Shell.WINDOWS) {
            // In Windows, file paths are enclosed in \" so remove them here
            // to avoid path errors
            if (path.charAt(0) == '"') {
                path = path.substring(1);
            }
            if (path.charAt(path.length() - 1) == '"') {
                path = path.substring(0, path.length() - 1);
            }
        }
        return path;
    }

    protected static void setYarnTag(Configuration actionConf) {
        if(actionConf.get(CHILD_MAPREDUCE_JOB_TAGS) != null) {
            actionConf.set("mapreduce.job.tags",actionConf.get(CHILD_MAPREDUCE_JOB_TAGS));
        }
    }
}

class LauncherMainException extends Exception {
    private int errorCode;

    public LauncherMainException(int code) {
        errorCode = code;
    }

    int getErrorCode() {
        return errorCode;
    }
}
