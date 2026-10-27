package dev.pranav.workbench.common;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
public class HealthController {
    private final JdbcTemplate db;
    public HealthController(JdbcTemplate db) {this.db=db;}
    @GetMapping("/api/health") Object health(){db.queryForObject("SELECT 1",Integer.class);return Map.of("status","UP","mode","LOCAL_SINGLE_USER","aiAccess","MANUAL_HANDOFF_NO_API_KEY","java",System.getProperty("java.version"));}
}
