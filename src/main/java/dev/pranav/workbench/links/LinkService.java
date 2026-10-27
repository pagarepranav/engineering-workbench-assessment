package dev.pranav.workbench.links;

import dev.pranav.workbench.common.ApiException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.net.URI;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;

@Service
public class LinkService {
    public record Link(String id, String code, String destination, String createdAt, String expiresAt, boolean active) {}
    public record Create(String destination, String alias, Instant expiresAt) {}
    public record Update(Boolean active, Instant expiresAt, Boolean clearExpiration) {}
    private final JdbcTemplate db;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private static final char[] ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();
    private static final Set<String> RESERVED = Set.of("api", "admin", "health", "assets", "index", "favicon");
    public LinkService(JdbcTemplate db, Clock clock) { this.db = db; this.clock = clock; }
    public Link create(Create input) {
        ApiException.require(input != null, 400, "Request required");
        validateDestination(input.destination()); validateExpiry(input.expiresAt());
        boolean custom = input.alias() != null && !input.alias().isBlank();
        if (custom) ApiException.require(input.alias().matches("[A-Za-z0-9_-]{3,64}") && !RESERVED.contains(input.alias().toLowerCase(Locale.ROOT)), 400, "Alias must contain 3-64 letters, digits, underscores or hyphens and must not be reserved");
        for (int attempt = 0; attempt < 5; attempt++) {
            String code = custom ? input.alias() : randomCode();
            Link link = new Link(UUID.randomUUID().toString(), code, input.destination(), clock.instant().toString(), input.expiresAt() == null ? null : input.expiresAt().toString(), true);
            try {
                db.update("INSERT INTO links(id,code,destination,created_at,expires_at,active) VALUES (?,?,?,?,?,?)", link.id(), link.code(), link.destination(), link.createdAt(), link.expiresAt(), link.active());
                return link;
            } catch (DuplicateKeyException e) { if (custom) throw new ApiException(409, "Alias already exists"); }
        }
        throw new ApiException(503, "Unable to allocate a code; retry later");
    }
    public Link get(String id) {
        return db.query("SELECT * FROM links WHERE id=?", (rs, n) -> new Link(rs.getString("id"), rs.getString("code"), rs.getString("destination"), rs.getString("created_at"), rs.getString("expires_at"), rs.getBoolean("active")), id).stream().findFirst().orElseThrow(() -> new ApiException(404, "Link not found"));
    }
    public Map<String,Object> list(int page, int size) {
        ApiException.require(page >= 0 && page <= 100000 && size >= 1 && size <= 100, 400, "Invalid pagination");
        var rows = db.query("SELECT * FROM links ORDER BY created_at DESC,id LIMIT ? OFFSET ?", (rs,n) -> new Link(rs.getString("id"),rs.getString("code"),rs.getString("destination"),rs.getString("created_at"),rs.getString("expires_at"),rs.getBoolean("active")),size,page*size);
        return Map.of("items", rows, "page", page, "size", size, "total", db.queryForObject("SELECT COUNT(*) FROM links", Long.class));
    }
    @Transactional public Link update(String id, Update input) {
        ApiException.require(input != null,400,"Request required");
        boolean clearExpiration=Boolean.TRUE.equals(input.clearExpiration());
        ApiException.require(!(clearExpiration && input.expiresAt()!=null),400,"Choose expiration or clearExpiration");
        validateExpiry(input.expiresAt());
        Link old = get(id);
        db.update("UPDATE links SET active=COALESCE(?,active) WHERE id=?", input.active(), id);
        if (clearExpiration || input.expiresAt()!=null) db.update("UPDATE links SET expires_at=? WHERE id=?", input.expiresAt()==null ? null : input.expiresAt().toString(),id);
        return get(id);
    }
    @Transactional public String redirect(String code) {
        List<Link> rows=db.query("SELECT * FROM links WHERE code=?",(rs,n)->new Link(rs.getString("id"),rs.getString("code"),rs.getString("destination"),rs.getString("created_at"),rs.getString("expires_at"),rs.getBoolean("active")),code);
        ApiException.require(!rows.isEmpty(),404,"Short URL not found");
        Link link=rows.get(0); Instant now=clock.instant();
        ApiException.require(link.active() && (link.expiresAt()==null || Instant.parse(link.expiresAt()).isAfter(now)),410,"Link is expired or disabled");
        db.update("INSERT INTO click_events(id,link_id,clicked_at,click_day) VALUES (?,?,?,?)",UUID.randomUUID().toString(),link.id(),now.toString(),now.atOffset(ZoneOffset.UTC).toLocalDate().toString());
        return link.destination();
    }
    public Map<String,Object> analytics(String id) {
        get(id);
        return Map.of("linkId",id,"timezone","UTC","totalClicks",db.queryForObject("SELECT COUNT(*) FROM click_events WHERE link_id=?",Long.class,id),"daily",db.query("SELECT click_day,COUNT(*) AS clicks FROM click_events WHERE link_id=? GROUP BY click_day ORDER BY click_day",(rs,n)->Map.of("date",rs.getString("click_day"),"clicks",rs.getLong("clicks")),id));
    }
    private void validateExpiry(Instant expiry) { ApiException.require(expiry==null || expiry.isAfter(clock.instant()),400,"Expiration must be in the future"); }
    static void validateDestination(String destination) {
        ApiException.require(destination!=null && destination.length()<=4096 && !destination.chars().anyMatch(c->c<=32 || c==127),400,"A valid HTTP/HTTPS destination is required (maximum 4096 characters)");
        try { URI uri=URI.create(destination); ApiException.require(Set.of("http","https").contains(uri.getScheme()==null?"":uri.getScheme().toLowerCase(Locale.ROOT)) && uri.getHost()!=null && uri.getUserInfo()==null,400,"Use an absolute HTTP/HTTPS URL without embedded credentials"); }
        catch (IllegalArgumentException e) { throw new ApiException(400,"Invalid destination URL"); }
    }
    private String randomCode() { StringBuilder s=new StringBuilder(); for(int i=0;i<8;i++) s.append(ALPHABET[random.nextInt(ALPHABET.length)]); return s.toString(); }
}
