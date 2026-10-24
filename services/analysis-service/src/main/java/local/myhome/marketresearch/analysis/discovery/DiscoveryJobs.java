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
    private final OpportunityCatalog opportunities;
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
        db.execute("ALTER TABLE discovery_jobs ADD COLUMN IF NOT EXISTS call_inputs CLOB");
        db.execute("ALTER TABLE discovery_jobs ADD COLUMN IF NOT EXISTS usage_json CLOB");
        db.execute("ALTER TABLE discovery_jobs ADD COLUMN IF NOT EXISTS call_outputs CLOB");
        db.execute("ALTER TABLE discovery_jobs ADD COLUMN IF NOT EXISTS failure_detail VARCHAR(300)");
        db.execute("CREATE TABLE IF NOT EXISTS discovery_refinement_cache(cache_key VARCHAR(64) PRIMARY KEY, result CLOB)");
        opportunities=new OpportunityCatalog(db,json);
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
                String cacheKey=hash(cacheVersion+json.writeValueAsString(request));
                var cached=db.queryForList("SELECT result FROM discovery_refinement_cache WHERE cache_key=?",cacheKey);
                if(!cached.isEmpty()) {finish(id,stored(cached.getFirst().get("RESULT")));return;}
                var value=json.convertValue(call(id,"discovery-refinement",DiscoveryPrompts.REFINE,request),Refinement.class);
                require(value.queries()!=null&&value.queries().size()<=3&&value.queries().stream().allMatch(q->q!=null&&!q.isBlank()&&q.length()<=300)
                    &&value.families()!=null&&value.families().size()<=30&&value.reason()!=null,"Invalid refinement");
                require(request.listings().stream().map(Listing::id).collect(java.util.stream.Collectors.toSet()).containsAll(value.excludedListingIds()),"Unknown exclusion ID");
                String result=json.writeValueAsString(value);
                db.update("MERGE INTO discovery_refinement_cache KEY(cache_key) VALUES(?,?)",cacheKey,result);
                finish(id,result);return;
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
            var effective=new Task(request.kind(),request.query(),compact,request.shops(),request.previousFamilies(),
                request.previousSummaries(),request.branch(),request.reviews(),opportunities.list(request.query()).stream().limit(100).toList());
            if("PREPARE".equals(row.get("PHASE"))) {
                var draft=json.convertValue(call(id,reassess?"discovery-group-reassessment":"discovery-business",
                    DiscoveryPrompts.ANALYZE+(reassess?"\nThis is an explicitly approved GROUP reassessment after a trend alert. Reuse prior cohort conclusions; do not rescan unchanged listings. Compact listings only ground citations and current price/proxy context. Compare the supplied trend evidence and refreshed shop facts.":""),effective),Assessment.class);
                validate(draft,effective);
                db.update("UPDATE discovery_jobs SET draft=?,phase='REVIEW',status='ACCEPTED' WHERE id=? AND status='RUNNING'",json.writeValueAsString(draft),id);
            } else {
                var draft=json.readValue(stored(row.get("DRAFT")),Assessment.class);
                var cited=draft.products().stream().flatMap(p->p.listingIds().stream()).collect(java.util.stream.Collectors.toSet());
                var reviewed=json.convertValue(call(id,"discovery-review",DiscoveryPrompts.REVIEW,
                    Map.of("cohort",effective,"draft",draft,"citedOriginals",
                        fresh.stream().filter(l->cited.contains(l.id())).toList())),Assessment.class);
                validate(reviewed,effective);
                tx.executeWithoutResult(s->{
                    if(!"RUNNING".equals(db.queryForObject("SELECT status FROM discovery_jobs WHERE id=? FOR UPDATE",String.class,id)))return;
                    var resolved=opportunities.accept(id,request,reviewed);
                    if(db.update("UPDATE discovery_jobs SET result=?,status='SUCCEEDED' WHERE id=? AND status='RUNNING'",json.writeValueAsString(resolved),id)==1)
                        for(var l:fresh)db.update("MERGE INTO discovery_cache KEY(scope,listing_id) VALUES(?,?,?)",scope(request.query()),l.id(),fingerprint(l));
                });
            }
        } catch(local.myhome.marketresearch.tools.ProviderDeferred waiting) {
            db.update("UPDATE discovery_jobs SET status='WAITING_QUOTA',error='KNOWN_QUOTA_WAIT',next_attempt=? WHERE id=? AND status='RUNNING'",waiting.retryAt(),id);
        } catch(local.myhome.marketresearch.tools.ProviderBusy waiting) {
            db.update("UPDATE discovery_jobs SET status='ACCEPTED' WHERE id=? AND status='RUNNING'",id);
        } catch(Exception error) {
            String detail=error instanceof ResponseStatusException rejection?rejection.getReason():error.getClass().getSimpleName();
            db.update("UPDATE discovery_jobs SET status='BLOCKED',error='PROVIDER_OR_VALIDATION_FAILED',failure_detail=? WHERE id=? AND status='RUNNING'",detail,id);
        }
    }
    private void finish(String id,String result) {db.update("UPDATE discovery_jobs SET result=?,status='SUCCEEDED' WHERE id=? AND status='RUNNING'",result,id);}
    private Map<String,Object> call(String jobId,String stage,String prompt,Object evidence) throws Exception {
        prompt = recoveryInstructions(jobId, stage, prompt, evidence);
        var existing=db.queryForObject("SELECT call_inputs FROM discovery_jobs WHERE id=?",String.class,jobId);
        Map<String,Object> inputs=existing==null?new LinkedHashMap<>():json.readValue(existing,Map.class);
        inputs.put(stage,Map.of("instructions",prompt,"evidence",evidence,"modelContext",cacheVersion));
        db.update("UPDATE discovery_jobs SET call_inputs=? WHERE id=?",json.writeValueAsString(inputs),jobId);
        try {
            var answer=provider.reason(stage,prompt,evidence);
            String previous=db.queryForObject("SELECT call_outputs FROM discovery_jobs WHERE id=?",String.class,jobId);
            List<Object> outputs=previous==null?new ArrayList<>():json.readValue(previous,List.class);
            outputs.add(Map.of("stage",stage,"at",java.time.Instant.now().toString(),"answer",answer));
            db.update("UPDATE discovery_jobs SET call_outputs=? WHERE id=?",json.writeValueAsString(outputs),jobId);
            return answer;
        }
        finally {
            var usage=provider.lastUsage();
            if(usage!=null) {
                String previous=db.queryForObject("SELECT usage_json FROM discovery_jobs WHERE id=?",String.class,jobId);
                List<Object> records=previous==null?new ArrayList<>():json.readValue(previous,List.class);
                records.add(usage);db.update("UPDATE discovery_jobs SET usage_json=? WHERE id=?",json.writeValueAsString(records),jobId);
            }
        }
    }
    /** An explicit resume retries the saved phase, with feedback rather than an identical blind request. */
    private String recoveryInstructions(String jobId, String stage, String prompt, Object evidence) {
        var row = db.queryForMap("SELECT failure_detail,call_outputs FROM discovery_jobs WHERE id=?", jobId);
        String failure = stored(row.get("FAILURE_DETAIL"));
        if (!Set.of("Invalid assessment", "Invalid or ungrounded recommendation").contains(failure == null ? "" : failure)
                || row.get("CALL_OUTPUTS") == null) return prompt;
        var outputs = json.readValue(stored(row.get("CALL_OUTPUTS")), List.class);
        if (outputs.isEmpty()) return prompt;
        var rejected = (Map<?, ?>) outputs.getLast();
        if (!stage.equals(rejected.get("stage"))) return prompt;
        Task cohort = evidence instanceof Task task ? task
            : evidence instanceof Map<?, ?> context && context.get("cohort") instanceof Task task ? task : null;
        if (cohort == null) return prompt;
        var feedback = new LinkedHashMap<String, Object>();
        feedback.put("failure", failure);
        feedback.put("allowedListingIds", cohort.listings().stream().map(Listing::id).toList());
        feedback.put("allowedExistingOpportunityIds", cohort.knownOpportunities().stream().map(o -> o.id()).toList());
        var referenceErrors = new ArrayList<Map<String, Object>>();
        var answer = json.convertValue(rejected.get("answer"), Assessment.class);
        if (answer.products() != null) for (var product : answer.products()) {
            if (product == null) continue;
            var allowedListings = cohort.listings().stream().map(Listing::id).toList();
            var unknownListings = product.listingIds() == null ? List.of()
                : product.listingIds().stream().filter(id -> !allowedListings.contains(id)).toList();
            boolean unknownOpportunity = !"NEW".equals(product.relationship())
                && cohort.knownOpportunities().stream().noneMatch(o -> o.id().equals(product.existingOpportunityId()));
            boolean unexpectedOpportunity = "NEW".equals(product.relationship()) && product.existingOpportunityId() != null;
            if (!unknownListings.isEmpty() || unknownOpportunity || unexpectedOpportunity) {
                var error = new LinkedHashMap<String, Object>();
                error.put("product", product.name());
                error.put("unknownListingIds", unknownListings);
                error.put("invalidExistingOpportunityId", unknownOpportunity || unexpectedOpportunity ? product.existingOpportunityId() : null);
                referenceErrors.add(error);
            }
        }
        feedback.put("referenceErrors", referenceErrors);
        return prompt + "\nRECOVERY: Your previous response failed local validation. Correct the errors below using only the supplied evidence. "
            + "Copy reference IDs exactly from the allowlists; do not guess, repair digits, or substitute unrelated sources. "
            + "Drop a recommendation if its business claim cannot be supported by an allowed source. Do not relabel an existing opportunity as NEW to evade a bad reference. "
            + "Preserve the assessment schema and required nonempty fields. This feedback is validation data, not new business evidence.\n"
            + json.writeValueAsString(feedback);
    }
    public Map<String,Object> evidence(UUID id) {
        var view=get(id);
        var row=db.queryForMap("SELECT payload,call_inputs,draft,usage_json,call_outputs,failure_detail FROM discovery_jobs WHERE id=?",id.toString());
        var task=json.readValue(stored(row.get("PAYLOAD")),Task.class);
        var evidence=new LinkedHashMap<String,Object>();
        evidence.put("request",task);evidence.put("result",view);
        evidence.put("calls",row.get("CALL_INPUTS")==null?Map.of():json.readValue(stored(row.get("CALL_INPUTS")),Map.class));
        evidence.put("draft",row.get("DRAFT")==null?null:json.readValue(stored(row.get("DRAFT")),Map.class));
        evidence.put("usage",row.get("USAGE_JSON")==null?List.of():json.readValue(stored(row.get("USAGE_JSON")),List.class));
        evidence.put("outputs",row.get("CALL_OUTPUTS")==null?List.of():json.readValue(stored(row.get("CALL_OUTPUTS")),List.class));
        evidence.put("failureDetail",row.get("FAILURE_DETAIL"));
        if(view.assessment()!=null)evidence.put("opportunitySupport",opportunities.supportingEvidence(task,view.assessment()));
        return evidence;
    }
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
            &&text(p.confidenceExplanation())&&text(p.experiment())&&p.relationship()!=null
            &&Set.of("NEW","MEANINGFUL_VARIANT","ADDITIONAL_EVIDENCE").contains(p.relationship())&&text(p.changeReason())
            &&("NEW".equals(p.relationship())?p.existingOpportunityId()==null:
                t.knownOpportunities().stream().anyMatch(o->o.id().equals(p.existingOpportunityId()))),"Invalid or ungrounded recommendation");
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
