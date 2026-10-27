package dev.pranav.workbench.links;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.Map;

@RestController
public class LinkController {
    private final LinkService links;
    public LinkController(LinkService links) { this.links=links; }
    @PostMapping("/api/links") ResponseEntity<?> create(@RequestBody LinkService.Create input) { var link=links.create(input); return ResponseEntity.created(URI.create("/api/links/"+link.id())).body(link); }
    @GetMapping("/api/links") Object list(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) { return links.list(page,size); }
    @GetMapping("/api/links/{id}") Object get(@PathVariable String id) { return links.get(id); }
    @PatchMapping("/api/links/{id}") Object update(@PathVariable String id,@RequestBody LinkService.Update input) { return links.update(id,input); }
    @GetMapping("/api/links/{id}/analytics") Object analytics(@PathVariable String id) { return links.analytics(id); }
    @GetMapping("/s/{code}") ResponseEntity<?> redirect(@PathVariable String code) { return ResponseEntity.status(302).header("Location",links.redirect(code)).header("Cache-Control","no-store").build(); }
}
