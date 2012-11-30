/*
 * dex2jar - Tools to work with android .dex and java .class files
 * Copyright (c) 2009-2012 Panxiaobo
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.googlecode.dex2jar.tools;

import java.io.File;
import java.io.PrintWriter;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;

public abstract class BaseCmd {
    @Retention(value = RetentionPolicy.RUNTIME)
    @Target(value = { ElementType.FIELD })
    static protected @interface Opt {
        String argName() default "";

        String description();

        boolean hasArg() default true;

        String longOpt() default "";

        String opt();

        boolean required() default false;
    }

    @Retention(value = RetentionPolicy.RUNTIME)
    @Target(value = { ElementType.TYPE })
    static protected @interface Syntax {

        String cmd();

        String desc() default "";

        String onlineHelp() default "";

        String syntax() default "";
    }

    private String cmdLineSyntax;
    private String cmdName;
    protected CommandLine commandLine;
    private String desc;
    @Opt(opt = "h", longOpt = "help", hasArg = false, description = "Print this help message")
    private boolean printHelp = false;
    private String onlineHelp;
    private Map<String, Field> map = new HashMap<String, Field>();
    protected final Options options = new Options();

    protected String remainingArgs[];

    public BaseCmd() {
    }

    public BaseCmd(String cmdLineSyntax, String header) {
        super();

        int i = cmdLineSyntax.indexOf(' ');
        if (i > 0) {
            this.cmdName = cmdLineSyntax.substring(0, i);
            this.cmdLineSyntax = cmdLineSyntax.substring(i + 1);
        }
        this.desc = header;
    }

    public BaseCmd(String cmdName, String cmdSyntax, String header) {
        super();

        this.cmdName = cmdName;
        this.cmdLineSyntax = cmdSyntax;
        this.desc = header;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    protected Object convert(String value, Class type) {
        if (type.equals(String.class)) {
            return value;
        }
        if (type.equals(int.class) || type.equals(Integer.class)) {
            return Integer.parseInt(value);
        }
        if (type.equals(long.class) || type.equals(Long.class)) {
            return Long.parseLong(value);
        }
        if (type.equals(float.class) || type.equals(Float.class)) {
            return Float.parseFloat(value);
        }
        if (type.equals(double.class) || type.equals(Double.class)) {
            return Double.parseDouble(value);
        }
        if (type.equals(boolean.class) || type.equals(Boolean.class)) {
            return Boolean.parseBoolean(value);
        }
        if (type.equals(File.class)) {
            return new File(value);
        }
        try {
            type.asSubclass(Enum.class);
            return Enum.valueOf(type, value);
        } catch (Exception e) {
        }

        throw new RuntimeException("can't convert [" + value + "] to type " + type);
    }

    protected abstract void doCommandLine() throws Exception;

    public void doMain(String... args) {
        initOptions();
        CommandLineParser parser = new PosixParser();
        for (String s : args) {
            if (s.equals("-h") || s.equals("--help")) {
                usage();
                return;
            }
        }
        try {
            commandLine = parser.parse(options, args);
        } catch (ParseException ex) {
            System.err.println("ERROR: " + ex.getMessage() + ", --help for detail help");
            return;
        }
        this.remainingArgs = commandLine.getArgs();
        try {
            for (Option option : commandLine.getOptions()) {
                String opt = option.getOpt();
                Field f = map.get(opt);
                if (f != null) {
                    Object value;
                    if (!option.hasArg()) {// no arg, it's a flag option
                        value = true;
                    } else {
                        value = convert(commandLine.getOptionValue(opt), f.getType());
                    }
                    f.set(this, value);
                }
            }
            if (this.printHelp) {
                usage();
            } else {
                doCommandLine();
            }
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }

    }

    protected String getVersionString() {
        return getClass().getPackage().getImplementationVersion();
    }

    protected void initOptionFromClass(Class<?> clz) {
        if (clz == null) {
            return;
        } else {
            initOptionFromClass(clz.getSuperclass());
        }

        Syntax syntax = clz.getAnnotation(Syntax.class);
        if (syntax != null) {
            this.cmdLineSyntax = syntax.syntax();
            this.cmdName = syntax.cmd();
            this.desc = syntax.desc();
            this.onlineHelp = syntax.onlineHelp();
        }

        Field[] fs = clz.getDeclaredFields();
        for (Field f : fs) {
            Opt opt = f.getAnnotation(Opt.class);
            if (opt != null) {
                f.setAccessible(true);
                if (!opt.hasArg()) {
                    Class<?> type = f.getType();
                    if (!type.equals(boolean.class)) {
                        throw new RuntimeException("the type of " + f
                                + " must be boolean, as it is declared as no args");
                    }
                    boolean b;
                    try {
                        b = (Boolean) f.get(this);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                    if (b) {
                        throw new RuntimeException("the value of " + f + " must be false, as it is declared as no args");
                    }
                }
                Option option = new Option(opt.opt(), opt.hasArg(), opt.description());
                option.setRequired(opt.required());
                if (!"".equals(opt.longOpt())) {
                    option.setLongOpt(opt.longOpt());
                }
                if (!"".equals(opt.argName())) {
                    option.setArgName(opt.argName());
                }
                options.addOption(option);
                map.put(opt.opt(), f);
            }
        }
    }

    protected void initOptions() {
        initOptionFromClass(this.getClass());
    }

    protected void usage() {
        HelpFormatter formatter = new HelpFormatter() {

            @Override
            public void printHelp(PrintWriter pw, int width, String cmdLineSyntax, String header, Options options,
                    int leftPad, int descPad, String footer, boolean autoUsage) {
                String xHeader = BaseCmd.this.desc;
                if (xHeader != null && !xHeader.equals("")) {
                    printWrapped(pw, width, xHeader);
                }
                super.printHelp(pw, width, cmdLineSyntax, header, options, leftPad, descPad, footer, autoUsage);
            }
        };
        String footer = "version: " + getVersionString();
        if (onlineHelp != null && !"".equals(onlineHelp)) {
            footer = footer + "\nonline help:\n" + onlineHelp;
        }
        formatter.printHelp(this.cmdName + " " + cmdLineSyntax, "options:", options, footer);
    }
}
