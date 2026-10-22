package local.myhome.marketresearch.analysis.discovery;

import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import local.myhome.marketresearch.contracts.analysis.Discovery.*;
import local.myhome.marketresearch.contracts.analysis.Discovery;
import local.myhome.marketresearch.tools.ProviderAccess;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

@Service
@EnableScheduling
public class DiscoveryJobs {
    private final JdbcTemplate db; private final ObjectMapper json; private final ProviderAccess provider;
    private final TransactionTemplate tx; private final String cacheVersion;
    @org.springframework.beans.factory.annotation.Autowired
    public DiscoveryJobs(JdbcTemplate db,ObjectMapper json,Environment env,PlatformTransactionManager manager,ProviderAccess provider) {
        this(db,json,provider,manager,Discovery.VERSION+":"+env.getProperty("research.runtime.model-provider","gemini")
            +":"+env.getProperty("research.models.gemini-model","gemini-3.5-flash-lite")
            +":"+env.getProperty("research.models.open-ai-model","gpt-5.4-mini"));
    }
    DiscoveryJobs(JdbcTemplate db,ObjectMapper json,ProviderAccess provider,PlatformTransactionManager manager,String version) {
        this.db=db;this.json=json;this.provider=provider;this.tx=new TransactionTemplate(manager);this.cacheVersion=version;
        db.execute("CREATE TABLE IF NOT EXISTS discovery_jobs(id VARCHAR(36) PRIMARY KEY, idem VARCHAR(200) UNIQUE, payload CLOB, status VARCHAR(30), phase VARCHAR(30), draft CLOB, result CLOB, error VARCHAR(80), fresh INT DEFAULT 0, cached INT DEFAULT 0)");
        db.execute("CREATE TABLE IF NOT EXISTS discovery_cache(scope VARCHAR(64), listing_id VARCHAR(40), fingerprint VARCHAR(64), PRIMARY KEY(scope,listing_id))");
        db.execute("ALTER TABLE discovery_jobs ADD COLUMN IF NOT EXISTS next_attempt BIGINT DEFAULT 0");
        // A crash during provider I/O is ambiguous: never silently rebill the same operation.
        db.update("UPDATE discovery_jobs SET status='BLOCKED',error='INTERRUPTED_PROVIDER_OPERATION' WHERE status='RUNNING'");
    }
    public synchronized TaskView submit(String key,Task task) {
        require(key!=null&&!key.isBlank()&&key.length()<=200&&task!=null&&Set.of("REFINE","BUSINESS","REASSESS").contains(task.kind())
            &&task.query()!=null&&!task.query().isBlank()&&task.query().length()<=300&&task.listings()!=null
            &&task.listings().size()<=1000&&task.shops()!=null&&task.shops().size()<=50
            &&task.previousFamilies()!=null&&task.previousSummaries()!=null,"Invalid discovery task");
        require(task.listings().stream().allMatch(l->l!=null&&l.id()!=null&&l.id().matches("[0-9]+")&&l.title()!=null),"Invalid listing");
        String payload=json.writeValueAsString(task);
        var old=db.queryForList("SELECT id,payload FROM discovery_jobs WHERE idem=?",key);
        if(!old.isEmpty()) {
            if(!payload.equals(stored(old.getFirst().get("PAYLOAD"))))throw new ResponseStatusException(HttpStatus.CONFLICT);
            return get(UUID.fromString(old.getFirst().get("ID").toString()));
        }
        UUID id=UUID.randomUUID();
        db.update("INSERT INTO discovery_jobs(id,idem,payload,status,phase) VALUES(?,?,?,'ACCEPTED','PREPARE')",id.toString(),key,payload);
        return get(id);
    }
    public TaskView get(UUID id) {
        var rows=db.queryForList("SELECT * FROM discovery_jobs WHERE id=?",id.toString());
        if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        var r=rows.getFirst(); var task=json.readValue(stored(r.get("PAYLOAD")),Task.class);
        String result=stored(r.get("RESULT"));
        return new TaskView(id,task.kind(),r.get("STATUS").toString(),(String)r.get("ERROR"),
            ((Number)r.get("FRESH")).intValue(),((Number)r.get("CACHED")).intValue(),
            result!=null&&task.kind().equals("REFINE")?json.readValue(result,Refinement.class):null,
            result!=null&&!task.kind().equals("REFINE")?json.readValue(result,Assessment.class):null,((Number)r.get("NEXT_ATTEMPT")).longValue());
    }
    public void cancel(UUID id) { get(id);db.update("UPDATE discovery_jobs SET status='CANCELLED' WHERE id=? AND status IN ('ACCEPTED','RUNNING','BLOCKED','WAITING_QUOTA')",id.toString()); }
    public void resume(UUID id) {get(id);db.update("UPDATE discovery_jobs SET status='ACCEPTED',error=NULL WHERE id=? AND status='BLOCKED'",id.toString());}
    @Scheduled(fixedDelayString="${research.discovery.worker-delay-ms:500}")
    public void tick() {
        var jobs=db.queryForList("SELECT id,payload,phase,draft FROM discovery_jobs WHERE status IN ('ACCEPTED','WAITING_QUOTA') AND next_attempt<=? FETCH FIRST 1 ROW ONLY",System.currentTimeMillis());
        if(jobs.isEmpty())return;
        var row=jobs.getFirst();String id=row.get("ID").toString();
        if(db.update("UPDATE discovery_jobs SET status='RUNNING',error=NULL WHERE id=? AND status IN ('ACCEPTED','WAITING_QUOTA')",id)!=1)return;
        try {
            var request=json.readValue(stored(row.get("PAYLOAD")),Task.class);
            if("REFINE".equals(request.kind())) {
                var value=json.convertValue(provider.reason("discovery-refinement",DiscoveryPrompts.REFINE,request),Refinement.class);
                require(value.queries()!=null&&value.queries().size()<=3&&value.queries().stream().allMatch(q->q!=null&&!q.isBlank()&&q.length()<=300)
                    &&value.families()!=null&&value.families().size()<=30&&value.reason()!=null,"Invalid refinement");
                require(request.listings().stream().map(Listing::id).collect(java.util.stream.Collectors.toSet()).containsAll(value.excludedListingIds()),"Unknown exclusion ID");
                finish(id,json.writeValueAsString(value));return;
            }
            boolean reassess="REASSESS".equals(request.kind());
            var fresh=reassess?request.listings():request.listings().stream().filter(l->!cached(request.query(),l)).toList();
            db.update("UPDATE discovery_jobs SET fresh=?,cached=? WHERE id=?",reassess?0:fresh.size(),reassess?fresh.size():request.listings().size()-fresh.size(),id);
            if(fresh.isEmpty()) {finish(id,json.writeValueAsString(new Assessment("No new or meaningfully changed listings",List.of(),List.of(),List.of(),
                false,"All listing semantics in this cohort are already cached for this scope and protocol")));return;}
            var compact=fresh.stream().map(l->new Listing(l.id(),l.shopId(),l.title(),
                reassess?"":l.description()==null?"":l.description().substring(0,Math.min(160,l.description().length())),
                reassess||l.tags()==null?List.<String>of():l.tags().stream().limit(5).toList(),
                l.price(),l.currency(),l.views(),l.favorites(),l.createdAt())).toList();
            var effective=new Task(request.kind(),request.query(),compact,request.shops(),request.previousFamilies(),request.previousSummaries());
            if("PREPARE".equals(row.get("PHASE"))) {
                var draft=json.convertValue(provider.reason(reassess?"discovery-group-reassessment":"discovery-business",
                    DiscoveryPrompts.ANALYZE+(reassess?"\nThis is an explicitly approved GROUP reassessment after a trend alert. Reuse prior cohort conclusions; do not rescan unchanged listings. Compact listings only ground citations and current price/proxy context. Compare the supplied trend evidence and refreshed shop facts.":""),effective),Assessment.class);
                validate(draft,effective);
                db.update("UPDATE discovery_jobs SET draft=?,phase='REVIEW',status='ACCEPTED' WHERE id=? AND status='RUNNING'",json.writeValueAsString(draft),id);
            } else {
                var draft=json.readValue(stored(row.get("DRAFT")),Assessment.class);
                var cited=draft.products().stream().flatMap(p->p.listingIds().stream()).collect(java.util.stream.Collectors.toSet());
                var reviewed=json.convertValue(provider.reason("discovery-review",DiscoveryPrompts.REVIEW,
                    Map.of("cohort",effective,"draft",draft,"citedOriginals",
                        fresh.stream().filter(l->cited.contains(l.id())).toList())),Assessment.class);
                validate(reviewed,effective);
                tx.executeWithoutResult(s->{
                    if(db.update("UPDATE discovery_jobs SET result=?,status='SUCCEEDED' WHERE id=? AND status='RUNNING'",json.writeValueAsString(reviewed),id)==1)
                        for(var l:fresh)db.update("MERGE INTO discovery_cache KEY(scope,listing_id) VALUES(?,?,?)",scope(request.query()),l.id(),fingerprint(l));
                });
            }
        } catch(local.myhome.marketresearch.tools.ProviderDeferred waiting) {
            db.update("UPDATE discovery_jobs SET status='WAITING_QUOTA',error='KNOWN_QUOTA_WAIT',next_attempt=? WHERE id=? AND status='RUNNING'",waiting.retryAt(),id);
        } catch(local.myhome.marketresearch.tools.ProviderBusy waiting) {
            db.update("UPDATE discovery_jobs SET status='ACCEPTED' WHERE id=? AND status='RUNNING'",id);
        } catch(Exception error) {
            db.update("UPDATE discovery_jobs SET status='BLOCKED',error='PROVIDER_OR_VALIDATION_FAILED' WHERE id=? AND status='RUNNING'",id);
        }
    }
    private void finish(String id,String result) {db.update("UPDATE discovery_jobs SET result=?,status='SUCCEEDED' WHERE id=? AND status='RUNNING'",result,id);}
    private String scope(String query){return hash(cacheVersion+":"+query.strip().toLowerCase(Locale.ROOT));}
    private boolean cached(String query,Listing listing) {
        return !db.queryForList("SELECT listing_id FROM discovery_cache WHERE scope=? AND listing_id=? AND fingerprint=?",
            scope(query),listing.id(),fingerprint(listing)).isEmpty();
    }
    public static String fingerprint(Listing l) {
        // Volatile purchase proxies do not cause repeated per-listing semantic scans.
        return hash(l.id()+"|"+l.shopId()+"|"+l.title()+"|"+l.description()+"|"+l.tags()+"|"+l.price()+"|"+l.currency());
    }
    static String hash(String text) {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));}
        catch(Exception e){throw new IllegalStateException(e);}
    }
    static void validate(Assessment a,Task t) {
        var ids=t.listings().stream().map(Listing::id).collect(java.util.stream.Collectors.toSet());
        require(a!=null&&a.summary()!=null&&!a.summary().isBlank()&&a.families()!=null&&a.products()!=null&&a.products().size()<=5
            &&a.limitations()!=null&&a.newInsights()!=null&&text(a.marginalValueReason()),"Invalid assessment");
        for(var p:a.products())require(p!=null&&text(p.name())&&text(p.audience())&&text(p.useCase())
            &&p.deliverables()!=null&&!p.deliverables().isEmpty()&&p.deliverables().stream().allMatch(DiscoveryJobs::text)
            &&text(p.differentiation())&&p.proposedPriceUsd()!=null&&p.proposedPriceUsd().signum()>0
            &&text(p.recurringRevenueHypothesis())&&text(p.automationPlan())&&text(p.manualWorkAssumptions())
            &&text(p.seasonality())&&p.listingIds()!=null&&!p.listingIds().isEmpty()&&p.listingIds().size()<=20&&ids.containsAll(p.listingIds())
            &&p.counterevidence()!=null&&!p.counterevidence().isEmpty()&&p.confidence()!=null&&Set.of("LOW","MEDIUM","HIGH").contains(p.confidence())
            &&text(p.confidenceExplanation())&&text(p.experiment()),"Invalid or ungrounded recommendation");
    }
    static boolean text(String s){return s!=null&&!s.isBlank();}
    static String stored(Object value) {
        if(value==null)return null;
        if(value instanceof java.sql.Clob clob)try{return clob.getSubString(1,Math.toIntExact(clob.length()));}
        catch(java.sql.SQLException e){throw new IllegalStateException(e);}
        return value.toString();
    }
    static void require(boolean valid,String message){if(!valid)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
}
