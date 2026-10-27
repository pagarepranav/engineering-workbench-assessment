package dev.pranav.workbench.workflow;

import dev.pranav.workbench.common.ApiException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.*;
import static dev.pranav.workbench.workflow.Workflow.*;

@Repository
public class WorkflowRepository {
    private final JdbcTemplate db;
    private final ObjectMapper json;
    private final Clock clock;
    public WorkflowRepository(JdbcTemplate db, ObjectMapper json, Clock clock) { this.db=db; this.json=json; this.clock=clock; }
    public Run get(String id) {
        return db.query("SELECT snapshot FROM workflow_runs WHERE id=?", (rs,n)->json.readValue(rs.getString(1),Run.class),id).stream().findFirst().orElseThrow(()->new ApiException(404,"Workflow not found"));
    }
    public List<Run> list() { return db.query("SELECT snapshot FROM workflow_runs ORDER BY created_at DESC",(rs,n)->json.readValue(rs.getString(1),Run.class)); }
    @Transactional public void create(Run run) {
        db.update("INSERT INTO workflow_runs(id,version,snapshot,created_at) VALUES (?,?,?,?)",run.id,run.version,json.writeValueAsString(run),run.createdAt);
        event(run.id,"RUN_CREATED","mode="+run.mode+"; scenario="+run.scenario+"; provider="+run.provider);
    }
    @Transactional public void save(Run run, String type, String detail) {
        long previous=run.version; run.version++;
        int changed=db.update("UPDATE workflow_runs SET version=?,snapshot=? WHERE id=? AND version=?",run.version,json.writeValueAsString(run),run.id,previous);
        ApiException.require(changed==1,409,"Workflow changed; refresh and retry");
        event(run.id,type,detail);
    }
    @Transactional public void archive(Run run) {
        String snapshot=json.writeValueAsString(run);
        if(db.update("UPDATE workflow_revisions SET snapshot=? WHERE run_id=? AND revision=?",snapshot,run.id,run.revision)==0)
            db.update("INSERT INTO workflow_revisions(run_id,revision,snapshot) VALUES (?,?,?)",run.id,run.revision,snapshot);
    }
    public List<Map<String,Object>> revisions(String id) { get(id); return db.query("SELECT revision,snapshot FROM workflow_revisions WHERE run_id=? ORDER BY revision",(rs,n)->Map.of("revision",rs.getInt(1),"run",json.readValue(rs.getString(2),Run.class)),id); }
    private void event(String id, String type, String detail) {
        var last=db.query("SELECT sequence_no,event_hash FROM audit_events WHERE run_id=? ORDER BY sequence_no DESC LIMIT 1",(rs,n)->Map.entry(rs.getLong(1),rs.getString(2)),id);
        long sequence=last.isEmpty()?1:last.get(0).getKey()+1; String prev=last.isEmpty()?"GENESIS":last.get(0).getValue(); String at=clock.instant().toString();
        db.update("INSERT INTO audit_events(run_id,sequence_no,occurred_at,event_type,detail,previous_hash,event_hash) VALUES (?,?,?,?,?,?,?)",id,sequence,at,type,detail,prev,eventHash(id,sequence,at,type,detail,prev));
    }
    public List<Event> events(String id) { get(id); return db.query("SELECT * FROM audit_events WHERE run_id=? ORDER BY sequence_no",(rs,n)->new Event(rs.getLong("sequence_no"),rs.getString("occurred_at"),rs.getString("event_type"),rs.getString("detail"),rs.getString("previous_hash"),rs.getString("event_hash")),id); }
    public boolean verify(String id) {
        String previous="GENESIS"; long seq=0;
        for(Event e:events(id)) { if(e.sequence()!=++seq || !e.previousHash().equals(previous) || !e.hash().equals(eventHash(id,e.sequence(),e.at(),e.type(),e.detail(),previous)))return false; previous=e.hash(); }
        return true;
    }
    private String eventHash(String id,long seq,String at,String type,String detail,String previous) { return hash(json.writeValueAsString(List.of(id,seq,at,type,detail,previous))); }
    public static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    public static String filesHash(Map<String,String> files) {
        StringBuilder canonical=new StringBuilder(); new TreeMap<>(files).forEach((name,content)->canonical.append(name.length()).append(':').append(name).append(content.length()).append(':').append(content));
        return hash(canonical.toString());
    }
}
