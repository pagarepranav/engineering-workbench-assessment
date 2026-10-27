package dev.pranav.workbench.execution;

import dev.pranav.workbench.common.ApiException;
import dev.pranav.workbench.workflow.WorkflowRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Fixed commands and constrained files. Human approval is mandatory: this is NOT an OS sandbox. */
@Component
public class WorkspaceTools {
    private final Path root;
    private final int timeout;
    private static final Set<String> ALLOWED=Set.of("LinkStore.java","CandidateTest.java","Server.java","README.md");
    public record Result(boolean success, String output, long elapsedMillis, String hash) {}
    public WorkspaceTools(@Value("${workbench.storage}") String root,@Value("${workbench.command-timeout-seconds:30}") int timeout) {
        this.root=Path.of(root).toAbsolutePath().normalize(); this.timeout=timeout;
        try { Files.createDirectories(this.root); rejectSymlinks(this.root); } catch(IOException e) { throw new IllegalStateException(e); }
    }
    public void validate(Map<String,String> files) {
        ApiException.require(files!=null && files.keySet().containsAll(Set.of("LinkStore.java","CandidateTest.java","Server.java")) && ALLOWED.containsAll(files.keySet()),400,"Bundle must contain LinkStore.java, CandidateTest.java, Server.java; only README.md is additionally allowed");
        long total=0;
        for(var e:files.entrySet()) { ApiException.require(e.getValue()!=null && !e.getValue().isBlank() && e.getValue().length()<=100000,400,"Each file must contain 1-100000 characters"); total+=e.getValue().length(); }
        ApiException.require(total<=300000,400,"Bundle is too large");
    }
    public void write(String runId,int revision,Map<String,String> files) {
        validate(files); Path dir=directory(runId,revision,"candidate");
        try { for(var e:files.entrySet()) { Path file=dir.resolve(e.getKey()); rejectSymlinks(file); Files.writeString(file,e.getValue(),StandardCharsets.UTF_8,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING,LinkOption.NOFOLLOW_LINKS); } }
        catch(IOException e){ throw new IllegalStateException("Unable to write candidate workspace",e); }
    }
    public Result validateCandidate(String id,int revision,String expectedHash) { return check(directory(id,revision,"candidate"),expectedHash); }
    public String diskHash(String id,int revision) { return hashDirectory(directory(id,revision,"candidate")); }
    public Result release(String id,int revision,String expectedHash) {
        Path candidate=directory(id,revision,"candidate"),release=directory(id,revision,"release");
        ApiException.require(hashDirectory(candidate).equals(expectedHash),409,"Candidate changed since approval");
        try {
            for(String name:ALLOWED) if(Files.exists(candidate.resolve(name))) Files.copy(candidate.resolve(name),release.resolve(name),StandardCopyOption.REPLACE_EXISTING,LinkOption.NOFOLLOW_LINKS);
            return check(release,expectedHash);
        } catch(IOException e) { throw new IllegalStateException("Local release failed",e); }
    }
    /** Called only after the engine rechecks safe-stop and revision state. */
    public void promote(String id,int revision,String hash) {
        Path release=directory(id,revision,"release");
        ApiException.require(hashDirectory(release).equals(hash),409,"Release source changed after smoke validation");
        Path pointer=root.resolve("release-"+id+".txt"),temporary=root.resolve("release-"+id+".next");
        try {rejectSymlinks(pointer);rejectSymlinks(temporary);Files.writeString(temporary,release.toString(),StandardCharsets.UTF_8);Files.move(temporary,pointer,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
        catch(IOException e){throw new IllegalStateException("Unable to promote local release",e);}
    }
    private Result check(Path dir,String expected) {
        ApiException.require(hashDirectory(dir).equals(expected),409,"Files changed since review; new approval required");
        long start=System.nanoTime(); StringBuilder output=new StringBuilder();
        try {
            Path classes=dir.resolve("classes"); Files.createDirectories(classes); rejectSymlinks(classes);
            String javac=executable("javac"),java=executable("java");
            int compile=command(dir,List.of(javac,"-encoding","UTF-8","--release","17","-d",classes.toString(),"LinkStore.java","CandidateTest.java","Server.java"),output);
            boolean ok=compile==0;
            if(ok) ok=command(dir,List.of(java,"-Xmx128m","-cp",classes.toString(),"CandidateTest"),output)==0;
            boolean unchanged=hashDirectory(dir).equals(expected);
            if(!unchanged) output.append("\nFAIL: source files changed during validation");
            return new Result(ok&&unchanged,output.toString(),Duration.ofNanos(System.nanoTime()-start).toMillis(),expected);
        } catch(Exception e) { return new Result(false,output+"\nTool failure: "+e.getClass().getSimpleName()+": "+e.getMessage(),Duration.ofNanos(System.nanoTime()-start).toMillis(),expected); }
    }
    private int command(Path dir,List<String> args,StringBuilder output) throws Exception {
        output.append("COMMAND ").append(String.join(" ",args)).append('\n');
        Path log=dir.resolve("tool-output.log"); rejectSymlinks(log);
        ProcessBuilder builder=new ProcessBuilder(args).directory(dir.toFile()).redirectErrorStream(true).redirectOutput(log.toFile());
        // Do not pass service credentials or user secrets into generated code.
        var env=builder.environment(); String path=env.get("PATH"),systemRoot=env.get("SystemRoot"),temp=env.get("TEMP"); env.clear();
        if(path!=null)env.put("PATH",path); if(systemRoot!=null)env.put("SystemRoot",systemRoot); if(temp!=null)env.put("TEMP",temp);
        Process process=builder.start(); boolean done=process.waitFor(timeout,TimeUnit.SECONDS);
        if(!done) { process.descendants().forEach(ProcessHandle::destroyForcibly);process.destroyForcibly();process.waitFor(5,TimeUnit.SECONDS); }
        try(InputStream in=Files.newInputStream(log)) { output.append(new String(in.readNBytes(50000),StandardCharsets.UTF_8)); }
        if(!done) { output.append("\nTIMEOUT\n");return 124; }
        output.append("\nEXIT ").append(process.exitValue()).append('\n');return process.exitValue();
    }
    private String executable(String tool) {
        boolean windows=System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
        Path exe=Path.of(System.getProperty("java.home"),"bin",tool+(windows?".exe":""));
        ApiException.require(Files.isRegularFile(exe),503,"A full JDK 17+ is required: missing "+tool);return exe.toString();
    }
    private Path directory(String id,int revision,String kind) {
        ApiException.require(id!=null && id.matches("[a-f0-9-]{36}") && revision>0,400,"Invalid workspace identity");
        Path dir=root.resolve(id).resolve("revision-"+revision).resolve(kind).normalize();
        ApiException.require(dir.startsWith(root),400,"Workspace escape denied");
        try { rejectSymlinks(dir); Files.createDirectories(dir); return dir; } catch(IOException e){ throw new IllegalStateException(e); }
    }
    private String hashDirectory(Path dir) {
        try { Map<String,String> files=new TreeMap<>(); for(String name:ALLOWED) { Path file=dir.resolve(name);rejectSymlinks(file);if(Files.exists(file))files.put(name,Files.readString(file,StandardCharsets.UTF_8)); } return WorkflowRepository.filesHash(files); }
        catch(IOException e){throw new IllegalStateException(e);}
    }
    private void rejectSymlinks(Path path) throws IOException {
        for(Path current=path;current!=null;current=current.getParent()) if(Files.isSymbolicLink(current))throw new IOException("Symbolic links are not allowed in workspaces");
    }
}
