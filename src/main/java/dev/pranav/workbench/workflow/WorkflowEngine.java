package dev.pranav.workbench.workflow;

import dev.pranav.workbench.common.ApiException;
import dev.pranav.workbench.execution.WorkspaceTools;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static dev.pranav.workbench.workflow.Workflow.*;

@Service
public class WorkflowEngine {
    private final WorkflowRepository repo;
    private final WorkspaceTools tools;
    private final AgentProvider provider;
    private final DemoFixtures fixtures;
    private final ObjectMapper json;
    private final Clock clock;
    private final int maxRuns;
    private final ExecutorService workers=Executors.newFixedThreadPool(2);
    public WorkflowEngine(WorkflowRepository repo,WorkspaceTools tools,AgentProvider provider,DemoFixtures fixtures,ObjectMapper json,Clock clock,@Value("${workbench.max-runs:100}") int maxRuns) {
        this.repo=repo;this.tools=tools;this.provider=provider;this.fixtures=fixtures;this.json=json;this.clock=clock;this.maxRuns=maxRuns;
    }
    @PreDestroy void close() { workers.shutdownNow(); }
    @EventListener(ApplicationReadyEvent.class) public synchronized void recover() {
        for(Run run:repo.list()) {
            if(run.tasks.stream().anyMatch(t->t.state==State.RUNNING)) {
                for(Task task:run.tasks) if(task.state==State.RUNNING) {task.state=State.PENDING;task.output+="\nProcess interrupted. Outcome requires re-validation and new approval.";}
                run.executionApproval="";run.releaseApproval="";
                if(run.status!=Status.STOPPED)run.status=Status.ACTIVE;
                repo.save(run,"RECOVERED","Recovered interrupted execution; approvals cleared; no automatic code execution");
                if(run.status==Status.ACTIVE) advance(run);
            } else if(run.status==Status.ACTIVE) {
                run.executionApproval="";run.releaseApproval="";
                repo.save(run,"RECOVERED","Reconciled active run between persisted transitions; approvals cleared");
                advance(run);
            }
        }
    }
    public synchronized Run create(Create request) {
        ApiException.require(request!=null,400,"Request required"); validateRequirement(request.requirement());
        ApiException.require(repo.list().size()<maxRuns,429,"Local run limit reached");
        Run run=new Run();run.id=UUID.randomUUID().toString();run.createdAt=now();run.requirement=request.requirement();run.mode=request.mode()==null?Mode.MANUAL:request.mode();run.scenario=request.scenario()==null?Scenario.GREENFIELD:request.scenario();
        run.provider=run.mode==Mode.DEMO?"AUTHORED_DEMO_FIXTURE":"MANUAL_CHATGPT_HANDOFF";
        if(run.scenario==Scenario.BROWNFIELD)run.baseline=fixtures.files(false);
        repo.create(run);advance(run);return repo.get(run.id);
    }
    public synchronized Run handoff(String id,Handoff input) {
        Run run=repo.get(id); checkEditable(run);
        ApiException.require(input!=null && input.stage()!=null && input.revision()==run.revision && input.version()==run.version,409,"Stale handoff; refresh the run and export a new prompt");
        Task task=run.task(input.stage());
        ApiException.require(task.state==State.WAITING_FOR_INPUT && Set.of(Stage.ANALYZE,Stage.PLAN,Stage.IMPLEMENT).contains(task.stage),409,"Stage is not waiting for an AI handoff");
        ApiException.require(input.response()!=null && input.response().length()<=400000,400,"Response too large");
        JsonNode node;
        try { node=json.readTree(input.response()); } catch(RuntimeException e) { throw new ApiException(400,"Response must be valid JSON without Markdown fences"); }
        ApiException.require(node!=null && node.isObject(),400,"Response must be a JSON object");
        switch(task.stage) {
            case ANALYZE -> { text(node,"summary");array(node,"acceptanceCriteria",true);array(node,"questions",false);array(node,"assumptions",false); }
            case PLAN -> { text(node,"architecture");array(node,"tasks",true);array(node,"risks",true); }
            case IMPLEMENT -> {
                text(node,"rationale");ApiException.require(node.path("files").isObject(),400,"files must be an object");
                Map<String,String> files=new TreeMap<>();node.path("files").properties().forEach(e->{ApiException.require(e.getValue().isString(),400,"File values must be strings");files.put(e.getKey(),e.getValue().asString());});
                tools.validate(files);tools.write(run.id,run.revision,files);run.files=files;run.candidateHash=WorkflowRepository.filesHash(files);run.executionApproval="";run.releaseApproval="";
            }
            default -> throw new ApiException(400,"Unsupported handoff stage");
        }
        start(task);succeed(task,json.writeValueAsString(node));run.status=Status.ACTIVE;
        repo.save(run,"HANDOFF_IMPORTED","stage="+task.stage+"; revision="+run.revision+"; source=human-reviewed manual response");
        if(task.stage==Stage.ANALYZE && node.path("questions").size()>0 && run.clarification.isBlank()) {
            run.status=Status.WAITING_FOR_INPUT;repo.save(run,"CLARIFICATION_REQUIRED",node.path("questions").toString());return run;
        }
        advance(run);return repo.get(id);
    }
    public synchronized Run clarify(String id,String answer) {
        Run run=repo.get(id);checkEditable(run);
        ApiException.require(run.status==Status.WAITING_FOR_INPUT && run.task(Stage.ANALYZE).state==State.SUCCEEDED && run.task(Stage.PLAN).state==State.PENDING,409,"Run is not waiting for clarification");
        ApiException.require(answer!=null && !answer.isBlank() && answer.length()<=10000,400,"Clarification must contain 1-10000 characters");
        run.clarification=answer;run.status=Status.ACTIVE;repo.save(run,"CLARIFICATION_RECORDED",answer);advance(run);return repo.get(id);
    }
    public synchronized Run revise(String id,String requirement) {
        Run run=repo.get(id);validateRequirement(requirement);requireIdle(run);
        repo.archive(run);run.revision++;run.requirement=requirement;run.clarification="";run.tasks=Workflow.graph();run.files=new TreeMap<>();run.candidateHash="";run.executionApproval="";run.releaseApproval="";run.completedAt=null;run.retryCount=0;run.failureStartedAt=null;run.recoveryMillis=0;run.status=Status.ACTIVE;
        repo.save(run,"REPLANNED","Requirement changed; all descendants and approvals invalidated. Previous revision archived.");advance(run);return repo.get(id);
    }
    public synchronized Run stop(String id) {
        Run run=repo.get(id);ApiException.require(!Set.of(Status.COMPLETED,Status.REJECTED).contains(run.status),409,"Run is terminal");
        run.status=Status.STOPPED;run.executionApproval="";run.releaseApproval="";repo.save(run,"SAFE_STOP","New actions stopped; an already running process finishes under its timeout and its result is not promoted");return run;
    }
    public synchronized Run resume(String id) {
        Run run=repo.get(id);ApiException.require(run.status==Status.STOPPED,409,"Only stopped runs can resume");requireIdle(run);
        // Re-enter approval gates so the run status and task state agree after resuming.
        for(Task task:run.tasks) if(task.state==State.WAITING_FOR_APPROVAL)task.state=State.PENDING;
        run.status=run.tasks.stream().anyMatch(t->t.state==State.WAITING_FOR_INPUT)?Status.WAITING_FOR_INPUT:Status.ACTIVE;
        repo.save(run,"RESUMED","Resumed with fresh approval required for code execution");advance(run);return repo.get(id);
    }
    public synchronized Run approve(String id,String kind,Approval input) {
        Run run=repo.get(id);checkEditable(run);
        ApiException.require(input!=null && input.revision()==run.revision && Objects.equals(input.hash(),run.candidateHash) && !run.candidateHash.isBlank(),409,"Approval must match the current revision and candidate hash");
        Stage stage=switch(kind) {case "execution"->Stage.VALIDATE;case "release"->Stage.RELEASE;default->throw new ApiException(400,"Unknown approval kind");};
        ApiException.require(run.task(stage).state==State.WAITING_FOR_APPROVAL,409,"No pending approval for this action");
        ApiException.require(input.reason()!=null && !input.reason().isBlank() && input.reason().length()<=2000,400,"Record an approval or rejection reason");
        if(!input.approve()) {run.status=Status.REJECTED;run.completedAt=now();repo.save(run,"APPROVAL_REJECTED",kind+": "+input.reason());return run;}
        ApiException.require(tools.diskHash(id,run.revision).equals(run.candidateHash),409,"Candidate files changed; import and review a fresh bundle");
        if(stage==Stage.VALIDATE)run.executionApproval=run.candidateHash;else run.releaseApproval=run.candidateHash;
        run.task(stage).state=State.PENDING;run.status=Status.ACTIVE;repo.save(run,"APPROVAL_GRANTED",kind+"; hash="+run.candidateHash+"; reason="+input.reason());advance(run);return repo.get(id);
    }
    public String prompt(String id,Stage stage) { Run run=repo.get(id);ApiException.require(Set.of(Stage.ANALYZE,Stage.PLAN,Stage.IMPLEMENT).contains(stage),400,"This stage uses deterministic tools");return provider.prompt(run,stage); }
    private void advance(Run run) {
        if(run.status==Status.STOPPED || run.status==Status.REJECTED || run.status==Status.COMPLETED)return;
        // A crash between saving analysis and saving its question gate must not skip clarification.
        if(run.task(Stage.ANALYZE).state==State.SUCCEEDED && run.task(Stage.PLAN).state==State.PENDING && run.clarification.isBlank()
            && json.readTree(run.task(Stage.ANALYZE).output).path("questions").size()>0) {
            run.status=Status.WAITING_FOR_INPUT;repo.save(run,"CLARIFICATION_REQUIRED","Resolve analysis questions before planning");return;
        }
        boolean changed;
        do {
            changed=false;
            for(Task task:run.tasks) {
                if(task.state!=State.PENDING || !task.dependsOn.stream().allMatch(dep->run.task(dep).state==State.SUCCEEDED))continue;
                if(task.stage==Stage.ANALYZE || task.stage==Stage.PLAN || task.stage==Stage.IMPLEMENT) {
                    if(run.mode==Mode.MANUAL || (task.stage==Stage.IMPLEMENT && run.retryCount>0)) {
                        task.state=State.WAITING_FOR_INPUT;run.status=Status.WAITING_FOR_INPUT;repo.save(run,"HANDOFF_REQUIRED","stage="+task.stage);changed=true;continue;
                    }
                    demoStage(run,task);changed=true;
                    if(run.status==Status.WAITING_FOR_INPUT)return;
                } else if(task.stage==Stage.TEST_SPEC || task.stage==Stage.DOC_DRAFT) {
                    start(task);repo.save(run,"TASK_STARTED","stage="+task.stage+"; independent branch");
                    String branchId=run.id;int branchRevision=run.revision;Stage branchStage=task.stage;
                    workers.submit(()->draft(branchId,branchRevision,branchStage));changed=true;
                } else if(task.stage==Stage.VALIDATE || task.stage==Stage.RELEASE) {
                    String approval=task.stage==Stage.VALIDATE?run.executionApproval:run.releaseApproval;
                    if(!approval.equals(run.candidateHash) || approval.isBlank()) {
                        task.state=State.WAITING_FOR_APPROVAL;run.status=Status.WAITING_FOR_APPROVAL;
                        repo.save(run,"APPROVAL_REQUIRED","stage="+task.stage+"; hash="+run.candidateHash+"; execution is local and NOT OS sandboxed");return;
                    }
                    start(task);run.status=Status.ACTIVE;repo.save(run,"TASK_STARTED","stage="+task.stage+"; attempt="+task.attempts);
                    String id=run.id,hash=run.candidateHash;int revision=run.revision;Stage stage=task.stage;
                    workers.submit(()->execute(id,revision,stage,hash));return;
                } else if(task.stage==Stage.SUMMARY) {
                    succeed(task,"Validation passed for revision "+run.revision+"; candidate SHA-256 "+run.candidateHash+". Provider: "+run.provider+". Human release approval required. Actual tool evidence is attached to VALIDATE. Candidate is a JDK-only in-memory demonstration service; main Spring Boot service has database persistence.");
                    repo.save(run,"SUMMARY_READY","Review validation, diff, provider provenance, and release scope");changed=true;
                }
            }
        } while(changed);
    }
    private void draft(String id,int revision,Stage stage) {
        String output=stage==Stage.TEST_SPEC?"Contract: create, redirect, collision, invalid scheme, unknown code, click count; add exact-boundary expiration checks when requested. CandidateTest.java executes assertions; review for adequacy.":"Setup: JDK 17+, compile Java sources, run CandidateTest, run Server on loopback:9090. Candidate storage is in memory. Final evidence follows validation.";
        synchronized(this) {
            Run run=repo.get(id);if(run.revision!=revision)return;
            if(run.status==Status.STOPPED){run.task(stage).state=State.PENDING;repo.save(run,"BRANCH_STOPPED","stage="+stage);return;}
            succeed(run.task(stage),output);repo.save(run,"TASK_SUCCEEDED","stage="+stage+"; independent worker completed");advance(run);
        }
    }
    private void demoStage(Run run,Task task) {
        start(task);
        if(task.stage==Stage.ANALYZE) {
            succeed(task,json.writeValueAsString(Map.of("summary",run.requirement,"acceptanceCriteria",List.of("Create and redirect valid HTTP/HTTPS URLs","Reject conflicts and unsafe schemes","Record successful clicks"),"assumptions",List.of("Single-user local demonstration","Authored fixture, not autonomous AI"),"questions",run.scenario==Scenario.AMBIGUOUS?List.of("What abuse limit and analytics retention do you want? Which behavior should the candidate implement?"):List.of())));
            if(run.scenario==Scenario.AMBIGUOUS && run.clarification.isBlank())run.status=Status.WAITING_FOR_INPUT;
        } else if(task.stage==Stage.PLAN) {
            succeed(task,json.writeValueAsString(Map.of("architecture","JDK URL store and HTTP server in a candidate workspace. Deterministic, authored demo plan.","tasks",List.of("Implement mapping and redirect","Prepare independent test and setup specifications","Compile and execute assertions after human approval","Review and locally release"),"risks",List.of("Generated code runs with the local OS user's permissions after approval","Candidate storage is in memory"),"impact",run.scenario==Scenario.BROWNFIELD?"Change LinkStore.create/resolve and CandidateTest to support expiration. Preserve baseline diff.":"Empty candidate workspace")));
        } else {
            // An ambiguous requirement never gets silently mapped to a canned implementation.
            if(run.scenario==Scenario.AMBIGUOUS) {task.state=State.WAITING_FOR_INPUT;run.status=Status.WAITING_FOR_INPUT;repo.save(run,"HANDOFF_REQUIRED","Ambiguous clarification requires a real manual implementation response");return;}
            run.files=fixtures.files(run.scenario==Scenario.BROWNFIELD);tools.write(run.id,run.revision,run.files);run.candidateHash=WorkflowRepository.filesHash(run.files);
            succeed(task,"Authored demo fixture staged. No model was called. Review source and test adequacy before execution.");
        }
        repo.save(run,"DEMO_FIXTURE_STAGE","stage="+task.stage+"; authored demonstration, no AI call");
    }
    private void execute(String id,int revision,Stage stage,String hash) {
        WorkspaceTools.Result result;
        try {result=stage==Stage.VALIDATE?tools.validateCandidate(id,revision,hash):tools.release(id,revision,hash);}
        catch(Exception e) {result=new WorkspaceTools.Result(false,"Execution failed: "+e.getMessage(),0,hash);}
        synchronized(this) {
            Run run=repo.get(id);
            if(run.revision!=revision || !run.candidateHash.equals(hash))return;
            Task task=run.task(stage);
            task.output=result.output();task.completedAt=now();
            if(run.status==Status.STOPPED) {
                task.state=State.PENDING;run.executionApproval="";run.releaseApproval="";
                repo.save(run,"STOPPED_RESULT_RECORDED","stage="+stage+"; result="+result.success()+"; not promoted");return;
            }
            if(result.success()) {
                task.state=State.SUCCEEDED;
                if(run.failureStartedAt!=null) {run.recoveryMillis+=Duration.between(Instant.parse(run.failureStartedAt),clock.instant()).toMillis();run.failureStartedAt=null;}
                if(stage==Stage.RELEASE) {
                    try {tools.promote(id,revision,hash);run.status=Status.COMPLETED;run.completedAt=now();}
                    catch(Exception e){task.state=State.FAILED;run.status=Status.FAILED;run.completedAt=now();run.rollbackCount++;repo.save(run,"RELEASE_ROLLED_BACK","Promotion failed; previous pointer retained: "+e.getMessage());return;}
                }
                repo.save(run,"TASK_SUCCEEDED","stage="+stage+"; elapsedMillis="+result.elapsedMillis()+"; hash="+hash);
                advance(run);
            } else {
                task.state=State.FAILED;if(run.failureStartedAt==null)run.failureStartedAt=now();
                if(stage==Stage.RELEASE) {run.rollbackCount++;run.status=Status.FAILED;run.completedAt=now();repo.save(run,"RELEASE_ROLLED_BACK","Local release pointer retained; smoke validation failed. "+result.output());}
                else if(run.retryCount<2) {
                    run.retryCount++;run.executionApproval="";run.releaseApproval="";run.status=Status.WAITING_FOR_INPUT;
                    run.task(Stage.IMPLEMENT).state=State.WAITING_FOR_INPUT;task.state=State.PENDING;
                    repo.save(run,"REPAIR_REQUIRED","Repair "+run.retryCount+" of 2; import corrected files, then approve their new hash. "+result.output());
                } else {run.status=Status.FAILED;run.completedAt=now();repo.save(run,"RETRY_EXHAUSTED","Two repair attempts exhausted; safe stop");}
            }
        }
    }
    private void checkEditable(Run run) { ApiException.require(!Set.of(Status.STOPPED,Status.FAILED,Status.COMPLETED,Status.REJECTED).contains(run.status),409,"Run is not editable; resume or create a requirement revision"); }
    private void requireIdle(Run run) { ApiException.require(run.tasks.stream().noneMatch(t->t.state==State.RUNNING),409,"A tool is running; wait for its bounded completion"); }
    private void validateRequirement(String requirement) { ApiException.require(requirement!=null && !requirement.isBlank() && requirement.length()<=10000,400,"Requirement must contain 1-10000 characters"); }
    private String now(){return clock.instant().toString();}
    private void start(Task task){task.state=State.RUNNING;task.attempts++;task.startedAt=now();task.completedAt=null;}
    private void succeed(Task task,String output){task.state=State.SUCCEEDED;task.output=output;task.completedAt=now();}
    private void text(JsonNode node,String key){ApiException.require(node.path(key).isString() && !node.path(key).asString().isBlank() && node.path(key).asString().length()<=20000,400,key+" must be a nonempty string up to 20000 characters");}
    private void array(JsonNode node,String key,boolean nonempty){ApiException.require(node.path(key).isArray() && (!nonempty || node.path(key).size()>0) && node.path(key).size()<=100,400,key+" must be a string array");for(JsonNode item:node.path(key))ApiException.require(item.isString() && !item.asString().isBlank() && item.asString().length()<=2000,400,key+" entries must be nonempty strings up to 2000 characters");}
}
