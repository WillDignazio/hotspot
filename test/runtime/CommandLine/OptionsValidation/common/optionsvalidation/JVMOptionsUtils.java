/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package optionsvalidation;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.function.Predicate;
import jdk.test.lib.OutputAnalyzer;
import jdk.test.lib.Platform;
import jdk.test.lib.ProcessTools;

public class JVMOptionsUtils {

    /* Java option which print options with ranges */
    private static final String PRINT_FLAGS_RANGES = "-XX:+PrintFlagsRanges";

    /* StringBuilder to accumulate failed message */
    private static final StringBuilder finalFailedMessage = new StringBuilder();

    /* Used to start the JVM with the same type as current */
    static String VMType;

    static {
        if (Platform.isServer()) {
            VMType = "-server";
        } else if (Platform.isClient()) {
            VMType = "-client";
        } else if (Platform.isMinimal()) {
            VMType = "-minimal";
        } else if (Platform.isGraal()) {
            VMType = "-graal";
        } else {
            VMType = null;
        }
    }

    /**
     * Add dependency for option depending on it's name. E.g. enable G1 GC for
     * G1 options or add prepend options to not hit constraints.
     *
     * @param option option
     */
    private static void addNameDependency(JVMOption option) {
        String name = option.getName();

        if (name.startsWith("G1")) {
            option.addPrepend("-XX:+UseG1GC");
        }

        if (name.startsWith("CMS")) {
            option.addPrepend("-XX:+UseConcMarkSweepGC");
        }

        switch (name) {
            case "MinHeapFreeRatio":
                option.addPrepend("-XX:MaxHeapFreeRatio=100");
                break;
            case "MaxHeapFreeRatio":
                option.addPrepend("-XX:MinHeapFreeRatio=0");
                break;
            case "MinMetaspaceFreeRatio":
                option.addPrepend("-XX:MaxMetaspaceFreeRatio=100");
                break;
            case "MaxMetaspaceFreeRatio":
                option.addPrepend("-XX:MinMetaspaceFreeRatio=0");
                break;
            case "CMSOldPLABMin":
                option.addPrepend("-XX:CMSOldPLABMax=" + option.getMax());
                break;
            case "CMSOldPLABMax":
                option.addPrepend("-XX:CMSOldPLABMin=" + option.getMin());
                break;
            case "CMSPrecleanNumerator":
                option.addPrepend("-XX:CMSPrecleanDenominator=" + option.getMax());
                break;
            case "CMSPrecleanDenominator":
                option.addPrepend("-XX:CMSPrecleanNumerator=" + ((new Integer(option.getMin())) - 1));
                break;
            case "InitialTenuringThreshold":
                option.addPrepend("-XX:MaxTenuringThreshold=" + option.getMax());
                break;
            default:
                /* Do nothing */
                break;
        }

    }

    /**
     * Add dependency for option depending on it's type. E.g. run the JVM in
     * compilation mode for compiler options.
     *
     * @param option option
     * @param type type of the option
     */
    private static void addTypeDependency(JVMOption option, String type) {
        if (type.contains("C1") || type.contains("C2")) {
            /* Run in compiler mode for compiler flags */
            option.addPrepend("-Xcomp");
        }
    }

    /**
     * Parse JVM Options. Get input from "inputReader". Parse using
     * "-XX:+PrintFlagsRanges" output format.
     *
     * @param inputReader input data for parsing
     * @param withRanges true if needed options with defined ranges inside JVM
     * @param acceptOrigin predicate for option origins. Origins can be
     * "product", "diagnostic" etc. Accept option only if acceptOrigin evaluates
     * to true.
     * @return map from option name to the JVMOption object
     * @throws IOException if an error occurred while reading the data
     */
    private static Map<String, JVMOption> getJVMOptions(Reader inputReader,
            boolean withRanges, Predicate<String> acceptOrigin) throws IOException {
        BufferedReader reader = new BufferedReader(inputReader);
        String type;
        String line;
        String token;
        String name;
        StringTokenizer st;
        JVMOption option;
        Map<String, JVMOption> allOptions = new LinkedHashMap<>();

        // Skip first line
        line = reader.readLine();

        while ((line = reader.readLine()) != null) {
            /*
             * Parse option from following line:
             * <type> <name> [ <min, optional> ... <max, optional> ] {<origin>}
             */
            st = new StringTokenizer(line);

            type = st.nextToken();

            name = st.nextToken();

            option = JVMOption.createVMOption(type, name);

            /* Skip '[' */
            token = st.nextToken();

            /* Read min range or "..." if range is absent */
            token = st.nextToken();

            if (token.equals("...") == false) {
                if (!withRanges) {
                    /*
                     * Option have range, but asked for options without
                     * ranges => skip it
                     */
                    continue;
                }

                /* Mark this option as option which range is defined in VM */
                option.optionWithRange();

                option.setMin(token);

                /* Read "..." and skip it */
                token = st.nextToken();

                /* Get max value */
                token = st.nextToken();
                option.setMax(token);
            } else if (withRanges) {
                /*
                 * Option not have range, but asked for options with
                 * ranges => skip it
                 */
                continue;
            }

            /* Skip ']' */
            token = st.nextToken();

            /* Read origin of the option */
            token = st.nextToken();

            while (st.hasMoreTokens()) {
                token += st.nextToken();
            };
            token = token.substring(1, token.indexOf("}"));

            if (acceptOrigin.test(token)) {
                addTypeDependency(option, token);
                addNameDependency(option);

                allOptions.put(name, option);
            }
        }

        return allOptions;
    }

    static void failedMessage(String optionName, String value, boolean valid, String message) {
        String temp;

        if (valid) {
            temp = "valid";
        } else {
            temp = "invalid";
        }

        failedMessage(String.format("Error processing option %s with %s value '%s'! %s",
                optionName, temp, value, message));
    }

    static void failedMessage(String message) {
        System.err.println("TEST FAILED: " + message);
        finalFailedMessage.append(String.format("(%s)%n", message));
    }

    static void printOutputContent(OutputAnalyzer output) {
        System.err.println(String.format("stdout content[%s]", output.getStdout()));
        System.err.println(String.format("stderr content[%s]%n", output.getStderr()));
    }

    /**
     * Return string with accumulated failure messages
     *
     * @return string with accumulated failure messages
     */
    public static String getMessageWithFailures() {
        return finalFailedMessage.toString();
    }

    /**
     * Run command line tests for options passed in the list
     *
     * @param options list of options to test
     * @return number of failed tests
     * @throws Exception if java process can not be started
     */
    public static int runCommandLineTests(List<? extends JVMOption> options) throws Exception {
        int failed = 0;

        for (JVMOption option : options) {
            failed += option.testCommandLine();
        }

        return failed;
    }

    /**
     * Test passed options using DynamicVMOption isValidValue and isInvalidValue
     * methods. Only tests writeable options.
     *
     * @param options list of options to test
     * @return number of failed tests
     */
    public static int runDynamicTests(List<? extends JVMOption> options) {
        int failed = 0;

        for (JVMOption option : options) {
            failed += option.testDynamic();
        }

        return failed;
    }

    /**
     * Test passed options using Jcmd. Only tests writeable options.
     *
     * @param options list of options to test
     * @return number of failed tests
     */
    public static int runJcmdTests(List<? extends JVMOption> options) {
        int failed = 0;

        for (JVMOption option : options) {
            failed += option.testJcmd();
        }

        return failed;
    }

    /**
     * Test passed option using attach method. Only tests writeable options.
     *
     * @param options list of options to test
     * @return number of failed tests
     * @throws Exception if an error occurred while attaching to the target JVM
     */
    public static int runAttachTests(List<? extends JVMOption> options) throws Exception {
        int failed = 0;

        for (JVMOption option : options) {
            failed += option.testAttach();
        }

        return failed;
    }

    /**
     * Get JVM options as map. Can return options with defined ranges or options
     * without range depending on "withRanges" argument. "acceptOrigin"
     * predicate can be used to filter option origin.
     *
     * @param withRanges true if needed options with defined ranges inside JVM
     * @param acceptOrigin predicate for option origins. Origins can be
     * "product", "diagnostic" etc. Accept option only if acceptOrigin evaluates
     * to true.
     * @param additionalArgs additional arguments to the Java process which ran
     * with "-XX:+PrintFlagsRanges"
     * @return map from option name to the JVMOption object
     * @throws Exception if a new process can not be created or an error
     * occurred while reading the data
     */
    public static Map<String, JVMOption> getOptionsAsMap(boolean withRanges, Predicate<String> acceptOrigin,
            String... additionalArgs) throws Exception {
        Map<String, JVMOption> result;
        Process p;
        List<String> runJava = new ArrayList<>();

        if (additionalArgs.length > 0) {
            runJava.addAll(Arrays.asList(additionalArgs));
        }

        if (VMType != null) {
            runJava.add(VMType);
        }
        runJava.add(PRINT_FLAGS_RANGES);
        runJava.add("-version");

        p = ProcessTools.createJavaProcessBuilder(runJava.toArray(new String[0])).start();

        result = getJVMOptions(new InputStreamReader(p.getInputStream()), withRanges, acceptOrigin);

        p.waitFor();

        return result;
    }

    /**
     * Get JVM options as list. Can return options with defined ranges or
     * options without range depending on "withRanges" argument. "acceptOrigin"
     * predicate can be used to filter option origin.
     *
     * @param withRanges true if needed options with defined ranges inside JVM
     * @param acceptOrigin predicate for option origins. Origins can be
     * "product", "diagnostic" etc. Accept option only if acceptOrigin evaluates
     * to true.
     * @param additionalArgs additional arguments to the Java process which ran
     * with "-XX:+PrintFlagsRanges"
     * @return List of options
     * @throws Exception if a new process can not be created or an error
     * occurred while reading the data
     */
    public static List<JVMOption> getOptions(boolean withRanges, Predicate<String> acceptOrigin,
            String... additionalArgs) throws Exception {
        return new ArrayList<>(getOptionsAsMap(withRanges, acceptOrigin, additionalArgs).values());
    }

    /**
     * Get JVM options with ranges as list. "acceptOrigin" predicate can be used
     * to filter option origin.
     *
     * @param acceptOrigin predicate for option origins. Origins can be
     * "product", "diagnostic" etc. Accept option only if acceptOrigin evaluates
     * to true.
     * @param additionalArgs additional arguments to the Java process which ran
     * with "-XX:+PrintFlagsRanges"
     * @return List of options
     * @throws Exception if a new process can not be created or an error
     * occurred while reading the data
     */
    public static List<JVMOption> getOptionsWithRange(Predicate<String> acceptOrigin, String... additionalArgs) throws Exception {
        return getOptions(true, acceptOrigin, additionalArgs);
    }

    /**
     * Get JVM options with ranges as list.
     *
     * @param additionalArgs additional arguments to the Java process which ran
     * with "-XX:+PrintFlagsRanges"
     * @return list of options
     * @throws Exception if a new process can not be created or an error
     * occurred while reading the data
     */
    public static List<JVMOption> getOptionsWithRange(String... additionalArgs) throws Exception {
        return getOptionsWithRange(origin -> true, additionalArgs);
    }

    /**
     * Get JVM options with range as map. "acceptOrigin" predicate can be used
     * to filter option origin.
     *
     * @param acceptOrigin predicate for option origins. Origins can be
     * "product", "diagnostic" etc. Accept option only if acceptOrigin evaluates
     * to true.
     * @param additionalArgs additional arguments to the Java process which ran
     * with "-XX:+PrintFlagsRanges"
     * @return Map from option name to the JVMOption object
     * @throws Exception if a new process can not be created or an error
     * occurred while reading the data
     */
    public static Map<String, JVMOption> getOptionsWithRangeAsMap(Predicate<String> acceptOrigin, String... additionalArgs) throws Exception {
        return getOptionsAsMap(true, acceptOrigin, additionalArgs);
    }

    /**
     * Get JVM options with range as map
     *
     * @param additionalArgs additional arguments to the Java process which ran
     * with "-XX:+PrintFlagsRanges"
     * @return map from option name to the JVMOption object
     * @throws Exception if a new process can not be created or an error
     * occurred while reading the data
     */
    public static Map<String, JVMOption> getOptionsWithRangeAsMap(String... additionalArgs) throws Exception {
        return getOptionsWithRangeAsMap(origin -> true, additionalArgs);
    }

    /* Simple method to test that java start-up. Used for testing options. */
    public static void main(String[] args) {
        System.out.print("Java start-up!");
    }
}
