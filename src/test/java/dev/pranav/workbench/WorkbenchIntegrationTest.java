package dev.pranav.workbench;

import dev.pranav.workbench.common.ApiException;
import dev.pranav.workbench.execution.WorkspaceTools;
import dev.pranav.workbench.links.LinkService;
import dev.pranav.workbench.workflow.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Predicate;
import static org.junit.jupiter.api.Assertions.*;
import static dev.pranav.workbench.workflow.Workflow.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"spring.datasource.url=jdbc:h2:mem:integration;DB_CLOSE_DELAY=-1","workbench.storage=./target/test-workspaces","workbench.max-runs=200","workbench.command-timeout-seconds=8"})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WorkbenchIntegrationTest {
    @Autowired LinkService links;
    @Autowired WorkflowEngine engine;
    @Autowired WorkflowRepository repo;
    @Autowired WorkspaceTools tools;
    @Autowired DemoFixtures fixtures;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;
    @Value("${local.server.port}") int port;
    final HttpClient client=HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    private HttpResponse<String> request(String path,String method,Object body) throws Exception {
        var builder=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path));
        if(body==null)builder.method(method,HttpRequest.BodyPublishers.noBody());else builder.header("Content-Type","application/json").method(method,HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        return client.send(builder.build(),HttpResponse.BodyHandlers.ofString());
    }
    private String alias(){return "test-"+UUID.randomUUID().toString().substring(0,12);}
    private Run demo(Scenario scenario){return engine.create(new Create("Build a URL shortener; include expiration if requested.",Mode.DEMO,scenario));}
    private Run await(String id,Predicate<Run> predicate) throws Exception {long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(25);Run r;do{r=repo.get(id);if(predicate.test(r))return r;Thread.sleep(40);}while(System.nanoTime()<end);fail("Workflow timeout: "+json.writeValueAsString(r));return r;}
    private Run executionGate(String id)throws Exception{return await(id,r->r.task(Stage.VALIDATE).state==State.WAITING_FOR_APPROVAL);}
    private void approve(Run run,String kind){engine.approve(run.id,kind,new Approval(run.revision,run.candidateHash,true,"Reviewed trusted authored fixture for integration test"));}
    private Run releaseGate(String id)throws Exception{return await(id,r->r.task(Stage.RELEASE).state==State.WAITING_FOR_APPROVAL);}
    private void expectStatus(int status,Runnable action){assertEquals(status,assertThrows(ApiException.class,action::run).status());}

    @Test void createRedirectAndUtcAnalyticsUseActualHttp() throws Exception {
        var created=request("/api/links","POST",Map.of("destination","https://example.com/path?q=1","alias",alias()));assertEquals(201,created.statusCode());
        JsonNode link=json.readTree(created.body());var redirected=request("/s/"+link.path("code").asString(),"GET",null);
        assertEquals(302,redirected.statusCode());assertEquals("https://example.com/path?q=1",redirected.headers().firstValue("Location").orElseThrow());
        assertEquals("no-store",redirected.headers().firstValue("Cache-Control").orElseThrow());
        var analytics=json.readTree(request("/api/links/"+link.path("id").asString()+"/analytics","GET",null).body());assertEquals(1,analytics.path("totalClicks").asInt());assertEquals("UTC",analytics.path("timezone").asString());assertEquals(LocalDate.now(ZoneOffset.UTC).toString(),analytics.path("daily").get(0).path("date").asString());
    }
    @Test void unsafeUrlsAndCredentialUrlsAreRejected() {
        for(String url:List.of("javascript:alert(1)","file:///etc/passwd","https://user:pass@example.com","https://example.com\r\nX-Test: a","relative/path"))expectStatus(400,()->links.create(new LinkService.Create(url,null,null)));
    }
    @Test void aliasUniquenessSurvivesConcurrency() throws Exception {
        String code=alias();ExecutorService pool=Executors.newFixedThreadPool(6);try{
            List<Callable<Integer>> calls=new ArrayList<>();for(int i=0;i<8;i++)calls.add(()->{try{links.create(new LinkService.Create("https://example.com",code,null));return 201;}catch(ApiException e){return e.status();}});
            List<Integer> outcomes=new ArrayList<>();for(var f:pool.invokeAll(calls))outcomes.add(f.get());assertEquals(1,outcomes.stream().filter(x->x==201).count());assertEquals(7,outcomes.stream().filter(x->x==409).count());
        }finally{pool.shutdownNow();}
    }
    @Test void expirationAndDisableDoNotRecordClicks() {
        var link=links.create(new LinkService.Create("https://example.com",alias(),null));
        links.update(link.id(),new LinkService.Update(false,null,false));expectStatus(410,()->links.redirect(link.code()));assertEquals(0L,links.analytics(link.id()).get("totalClicks"));
        links.update(link.id(),new LinkService.Update(true,null,false));db.update("UPDATE links SET expires_at=? WHERE id=?",Instant.now().minusSeconds(1).toString(),link.id());expectStatus(410,()->links.redirect(link.code()));assertEquals(0L,links.analytics(link.id()).get("totalClicks"));
        links.update(link.id(),new LinkService.Update(null,null,true));assertEquals("https://example.com",links.redirect(link.code()));
    }
    @Test void httpDisableAndEnableAcceptOnlyActiveAndPreserveExpiration() throws Exception {
        Instant expiration=Instant.now().plusSeconds(3600);
        var link=links.create(new LinkService.Create("https://example.com",alias(),expiration));
        var disabled=request("/api/links/"+link.id(),"PATCH",Map.of("active",false));
        assertEquals(200,disabled.statusCode(),disabled.body());
        assertFalse(json.readTree(disabled.body()).path("active").asBoolean());
        assertEquals(expiration.toString(),links.get(link.id()).expiresAt());
        assertEquals(410,request("/s/"+link.code(),"GET",null).statusCode());
        assertEquals(0L,links.analytics(link.id()).get("totalClicks"));
        var enabled=request("/api/links/"+link.id(),"PATCH",Map.of("active",true));
        assertEquals(200,enabled.statusCode(),enabled.body());
        assertTrue(json.readTree(enabled.body()).path("active").asBoolean());
        assertEquals(expiration.toString(),links.get(link.id()).expiresAt());
        assertEquals(302,request("/s/"+link.code(),"GET",null).statusCode());
    }
    @Test void httpExpirationUpdatesDoNotRequireAnActiveOrClearFlag() throws Exception {
        var link=links.create(new LinkService.Create("https://example.com",alias(),null));
        Instant expiration=Instant.now().plusSeconds(3600);
        var changed=request("/api/links/"+link.id(),"PATCH",Map.of("expiresAt",expiration.toString()));
        assertEquals(200,changed.statusCode(),changed.body());
        assertTrue(links.get(link.id()).active());assertEquals(expiration.toString(),links.get(link.id()).expiresAt());
        var cleared=request("/api/links/"+link.id(),"PATCH",Map.of("clearExpiration",true));
        assertEquals(200,cleared.statusCode(),cleared.body());assertNull(links.get(link.id()).expiresAt());
    }
    @Test void exactExpiryBoundaryIsGone() {
        Instant boundary=Instant.parse("2026-01-01T00:00:00Z");var service=new LinkService(db,Clock.fixed(boundary.minusSeconds(1),ZoneOffset.UTC));var link=service.create(new LinkService.Create("https://example.com",alias(),boundary));
        var atBoundary=new LinkService(db,Clock.fixed(boundary,ZoneOffset.UTC));expectStatus(410,()->atBoundary.redirect(link.code()));
    }
    @Test void badExpirationPaginationAndReservedAliasesAreRejected() {
        expectStatus(400,()->links.create(new LinkService.Create("https://example.com","api",null)));
        expectStatus(400,()->links.create(new LinkService.Create("https://example.com",null,Instant.now().minusSeconds(1))));
        expectStatus(400,()->links.list(-1,10));expectStatus(400,()->links.list(0,1000));
        expectStatus(404,()->links.redirect("missing-"+alias()));
    }
    @Test void crossOriginMutationAndNonJsonFormsAreRejected() throws Exception {
        var cross=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/links")).header("Content-Type","application/json").header("Origin","https://evil.example").POST(HttpRequest.BodyPublishers.ofString("{}")).build();assertEquals(403,client.send(cross,HttpResponse.BodyHandlers.ofString()).statusCode());
        var form=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/links")).header("Content-Type","text/plain").POST(HttpRequest.BodyPublishers.ofString("{}")).build();assertEquals(415,client.send(form,HttpResponse.BodyHandlers.ofString()).statusCode());
    }
    @Test void healthStaticUiAndMetricsAreServed() throws Exception {
        assertEquals(200,request("/api/health","GET",null).statusCode());assertTrue(request("/","GET",null).body().contains("Engineering Workbench"));assertEquals(200,request("/api/metrics","GET",null).statusCode());
    }
    @Test void greenfieldDemoCompilesTestsAndReleasesRealFiles() throws Exception {
        Run run=executionGate(demo(Scenario.GREENFIELD).id);assertEquals("AUTHORED_DEMO_FIXTURE",run.provider);
        assertTrue(run.tasks.stream().filter(t->Set.of(Stage.TEST_SPEC,Stage.DOC_DRAFT).contains(t.stage)).allMatch(t->t.state==State.SUCCEEDED));
        approve(run,"execution");run=releaseGate(run.id);assertTrue(run.task(Stage.VALIDATE).output.contains("PASS: 6 candidate assertions"));assertTrue(run.task(Stage.VALIDATE).output.contains("EXIT 0"));
        approve(run,"release");run=await(run.id,r->r.status==Status.COMPLETED);assertTrue(repo.verify(run.id));assertTrue(Files.exists(Path.of("target/test-workspaces/release-"+run.id+".txt")));
    }
    @Test void brownfieldPreservesBaselineAndValidatesExpiration() throws Exception {
        Run run=executionGate(demo(Scenario.BROWNFIELD).id);assertNotEquals(run.baseline.get("LinkStore.java"),run.files.get("LinkStore.java"));approve(run,"execution");run=releaseGate(run.id);assertTrue(run.task(Stage.VALIDATE).output.contains("PASS: 9 candidate assertions"));
    }
    @Test void ambiguousScenarioPausesAndDoesNotInventImplementation() throws Exception {
        Run run=demo(Scenario.AMBIGUOUS);assertEquals(Status.WAITING_FOR_INPUT,run.status);assertTrue(run.files.isEmpty());
        engine.clarify(run.id,"Implement expiration only; no IP collection; UTC aggregate analytics.");run=await(run.id,r->r.task(Stage.IMPLEMENT).state==State.WAITING_FOR_INPUT&&r.tasks.stream().noneMatch(t->t.state==State.RUNNING));assertTrue(run.files.isEmpty());assertEquals(State.PENDING,run.task(Stage.VALIDATE).state);
    }
    @Test void manualModeImportsStructuredAnalysisAndPlan() throws Exception {
        Run run=engine.create(new Create("Build a URL service",Mode.MANUAL,Scenario.GREENFIELD));assertEquals(State.WAITING_FOR_INPUT,run.task(Stage.ANALYZE).state);assertTrue(engine.prompt(run.id,Stage.ANALYZE).contains("acceptanceCriteria"));
        run=engine.handoff(run.id,new Handoff(Stage.ANALYZE,run.revision,run.version,json.writeValueAsString(Map.of("summary","URL service","acceptanceCriteria",List.of("Redirect valid links"),"questions",List.of(),"assumptions",List.of("Local single user")))));
        run=engine.handoff(run.id,new Handoff(Stage.PLAN,run.revision,run.version,json.writeValueAsString(Map.of("architecture","JDK URL store","tasks",List.of("Implement","Test"),"risks",List.of("Local execution requires review")))));
        run=await(run.id,r->r.task(Stage.IMPLEMENT).state==State.WAITING_FOR_INPUT&&r.tasks.stream().noneMatch(t->t.state==State.RUNNING));
        assertEquals("MANUAL_CHATGPT_HANDOFF",run.provider);assertTrue(run.files.isEmpty());
        run=engine.handoff(run.id,new Handoff(Stage.IMPLEMENT,run.revision,run.version,json.writeValueAsString(Map.of("files",fixtures.files(false),"rationale","Human reviewed authored test bundle"))));assertEquals(State.WAITING_FOR_APPROVAL,run.task(Stage.VALIDATE).state);
    }
    @Test void malformedOrStaleHandoffsCannotChangeState() {
        Run run=engine.create(new Create("Build a URL service",Mode.MANUAL,Scenario.GREENFIELD));
        expectStatus(400,()->engine.handoff(run.id,new Handoff(Stage.ANALYZE,run.revision,run.version,"not json")));
        expectStatus(400,()->engine.handoff(run.id,new Handoff(Stage.ANALYZE,run.revision,run.version,"{}")));
        expectStatus(409,()->engine.handoff(run.id,new Handoff(Stage.ANALYZE,2,run.version,"{}")));
        assertEquals(State.WAITING_FOR_INPUT,repo.get(run.id).task(Stage.ANALYZE).state);
    }
    @Test void approvalsBindRevisionAndHashAndCanBeRejected() throws Exception {
        Run run=executionGate(demo(Scenario.GREENFIELD).id);
        expectStatus(409,()->engine.approve(run.id,"execution",new Approval(run.revision,"bad",true,"review")));
        expectStatus(409,()->engine.approve(run.id,"execution",new Approval(run.revision+1,run.candidateHash,true,"review")));
        engine.approve(run.id,"execution",new Approval(run.revision,run.candidateHash,false,"Reject for missing acceptance evidence"));assertEquals(Status.REJECTED,repo.get(run.id).status);assertFalse(repo.events(run.id).stream().anyMatch(e->e.type().equals("TASK_STARTED")&&e.detail().contains("VALIDATE")));
    }
    @Test void tamperedDiskFilesInvalidateApproval() throws Exception {
        Run run=executionGate(demo(Scenario.GREENFIELD).id);Path file=Path.of("target/test-workspaces",run.id,"revision-1/candidate/LinkStore.java");Files.writeString(file,run.files.get("LinkStore.java")+"\n// modified\n");expectStatus(409,()->approve(run,"execution"));
    }
    @Test void stopResumeAndRequirementRevisionClearApprovals() throws Exception {
        Run run=executionGate(demo(Scenario.GREENFIELD).id);engine.stop(run.id);assertEquals(Status.STOPPED,repo.get(run.id).status);Run stopped=run;expectStatus(409,()->approve(stopped,"execution"));engine.resume(run.id);assertEquals(State.WAITING_FOR_APPROVAL,repo.get(run.id).task(Stage.VALIDATE).state);
        run=engine.revise(run.id,"Build a revised URL service with stricter validation");run=executionGate(run.id);assertEquals(2,run.revision);assertEquals("",run.executionApproval);assertEquals(1,repo.revisions(run.id).size());assertTrue(repo.verify(run.id));
    }
    @Test void failureTriggersBoundedRepairThenSafeStop() throws Exception {
        Run run=executionGate(demo(Scenario.GREENFIELD).id);String id=run.id;
        for(int attempt=0;attempt<3;attempt++){
            if(attempt==0){ // Use revision to enter manual mode without hidden demo repair behavior.
                run.mode=Mode.MANUAL;run.task(Stage.IMPLEMENT).state=State.WAITING_FOR_INPUT;repo.save(run,"TEST_SETUP","Switch to manual handoff for a controlled failing fixture");
            }
            Map<String,String> failing=new TreeMap<>(fixtures.files(false));failing.put("CandidateTest.java","public class CandidateTest { public static void main(String[] args) { throw new AssertionError(\"Controlled failure\"); } }");
            run=repo.get(id);run=engine.handoff(id,new Handoff(Stage.IMPLEMENT,run.revision,run.version,json.writeValueAsString(Map.of("files",failing,"rationale","Controlled failure demonstrates bounded repair"))));approve(run,"execution");
            run=await(id,r->r.status==Status.WAITING_FOR_INPUT||r.status==Status.FAILED);
            assertTrue(run.task(Stage.VALIDATE).output.contains("Controlled failure"));
        }
        assertEquals(Status.FAILED,run.status);assertEquals(2,run.retryCount);assertEquals(3,run.task(Stage.VALIDATE).attempts);
    }
    @Test void restartRecoveryNeverReplaysApprovedExecution() throws Exception {
        Run run=executionGate(demo(Scenario.GREENFIELD).id);run.task(Stage.VALIDATE).state=State.RUNNING;run.executionApproval=run.candidateHash;repo.save(run,"TEST_INTERRUPTION","Simulate crash after a task lease was persisted");engine.recover();run=repo.get(run.id);assertEquals(State.WAITING_FOR_APPROVAL,run.task(Stage.VALIDATE).state);assertEquals("",run.executionApproval);assertTrue(repo.events(run.id).stream().anyMatch(e->e.type().equals("RECOVERED")));
    }
    @Test void pathTraversalAndMissingRequiredFilesAreRejected() {
        var files=new TreeMap<>(fixtures.files(false));files.put("../escape.java","bad");expectStatus(400,()->tools.validate(files));files.remove("../escape.java");files.remove("CandidateTest.java");expectStatus(400,()->tools.validate(files));
    }
    @Test void auditMutationIsDetected() throws Exception {
        Run run=executionGate(demo(Scenario.GREENFIELD).id);assertTrue(repo.verify(run.id));db.update("UPDATE audit_events SET detail=? WHERE run_id=? AND sequence_no=1","tampered",run.id);assertFalse(repo.verify(run.id));
    }
    @Test void unchangedRunLoadsFromPersistentRepository() throws Exception {
        Run run=executionGate(demo(Scenario.GREENFIELD).id);var reloaded=new WorkflowRepository(db,json,Clock.systemUTC()).get(run.id);assertEquals(run.candidateHash,reloaded.candidateHash);assertEquals(run.version,reloaded.version);
    }
    @Test void releaseFailureRetainsPreviousPointer() throws Exception {
        Run run=executionGate(demo(Scenario.GREENFIELD).id);approve(run,"execution");run=releaseGate(run.id);approve(run,"release");run=await(run.id,r->r.status==Status.COMPLETED);
        Path pointer=Path.of("target/test-workspaces/release-"+run.id+".txt");String previous=Files.readString(pointer);
        run=engine.revise(run.id,"Add release-only failure demonstration");run=executionGate(run.id);
        run.mode=Mode.MANUAL;run.task(Stage.IMPLEMENT).state=State.WAITING_FOR_INPUT;repo.save(run,"TEST_SETUP","Trusted release-only failure fixture");
        var files=new TreeMap<>(fixtures.files(false));files.put("CandidateTest.java",files.get("CandidateTest.java").replace("Instant now=", "if(System.getProperty(\"user.dir\").endsWith(\"release\")) throw new AssertionError(\"Controlled release smoke failure\"); Instant now="));
        run=engine.handoff(run.id,new Handoff(Stage.IMPLEMENT,run.revision,run.version,json.writeValueAsString(Map.of("files",files,"rationale","Trusted release failure test"))));approve(run,"execution");run=releaseGate(run.id);approve(run,"release");run=await(run.id,r->r.status==Status.FAILED);
        assertEquals(1,run.rollbackCount);assertEquals(previous,Files.readString(pointer));assertTrue(run.task(Stage.RELEASE).output.contains("Controlled release smoke failure"));
    }
    @Test void independentBranchesStartBeforeEitherMustComplete() throws Exception {
        Run run=executionGate(demo(Scenario.GREENFIELD).id);List<Event> events=repo.events(run.id);
        long testStart=events.stream().filter(e->e.type().equals("TASK_STARTED")&&e.detail().contains("TEST_SPEC")).findFirst().orElseThrow().sequence();
        long docStart=events.stream().filter(e->e.type().equals("TASK_STARTED")&&e.detail().contains("DOC_DRAFT")).findFirst().orElseThrow().sequence();
        long firstFinish=events.stream().filter(e->e.type().equals("TASK_SUCCEEDED")&&(e.detail().contains("TEST_SPEC")||e.detail().contains("DOC_DRAFT"))).mapToLong(Event::sequence).min().orElseThrow();
        assertTrue(testStart<firstFinish&&docStart<firstFinish);
    }
    @Test void restartBetweenAnalysisAndQuestionGateStillRequiresClarification() {
        Run run=engine.create(new Create("Clarify URL analytics",Mode.MANUAL,Scenario.AMBIGUOUS));
        run.task(Stage.ANALYZE).state=State.SUCCEEDED;run.task(Stage.ANALYZE).output=json.writeValueAsString(Map.of("questions",List.of("What retention period?")));run.status=Status.ACTIVE;
        repo.save(run,"TEST_INTERRUPTION","Simulate crash after analysis save and before question gate");engine.recover();
        run=repo.get(run.id);assertEquals(Status.WAITING_FOR_INPUT,run.status);assertEquals(State.PENDING,run.task(Stage.PLAN).state);
    }
    @Test void archiveOperationCanBeReconciledAfterInterruption() throws Exception {
        Run run=executionGate(demo(Scenario.GREENFIELD).id);repo.archive(run);repo.archive(run);assertEquals(1,repo.revisions(run.id).size());
        run=engine.revise(run.id,"Revised after an interrupted archive");assertEquals(2,run.revision);assertEquals(1,repo.revisions(run.id).size());
    }
    @Test void httpResumeRestoresApprovalStatusWithoutExecuting() throws Exception {
        Run run=executionGate(demo(Scenario.GREENFIELD).id);
        assertEquals(200,request("/api/runs/"+run.id+"/stop","POST",Map.of()).statusCode());
        var response=request("/api/runs/"+run.id+"/resume","POST",Map.of());
        assertEquals(200,response.statusCode(),response.body());
        assertEquals("WAITING_FOR_APPROVAL",json.readTree(response.body()).path("status").asString());
        Run resumed=repo.get(run.id);assertEquals(State.WAITING_FOR_APPROVAL,resumed.task(Stage.VALIDATE).state);
        assertEquals(0,resumed.task(Stage.VALIDATE).attempts);assertEquals("",resumed.executionApproval);
    }
    @Test void resumeManualHandoffPreservesWaitingInputStatus() {
        Run run=engine.create(new Create("Build a URL service",Mode.MANUAL,Scenario.GREENFIELD));
        engine.stop(run.id);run=engine.resume(run.id);
        assertEquals(Status.WAITING_FOR_INPUT,run.status);assertEquals(State.WAITING_FOR_INPUT,run.task(Stage.ANALYZE).state);
        assertEquals(0,run.task(Stage.VALIDATE).attempts);
    }
}
