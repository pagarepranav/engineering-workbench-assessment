package dev.pranav.workbench.workflow;

import java.util.*;

/** Explicit, serializable state. No agent may mutate state without engine validation. */
public final class Workflow {
    private Workflow() {}
    public enum Status { ACTIVE, WAITING_FOR_INPUT, WAITING_FOR_APPROVAL, STOPPED, FAILED, COMPLETED, REJECTED }
    public enum State { PENDING, RUNNING, WAITING_FOR_INPUT, WAITING_FOR_APPROVAL, SUCCEEDED, FAILED, INVALIDATED }
    public enum Stage { ANALYZE, PLAN, IMPLEMENT, TEST_SPEC, DOC_DRAFT, VALIDATE, SUMMARY, RELEASE }
    public enum Mode { MANUAL, DEMO }
    public enum Scenario { GREENFIELD, BROWNFIELD, AMBIGUOUS }
    public static class Task {
        public Stage stage;
        public List<Stage> dependsOn = new ArrayList<>();
        public State state = State.PENDING;
        public int attempts;
        public String output="";
        public String startedAt;
        public String completedAt;
        public Task() {}
        public Task(Stage stage, Stage... dependsOn) { this.stage=stage; this.dependsOn=List.of(dependsOn); }
    }
    public static class Run {
        public String id;
        public long version;
        public int revision=1;
        public String requirement;
        public String clarification="";
        public Mode mode;
        public Scenario scenario;
        public Status status=Status.ACTIVE;
        public List<Task> tasks = graph();
        public Map<String,String> files = new TreeMap<>();
        public Map<String,String> baseline = new TreeMap<>();
        public String candidateHash="";
        public String executionApproval="";
        public String releaseApproval="";
        public String createdAt;
        public String completedAt;
        public int retryCount;
        public int rollbackCount;
        public String failureStartedAt;
        public long recoveryMillis;
        public String provider="MANUAL_CHATGPT_HANDOFF";
        public Run() {}
        public Task task(Stage stage) { return tasks.stream().filter(t->t.stage==stage).findFirst().orElseThrow(); }
    }
    public static List<Task> graph() {
        return new ArrayList<>(List.of(new Task(Stage.ANALYZE),new Task(Stage.PLAN,Stage.ANALYZE),
            new Task(Stage.IMPLEMENT,Stage.PLAN),new Task(Stage.TEST_SPEC,Stage.PLAN),new Task(Stage.DOC_DRAFT,Stage.PLAN),
            new Task(Stage.VALIDATE,Stage.IMPLEMENT,Stage.TEST_SPEC,Stage.DOC_DRAFT),new Task(Stage.SUMMARY,Stage.VALIDATE),new Task(Stage.RELEASE,Stage.SUMMARY)));
    }
    public record Event(long sequence, String at, String type, String detail, String previousHash, String hash) {}
    public record Create(String requirement, Mode mode, Scenario scenario) {}
    public record Handoff(Stage stage, int revision, long version, String response) {}
    public record Approval(int revision, String hash, boolean approve, String reason) {}
    public record Revision(String requirement) {}
    public record Clarification(String answer) {}
}
