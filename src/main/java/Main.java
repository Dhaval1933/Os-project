import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Main {

    static class Job {
        int id;
        long pid;
        String command;
        String status;
        Process process;

        Job(int id, long pid, String command, String status, Process process) {
            this.id = id;
            this.pid = pid;
            this.command = command;
            this.status = status;
            this.process = process;
        }
    }

    public static void main(String[] args) {

        Scanner scanner = new Scanner(System.in);
        List<Job> activeJobs = new ArrayList<>();

        while (true) {

            System.out.print("$ ");
            System.out.flush();

            String input = scanner.nextLine().trim();

            if (input.isEmpty()) {
                reapJobsBeforePrompt(activeJobs);
                continue;
            }

            if (input.equals("exit")) {
                break;
            }

            List<String> parsedArgs = parseArguments(input);
            if (parsedArgs.isEmpty()) {
                reapJobsBeforePrompt(activeJobs);
                continue;
            }

            boolean isBackground = false;
            if (parsedArgs.get(parsedArgs.size() - 1).equals("&")) {
                isBackground = true;
                parsedArgs.remove(parsedArgs.size() - 1);
            }

            if (parsedArgs.isEmpty()) {
                reapJobsBeforePrompt(activeJobs);
                continue;
            }

            String cmd = parsedArgs.get(0);

            boolean hasPipeline = false;
            for (String arg : parsedArgs) {
                if (arg.equals("|")) {
                    hasPipeline = true;
                    break;
                }
            }

            if (hasPipeline) {
                try {
                    List<String> cmd1Args = new ArrayList<>();
                    List<String> cmd2Args = new ArrayList<>();
                    boolean passedPipe = false;

                    for (String arg : parsedArgs) {
                        if (arg.equals("|")) {
                            passedPipe = true;
                            continue;
                        }
                        if (!passedPipe) {
                            cmd1Args.add(arg);
                        } else {
                            cmd2Args.add(arg);
                        }
                    }

                    java.io.PipedOutputStream pipeOut = new java.io.PipedOutputStream();
                    java.io.PipedInputStream pipeIn = new java.io.PipedInputStream(pipeOut);

                    java.io.PrintStream originalOut = System.out;
                    java.io.InputStream originalIn = System.in;

                    Thread stage1Thread = new Thread(() -> {
                        java.io.PrintStream tempOut = null;
                        try {
                            tempOut = new java.io.PrintStream(pipeOut, true);
                            System.setOut(tempOut);
                            
                            String cmd1 = cmd1Args.get(0);
                            if (isBuiltin(cmd1)) {
                                executeBuiltin(cmd1, cmd1Args, activeJobs);
                            } else {
                                ProcessBuilder pb1 = new ProcessBuilder(cmd1Args);
                                pb1.redirectInput(ProcessBuilder.Redirect.INHERIT);
                                pb1.redirectError(ProcessBuilder.Redirect.INHERIT);
                                Process p1 = pb1.start();
                                
                                java.io.InputStream p1In = p1.getInputStream();
                                byte[] buffer = new byte[4096];
                                int read;
                                while ((read = p1In.read(buffer)) != -1) {
                                    pipeOut.write(buffer, 0, read);
                                    pipeOut.flush();
                                }
                                p1.waitFor();
                            }
                        } catch (Exception e) {
                        } finally {
                            if (tempOut != null) tempOut.close();
                            try { pipeOut.close(); } catch (Exception e) {}
                        }
                    });

                    stage1Thread.start();

                    String cmd2 = cmd2Args.get(0);
                    if (isBuiltin(cmd2)) {
                        System.setIn(pipeIn);
                        executeBuiltin(cmd2, cmd2Args, activeJobs);
                        System.setIn(originalIn);
                        System.setOut(originalOut);
                        stage1Thread.join();
                    } else {
                        ProcessBuilder pb2 = new ProcessBuilder(cmd2Args);
                        pb2.redirectOutput(ProcessBuilder.Redirect.INHERIT);
                        pb2.redirectError(ProcessBuilder.Redirect.INHERIT);
                        Process p2 = pb2.start();

                        java.io.OutputStream p2Out = p2.getOutputStream();
                        
                        Thread feederThread = new Thread(() -> {
                            byte[] buffer = new byte[4096];
                            int read;
                            try {
                                while ((read = pipeIn.read(buffer)) != -1) {
                                    p2.getOutputStream().write(buffer, 0, read);
                                    p2.getOutputStream().flush();
                                }
                            } catch (Exception e) {
                            } finally {
                                try { p2Out.close(); } catch (Exception e) {}
                                try { pipeIn.close(); } catch (Exception e) {}
                            }
                        });
                        feederThread.start();

                        if (isBackground) {
                            int assignedJobId = 1;
                            while (true) {
                                boolean idTaken = false;
                                for (Job job : activeJobs) {
                                    if (job.id == assignedJobId) {
                                        idTaken = true;
                                        break;
                                    }
                                }
                                if (!idTaken) {
                                    break;
                                }
                                assignedJobId++;
                            }
                            System.out.println("[" + assignedJobId + "] " + p2.pid());
                            int insertionIndex = 0;
                            while (insertionIndex < activeJobs.size() && activeJobs.get(insertionIndex).id < assignedJobId) {
                                insertionIndex++;
                            }
                            activeJobs.add(insertionIndex, new Job(assignedJobId, p2.pid(), input, "Running", p2));
                        } else {
                            p2.waitFor();
                            stage1Thread.join();
                            feederThread.join();
                        }
                    }

                } catch (Exception e) {
                    System.err.println("Error executing pipeline");
                }

                if (!cmd.equals("jobs")) {
                    reapJobsBeforePrompt(activeJobs);
                }
                continue;
            }

            String redirectFile = null;
            String redirectErrFile = null;
            boolean appendOut = false;
            boolean appendErr = false;
            int redirectIndex = -1;

            for (int i = parsedArgs.size() - 2; i >= 0; i--) {
                String arg = parsedArgs.get(i);
                if (arg.equals(">") || arg.equals("1>")) {
                    redirectIndex = i;
                    redirectFile = parsedArgs.get(i + 1);
                    appendOut = false;
                    break;
                } else if (arg.equals(">>") || arg.equals("1>>")) {
                    redirectIndex = i;
                    redirectFile = parsedArgs.get(i + 1);
                    appendOut = true;
                    break;
                } else if (arg.equals("2>")) {
                    redirectIndex = i;
                    redirectErrFile = parsedArgs.get(i + 1);
                    appendErr = false;
                    break;
                } else if (arg.equals("2>>")) {
                    redirectIndex = i;
                    redirectErrFile = parsedArgs.get(i + 1);
                    appendErr = true;
                    break;
                }
            }

            if (redirectIndex != -1) {
                parsedArgs.remove(redirectIndex + 1);
                parsedArgs.remove(redirectIndex);
            }

            if (parsedArgs.isEmpty()) {
                if (!cmd.equals("jobs")) {
                    reapJobsBeforePrompt(activeJobs);
                }
                continue;
            }

            java.io.PrintStream originalOut = System.out;
            java.io.PrintStream originalErr = System.err;
            java.io.PrintStream fileOut = null;
            java.io.PrintStream fileErr = null;

            if (redirectFile != null) {
                try {
                    File outFile = new File(redirectFile);
                    if (outFile.getParentFile() != null) {
                        outFile.getParentFile().mkdirs();
                    }
                    fileOut = new java.io.PrintStream(new FileOutputStream(outFile, appendOut));
                    System.setOut(fileOut);
                } catch (Exception e) {
                    System.err.println("Shell error: Cannot write to file " + redirectFile);
                    if (!cmd.equals("jobs")) {
                        reapJobsBeforePrompt(activeJobs);
                    }
                    continue;
                }
            }

            if (redirectErrFile != null) {
                try {
                    File errFile = new File(redirectErrFile);
                    if (errFile.getParentFile() != null) {
                        errFile.getParentFile().mkdirs();
                    }
                    fileErr = new java.io.PrintStream(new FileOutputStream(errFile, appendErr));
                    System.setErr(fileErr);
                } catch (Exception e) {
                    System.err.println("Shell error: Cannot write to file " + redirectErrFile);
                    if (fileOut != null) fileOut.close();
                    System.setOut(originalOut);
                    if (!cmd.equals("jobs")) {
                        reapJobsBeforePrompt(activeJobs);
                    }
                    continue;
                }
            }

            try {
                if (isBuiltin(cmd)) {
                    executeBuiltin(cmd, parsedArgs, activeJobs);
                    continue;
                }

                String pathEnv = System.getenv("PATH");
                String found = null;

                if (pathEnv != null) {
                    String[] paths = pathEnv.split(File.pathSeparator);

                    for (String dir : paths) {
                        File file = new File(dir, cmd);

                        if (file.exists() && file.canExecute()) {
                            found = file.getAbsolutePath();
                            break;
                        }
                    }
                }

                if (found == null) {
                    System.err.println(cmd + ": command not found");
                    continue;
                }

                try {
                    ProcessBuilder pb = new ProcessBuilder(parsedArgs);
                    
                    if (redirectFile != null) {
                        if (appendOut) {
                            pb.redirectOutput(ProcessBuilder.Redirect.appendTo(new File(redirectFile)));
                        } else {
                            pb.redirectOutput(new File(redirectFile));
                        }
                    } else {
                        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
                    }

                    if (redirectErrFile != null) {
                        if (appendErr) {
                            pb.redirectError(ProcessBuilder.Redirect.appendTo(new File(redirectErrFile)));
                        } else {
                            pb.redirectError(new File(redirectErrFile));
                        }
                    } else {
                        pb.redirectError(ProcessBuilder.Redirect.INHERIT);
                    }

                    pb.redirectInput(ProcessBuilder.Redirect.INHERIT);

                    Process process = pb.start();

                    if (isBackground) {
                        int assignedJobId = 1;
                        while (true) {
                            boolean idTaken = false;
                            for (Job job : activeJobs) {
                                if (job.id == assignedJobId) {
                                    idTaken = true;
                                    break;
                                }
                            }
                            if (!idTaken) {
                                break;
                            }
                            assignedJobId++;
                        }

                        long pid = process.pid();
                        originalOut.println("[" + assignedJobId + "] " + pid);
                        String rawCommand = input;
                        
                        int insertionIndex = 0;
                        while (insertionIndex < activeJobs.size() && activeJobs.get(insertionIndex).id < assignedJobId) {
                            insertionIndex++;
                        }
                        activeJobs.add(insertionIndex, new Job(assignedJobId, pid, rawCommand, "Running", process));
                    } else {
                        process.waitFor();
                    }

                } catch (Exception e) {
                    System.err.println("Error executing command");
                }

            } finally {
                if (fileOut != null) {
                    fileOut.close();
                    System.setOut(originalOut);
                }
                if (fileErr != null) {
                    fileErr.close();
                    System.setErr(originalErr);
                }
                System.out.flush();
                System.err.flush();

                if (!cmd.equals("jobs")) {
                    reapJobsBeforePrompt(activeJobs);
                }
            }
        }
    }

    private static boolean isBuiltin(String cmd) {
        return cmd.equals("echo") || cmd.equals("exit") || cmd.equals("type") || cmd.equals("pwd") || cmd.equals("cd") || cmd.equals("jobs");
    }

    private static void executeBuiltin(String cmd, List<String> args, List<Job> activeJobs) {
        if (cmd.equals("jobs")) {
            int size = activeJobs.size();
            for (int i = 0; i < size; i++) {
                Job job = activeJobs.get(i);
                if (!job.process.isAlive()) {
                    job.status = "Done";
                }
            }

            List<Job> toRemove = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                Job job = activeJobs.get(i);
                char marker = ' ';
                if (i == size - 1) {
                    marker = '+';
                } else if (i == size - 2) {
                    marker = '-';
                }

                String displayCmd = job.command;
                if (job.status.equals("Done") && displayCmd.endsWith(" &")) {
                    displayCmd = displayCmd.substring(0, displayCmd.length() - 2);
                }

                System.out.printf("[%d]%c  %-24s%s\n", job.id, marker, job.status, displayCmd);

                if (job.status.equals("Done")) {
                    toRemove.add(job);
                }
            }
            activeJobs.removeAll(toRemove);
        } else if (cmd.equals("pwd")) {
            System.out.println(System.getProperty("user.dir"));
        } else if (cmd.equals("cd")) {
            String path = args.size() > 1 ? args.get(1) : "~";
            File target;

            if (path.equals("~")) {
                String home = System.getenv("HOME");
                target = new File(home);
            } else if (path.startsWith("/")) {
                target = new File(path);
            } else {
                File current = new File(System.getProperty("user.dir"));
                target = new File(current, path);
            }

            try {
                File resolved = new File(target.getCanonicalPath());
                if (resolved.isDirectory()) {
                    System.setProperty("user.dir", resolved.getAbsolutePath());
                } else {
                    System.err.println("cd: " + path + ": No such file or directory");
                }
            } catch (Exception e) {
                System.err.println("cd: " + path + ": No such file or directory");
            }
        } else if (cmd.equals("echo")) {
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i < args.size(); i++) {
                sb.append(args.get(i));
                if (i < args.size() - 1) {
                    sb.append(" ");
                }
            }
            System.out.println(sb.toString());
        } else if (cmd.equals("type")) {
            String targetCmd = args.size() > 1 ? args.get(1) : "";
            if (isBuiltin(targetCmd)) {
                System.out.println(targetCmd + " is a shell builtin");
            } else {
                String pathEnv = System.getenv("PATH");
                String found = null;
                if (pathEnv != null) {
                    String[] paths = pathEnv.split(File.pathSeparator);
                    for (String dir : paths) {
                        File file = new File(dir, targetCmd);
                        if (file.exists() && file.canExecute()) {
                            found = file.getAbsolutePath();
                            break;
                        }
                    }
                }
                if (found != null) {
                    System.out.println(targetCmd + " is " + found);
                } else {
                    System.err.println(targetCmd + ": not found");
                }
            }
        }
    }

    private static void reapJobsBeforePrompt(List<Job> activeJobs) {
        int size = activeJobs.size();
        List<Job> toRemove = new ArrayList<>();

        for (int i = 0; i < size; i++) {
            Job job = activeJobs.get(i);
            char marker = ' ';
            if (i == size - 1) {
                marker = '+';
            } else if (i == size - 2) {
                marker = '-';
            }

            if (!job.process.isAlive() && job.status.equals("Running")) {
                job.status = "Done";
                String displayCmd = job.command;
                if (displayCmd.endsWith(" &")) {
                    displayCmd = displayCmd.substring(0, displayCmd.length() - 2);
                }
                System.out.printf("[%d]%c  %-24s%s\n", job.id, marker, job.status, displayCmd);
                toRemove.add(job);
            }
        }
        activeJobs.removeAll(toRemove);
    }

    private static List<String> parseArguments(String input) {
        List<String> tokens = new ArrayList<>();
        StringBuilder currentToken = new StringBuilder();
        boolean inSingleQuotes = false;
        boolean inDoubleQuotes = false;
        boolean contentAdded = false;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            if (c == '\\' && !inSingleQuotes && !inDoubleQuotes) {
                if (i + 1 < input.length()) {
                    currentToken.append(input.charAt(i + 1));
                    i++; 
                    contentAdded = true;
                } else {
                    currentToken.append(c);
                    contentAdded = true;
                }
            } 
            else if (c == '\\' && inDoubleQuotes) {
                if (i + 1 < input.length()) {
                    char next = input.charAt(i + 1);
                    if (next == '"' || next == '\\' || next == '$' || next == '`') {
                        currentToken.append(next);
                        i++;
                    } else {
                        currentToken.append(c);
                    }
                    contentAdded = true;
                } else {
                    currentToken.append(c);
                    contentAdded = true;
                }
            }
            else if (c == '\'' && !inDoubleQuotes) {
                inSingleQuotes = !inSingleQuotes;
                contentAdded = true;
            } 
            else if (c == '"' && !inSingleQuotes) {
                inDoubleQuotes = !inDoubleQuotes;
                contentAdded = true;
            } 
            else if (inSingleQuotes || inDoubleQuotes) {
                currentToken.append(c);
            } 
            else {
                if (Character.isWhitespace(c)) {
                    if (currentToken.length() > 0 || contentAdded) {
                        tokens.add(currentToken.toString());
                        currentToken.setLength(0);
                        contentAdded = false;
                    }
                } else {
                    currentToken.append(c);
                }
            }
        }

        if (currentToken.length() > 0 || contentAdded) {
            tokens.add(currentToken.toString());
        }

        return tokens;
    }
}