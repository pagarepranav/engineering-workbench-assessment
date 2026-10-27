package dev.pranav.workbench.workflow;

import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.TreeMap;

/** Authored demonstration material. This class is never described as an AI provider. */
@Component
public class DemoFixtures {
    public Map<String,String> files(boolean expiration) {
        Map<String,String> files=new TreeMap<>();
        files.put("LinkStore.java", """
            import java.net.URI;
            import java.time.*;
            import java.util.*;
            import java.util.concurrent.ConcurrentHashMap;
            import java.util.concurrent.atomic.AtomicLong;
            public class LinkStore {
                private record Link(String url, Instant expires) {}
                private final Map<String,Link> links=new ConcurrentHashMap<>();
                private final Map<String,AtomicLong> clicks=new ConcurrentHashMap<>();
                private final Clock clock;
                public LinkStore(Clock clock) { this.clock=clock; }
                public String create(String url,String alias,Instant expires) {
                    URI u=URI.create(url);
                    if(!(u.getScheme()!=null && Set.of("http","https").contains(u.getScheme().toLowerCase()) && u.getHost()!=null && u.getUserInfo()==null))throw new IllegalArgumentException("Invalid URL");
                    if(alias==null || !alias.matches("[A-Za-z0-9_-]{3,64}"))throw new IllegalArgumentException("Invalid alias");
                    EXPIRY_CREATE
                    if(links.putIfAbsent(alias,new Link(url,expires))!=null)throw new IllegalStateException("Alias exists");
                    return alias;
                }
                public String resolve(String code) {
                    Link link=links.get(code);
                    if(link==null)throw new NoSuchElementException("Unknown link");
                    EXPIRY_RESOLVE
                    clicks.computeIfAbsent(code,k->new AtomicLong()).incrementAndGet();
                    return link.url();
                }
                public long clicks(String code) { return clicks.getOrDefault(code,new AtomicLong()).get(); }
            }
            """.replace("EXPIRY_CREATE",expiration?"if(expires!=null && !expires.isAfter(clock.instant()))throw new IllegalArgumentException(\"Expiration must be future\");":"// Baseline does not enforce expiration")
                 .replace("EXPIRY_RESOLVE",expiration?"if(link.expires()!=null && !link.expires().isAfter(clock.instant()))throw new IllegalStateException(\"Expired\");":"// Baseline does not enforce expiration"));
        files.put("CandidateTest.java","""
            import java.time.*;
            import java.util.*;
            public class CandidateTest {
                private static int checks=0;
                private static void check(boolean condition,String message) { if(!condition)throw new AssertionError(message);checks++; }
                private static void fails(Runnable action,Class<? extends Exception> type) {
                    try { action.run(); throw new AssertionError("Expected "+type.getSimpleName()); }
                    catch(Exception e) { check(type.isInstance(e),"Unexpected exception "+e); }
                }
                public static void main(String[] args) {
                    Instant now=Instant.parse("2026-01-01T00:00:00Z");
                    Clock fixed=Clock.fixed(now,ZoneOffset.UTC);
                    LinkStore store=new LinkStore(fixed);
                    check(store.create("https://example.com/a","demo",null).equals("demo"),"Create alias");
                    check(store.resolve("demo").equals("https://example.com/a"),"Redirect target");
                    check(store.clicks("demo")==1,"Click count");
                    fails(()->store.create("https://example.com","demo",null),IllegalStateException.class);
                    fails(()->store.create("javascript:alert(1)","invalid",null),IllegalArgumentException.class);
                    fails(()->store.resolve("missing"),NoSuchElementException.class);
                    EXPIRY_TEST
                    System.out.println("PASS: "+checks+" candidate assertions");
                }
            }
            """.replace("EXPIRY_TEST",expiration?"""
                    fails(()->store.create("https://example.com","past",now),IllegalArgumentException.class);
                    Clock ticking=new Clock() {
                        int reads=0;
                        public ZoneId getZone(){return ZoneOffset.UTC;}
                        public Clock withZone(ZoneId zone){return this;}
                        public Instant instant(){return reads++==0?now:now.plusSeconds(2);}
                    };
                    LinkStore expiring=new LinkStore(ticking);
                    expiring.create("https://example.com","expires",now.plusSeconds(1));
                    fails(()->expiring.resolve("expires"),IllegalStateException.class);
                    check(expiring.clicks("expires")==0,"Expired clicks not counted");
                    """:"// Greenfield baseline covers basic shortening and analytics"));
        files.put("Server.java","""
            import com.sun.net.httpserver.*;
            import java.net.*;
            import java.nio.charset.StandardCharsets;
            import java.time.*;
            import java.util.*;
            public class Server {
                public static void main(String[] args) throws Exception {
                    LinkStore store=new LinkStore(Clock.systemUTC());
                    HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",9090),0);
                    server.createContext("/",exchange->{
                        try {
                            String path=exchange.getRequestURI().getPath();
                            if(path.startsWith("/s/") && exchange.getRequestMethod().equals("GET")) {
                                exchange.getResponseHeaders().set("Location",store.resolve(path.substring(3)));
                                exchange.getResponseHeaders().set("Cache-Control","no-store");
                                exchange.sendResponseHeaders(302,-1); exchange.close();return;
                            }
                            if(path.equals("/links") && exchange.getRequestMethod().equals("POST")) {
                                Map<String,String> query=new HashMap<>();
                                for(String part:Optional.ofNullable(exchange.getRequestURI().getRawQuery()).orElse("").split("&")) {
                                    String[] pair=part.split("=",2);if(pair.length==2)query.put(pair[0],URLDecoder.decode(pair[1],StandardCharsets.UTF_8));
                                }
                                Instant expires=query.containsKey("expires")?Instant.parse(query.get("expires")):null;
                                String code=store.create(query.getOrDefault("url",""),query.getOrDefault("alias",UUID.randomUUID().toString().substring(0,8)),expires);
                                byte[] body=("http://127.0.0.1:9090/s/"+code).getBytes(StandardCharsets.UTF_8);
                                exchange.sendResponseHeaders(201,body.length);exchange.getResponseBody().write(body);exchange.close();return;
                            }
                            byte[] body="Candidate URL service: POST /links?url=https%3A%2F%2Fexample.com&alias=demo then GET /s/demo".getBytes(StandardCharsets.UTF_8);
                            exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);
                        } catch(NoSuchElementException e) {exchange.sendResponseHeaders(404,-1);}
                          catch(IllegalStateException e) {exchange.sendResponseHeaders(410,-1);}
                          catch(IllegalArgumentException e) {exchange.sendResponseHeaders(400,-1);}
                        finally {exchange.close();}
                    });
                    server.start();System.out.println("Candidate server at http://127.0.0.1:9090");
                }
            }
            """);
        files.put("README.md","# Demonstration candidate\n\nAuthored fixture, not generated by an LLM. JDK-only URL service. In-memory storage.\n\nCompile: javac -d classes LinkStore.java CandidateTest.java Server.java\nTest: java -cp classes CandidateTest\nRun: java -cp classes Server\n\nExpiration enforcement: "+expiration+"\nThe primary Spring Boot application supplies persistent storage and richer analytics.\n");
        return files;
    }
}
