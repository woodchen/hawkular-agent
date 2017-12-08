/*
 * Copyright 2015-2017 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.hawkular.agent.javaagent.config;

import java.io.File;

// Below is a copy of org.jboss.security.util.StringPropertyReplacer with some minor alterations
// and the addditional feature of supporting environment variables via ${env.name:default}

/*
 * JBoss, Home of Professional Open Source
 * Copyright 2005, JBoss Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

import java.util.Properties;

/**
* A utility class for replacing properties in strings.
*
* @author <a href="mailto:jason@planet57.com">Jason Dillon</a>
* @author <a href="Scott.Stark@jboss.org">Scott Stark</a>
* @author <a href="claudio.vesco@previnet.it">Claudio Vesco</a>
* @author <a href="mailto:adrian@jboss.com">Adrian Brock</a>
* @author <a href="mailto:dimitris@jboss.org">Dimitris Andreadis</a>
* @version <tt>$Revision$</tt>
*/
public final class StringPropertyReplacer {
    /** New line string constant */
    public static final String NEWLINE = System.lineSeparator();

    /** File separator value */
    private static final String FILE_SEPARATOR = File.separator;

    /** Path separator value */
    private static final String PATH_SEPARATOR = File.pathSeparator;

    /** File separator alias */
    private static final String FILE_SEPARATOR_ALIAS = "/";

    /** Path separator alias */
    private static final String PATH_SEPARATOR_ALIAS = ":";

    // States used in property parsing
    private static final int NORMAL = 0;
    private static final int SEEN_DOLLAR = 1;
    private static final int IN_BRACKET = 2;

    /**
     * Go through the input string and replace any occurance of ${p} with
     * the System.getProperty(p) value. If there is no such property p defined,
     * then the ${p} reference will remain unchanged.
     *
     * If the property reference is of the form ${p:v} and there is no such property p,
     * then the default value v will be returned.
     *
     * If the property reference is of the form ${p1,p2} or ${p1,p2:v} then
     * the primary and the secondary properties will be tried in turn, before
     * returning either the unchanged input, or the default value.
     *
     * The property ${/} is replaced with System.getProperty("file.separator")
     * value and the property ${:} is replaced with System.getProperty("path.separator").
     *
     * @param string - the string with possible ${} references
     * @return the input string with all property references replaced if any.
     *    If there are no valid references the input string will be returned.
     */
    public static String replaceProperties(final String string) {
        return replaceProperties(string, null);
    }

    /**
     * Go through the input string and replace any occurance of ${p} with
     * the props.getProperty(p) value. If there is no such property p defined,
     * then the ${p} reference will remain unchanged.
     *
     * If the property reference is of the form ${p:v} and there is no such property p,
     * then the default value v will be returned.
     *
     * If the property reference is of the form ${p1,p2} or ${p1,p2:v} then
     * the primary and the secondary properties will be tried in turn, before
     * returning either the unchanged input, or the default value.
     *
     * The property ${/} is replaced with System.getProperty("file.separator")
     * value and the property ${:} is replaced with System.getProperty("path.separator").
     *
     * @param string - the string with possible ${} references
     * @param props - the source for ${x} property ref values, null means use System.getProperty()
     * @return the input string with all property references replaced if any.
     *    If there are no valid references the input string will be returned.
     */
    public static String replaceProperties(final String string, final Properties props) {
        if (string == null || string.isEmpty()) {
            return string;
        }

        final char[] chars = string.toCharArray();
        StringBuffer buffer = new StringBuffer();
        boolean properties = false;
        int state = NORMAL;
        int start = 0;
        for (int i = 0; i < chars.length; ++i) {
            char c = chars[i];

            // Dollar sign outside brackets
            if (c == '$' && state != IN_BRACKET) {
                state = SEEN_DOLLAR;
            }

            // Open bracket immediatley after dollar
            else if (c == '{' && state == SEEN_DOLLAR) {
                buffer.append(string.substring(start, i - 1));
                state = IN_BRACKET;
                start = i - 1;
            }

            // No open bracket after dollar
            else if (state == SEEN_DOLLAR) {
                state = NORMAL;
            }

            // Closed bracket after open bracket
            else if (c == '}' && state == IN_BRACKET) {
                // No content
                if (start + 2 == i) {
                    buffer.append("${}"); // REVIEW: Correct?
                } else // Collect the system property
                {
                    String value = null;

                    String key = string.substring(start + 2, i);

                    // check for alias
                    if (FILE_SEPARATOR_ALIAS.equals(key)) {
                        value = FILE_SEPARATOR;
                    } else if (PATH_SEPARATOR_ALIAS.equals(key)) {
                        value = PATH_SEPARATOR;
                    } else {
                        // check from the properties
                        value = getReplacementString(key, props);

                        if (value == null) {
                            // Check for a default value ${key:default}
                            int colon = key.indexOf(':');
                            if (colon > 0) {
                                String realKey = key.substring(0, colon);
                                value = getReplacementString(realKey, props);

                                if (value == null) {
                                    // Check for a composite key, "key1,key2"
                                    value = resolveCompositeKey(realKey, props);

                                    // Not a composite key either, use the specified default
                                    if (value == null) {
                                        value = key.substring(colon + 1);
                                    }
                                }
                            } else {
                                // No default, check for a composite key, "key1,key2"
                                value = resolveCompositeKey(key, props);
                            }
                        }
                    }

                    if (value != null) {
                        properties = true;
                        buffer.append(value);
                    } else {
                        buffer.append("${");
                        buffer.append(key);
                        buffer.append('}');
                    }

                }
                start = i + 1;
                state = NORMAL;
            }
        }

        // No properties
        if (properties == false) {
            return string;
        }

        // Collect the trailing characters
        if (start != chars.length) {
            buffer.append(string.substring(start, chars.length));
        }

        // Done
        return buffer.toString();
    }

    /**
     * Try to resolve a "key" from the provided properties by
     * checking if it is actually a "key1,key2", in which case
     * try first "key1", then "key2". If all fails, return null.
     *
     * It also accepts "key1," and ",key2".
     *
     * @param key the key to resolve
     * @param props the properties to use
     * @return the resolved key or null
     */
    private static String resolveCompositeKey(String key, Properties props) {
        String value = null;

        // Look for the comma
        int comma = key.indexOf(',');
        if (comma > -1) {
            // If we have a first part, try resolve it
            if (comma > 0) {
                // Check the first part
                String key1 = key.substring(0, comma);
                value = getReplacementString(key1, props);
            }
            // Check the second part, if there is one and first lookup failed
            if (value == null && comma < key.length() - 1) {
                String key2 = key.substring(comma + 1);
                value = getReplacementString(key2, props);
            }
        }
        // Return whatever we've found or null
        return value;
    }

    private static String getReplacementString(String key, Properties props) {
        String value;

        // If the key starts with "env." the value is obtained from an environment variable or null if not defined.
        // If the key starts with "<set>" the value is "true" if the named system property exists; "false" otherwise.
        // If the key starts with "<set>env." the value is "true" if the named env var exists; "false" otherwise.
        // If the key starts with "<notset>" the value is "false" if the named system property exists; "true" otherwise.
        // If the key starts with "<notset>env." the value is "false" if the named env var exists; "true" otherwise.
        // Otherwise, the value is obtained from a system property, or null if not defined.
        final String envPrefix = "env.";
        final String setPrefix = "<set>";
        final String notsetPrefix = "<notset>";
        if (key.startsWith(envPrefix)) {
            key = key.substring(envPrefix.length());
            value = System.getenv(key);
        } else if (key.startsWith(setPrefix)) {
            if (key.contains(",") || key.contains(":")) {
                throw new IllegalArgumentException(
                        "'<set>' expressions always resolve to a value. "
                                + "Specifying a composite key or a default value is invalid: " + key);
            }
            key = key.substring(setPrefix.length());
            if (key.startsWith(envPrefix)) {
                key = key.substring(envPrefix.length());
                value = Boolean.valueOf(System.getenv(key) != null).toString();
            } else {
                value = Boolean.valueOf(System.getProperty(key) != null).toString();
            }
        } else if (key.startsWith(notsetPrefix)) {
            if (key.contains(",") || key.contains(":")) {
                throw new IllegalArgumentException(
                        "<notset>' expressions always resolve to a value. "
                                + "Specifying a composite key or a default value is invalid: " + key);
            }
            key = key.substring(notsetPrefix.length());
            if (key.startsWith(envPrefix)) {
                key = key.substring(envPrefix.length());
                value = Boolean.valueOf(System.getenv(key) == null).toString();
            } else {
                value = Boolean.valueOf(System.getProperty(key) == null).toString();
            }
        } else {
            if (props != null) {
                value = props.getProperty(key);
            } else {
                value = System.getProperty(key);
            }
        }

        return value;
    }
}
