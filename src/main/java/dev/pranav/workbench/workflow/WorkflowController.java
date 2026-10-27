package dev.pranav.workbench.workflow;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.time.*;
import java.util.*;
import static dev.pranav.workbench.workflow.Workflow.*;

@RestController
@RequestMapping("/api")
public class WorkflowController {
    private final WorkflowEngine engine;
    private final WorkflowRepository repo;
    public WorkflowController(WorkflowEngine engine,WorkflowRepository repo) {this.engine=engine;this.repo=repo;}
    @PostMapping("/runs") ResponseEntity<?> create(@RequestBody Create input) {var run=engine.create(input);return ResponseEntity.created(URI.create("/api/runs/"+run.id)).body(run);}
    @GetMapping("/runs") Object list() {return repo.list().stream().map(r->Map.of("id",r.id,"requirement",r.requirement,"revision",r.revision,"mode",r.mode,"scenario",r.scenario,"status",r.status,"createdAt",r.createdAt,"provider",r.provider)).toList();}
    @GetMapping("/runs/{id}") Object get(@PathVariable String id) {return repo.get(id);}
    @GetMapping("/runs/{id}/prompt/{stage}") Object prompt(@PathVariable String id,@PathVariable Stage stage) {var run=repo.get(id);return Map.of("stage",stage,"revision",run.revision,"version",run.version,"prompt",engine.prompt(id,stage));}
    @PostMapping("/runs/{id}/handoff") Object handoff(@PathVariable String id,@RequestBody Handoff input) {return engine.handoff(id,input);}
    @PostMapping("/runs/{id}/clarification") Object clarify(@PathVariable String id,@RequestBody Clarification input) {return engine.clarify(id,input.answer());}
    @PostMapping("/runs/{id}/revision") Object revise(@PathVariable String id,@RequestBody Revision input) {return engine.revise(id,input.requirement());}
    @PostMapping("/runs/{id}/stop") Object stop(@PathVariable String id) {return engine.stop(id);}
    @PostMapping("/runs/{id}/resume") Object resume(@PathVariable String id) {return engine.resume(id);}
    @PostMapping("/runs/{id}/approvals/{kind}") Object approve(@PathVariable String id,@PathVariable String kind,@RequestBody Approval input) {return engine.approve(id,kind,input);}
    @GetMapping("/runs/{id}/events") Object events(@PathVariable String id) {return Map.of("events",repo.events(id),"hashChainValid",repo.verify(id),"note","Tamper-evident hash chain in local storage; not independently anchored or tamper-proof");}
    @GetMapping("/runs/{id}/revisions") Object revisions(@PathVariable String id) {return repo.revisions(id);}
    @GetMapping("/runs/{id}/export") Object export(@PathVariable String id) {return Map.of("run",repo.get(id),"events",repo.events(id),"archivedRevisions",repo.revisions(id),"hashChainValid",repo.verify(id));}
    @GetMapping("/metrics") Object metrics() {
        List<Run> runs=repo.list();long completed=runs.stream().filter(r->r.status==Status.COMPLETED).count();
        long terminal=runs.stream().filter(r->Set.of(Status.COMPLETED,Status.FAILED,Status.REJECTED).contains(r.status)).count();
        var latencies=runs.stream().filter(r->r.completedAt!=null).map(r->Duration.between(Instant.parse(r.createdAt),Instant.parse(r.completedAt)).toMillis()).toList();
        return Map.of("runCount",runs.size(),"terminalCount",terminal,"completedCount",completed,"terminalSuccessRate",terminal==0?0.0:(double)completed/terminal,"retryCount",runs.stream().mapToInt(r->r.retryCount).sum(),"rollbackCount",runs.stream().mapToInt(r->r.rollbackCount).sum(),"recoveryMillis",runs.stream().mapToLong(r->r.recoveryMillis).sum(),"meanTerminalLatencyMillis",latencies.stream().mapToLong(Long::longValue).average().orElse(0),"definitions","Success = completed / (completed + failed + rejected). Latency includes human wait time. Recovery sums failure-to-next-success intervals. Demo-scale observations, not production SLOs.");
    }
}
