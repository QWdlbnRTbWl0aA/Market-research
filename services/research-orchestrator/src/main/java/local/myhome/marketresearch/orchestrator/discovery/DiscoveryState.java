package local.myhome.marketresearch.orchestrator.discovery;

import java.util.*;
import local.myhome.marketresearch.contracts.analysis.Discovery.*;
import local.myhome.marketresearch.contracts.content.ProductDefinition;

/** One durable aggregate per broad query. External side effects use stable idempotency keys. */
public class DiscoveryState {
    public UUID id;
    public String query,status="ACTIVE",searchStatus="ACTIVE",error,blockedFrom="ACTIVE";
    public Map<String,Integer> retryCounts=new LinkedHashMap<>();
    public Map<String,Long> retryAt=new LinkedHashMap<>();
    public long nextWatch;
    public long nextSearchAttempt;
    public int iteration=1;
    public int stableAnalyses;
    public List<Query> queries=new ArrayList<>();
    public Map<String,Listing> listings=new LinkedHashMap<>();
    public Set<String> processed=new LinkedHashSet<>(),families=new LinkedHashSet<>();
    public Set<String> excluded=new LinkedHashSet<>();
    public List<Batch> batches=new ArrayList<>();
    public List<Assessment> assessments=new ArrayList<>();
    public List<Handoff> handoffs=new ArrayList<>();
    public Map<String,Map<String,Object>> shops=new LinkedHashMap<>();
    public Map<String,Long> shopChecked=new LinkedHashMap<>();
    public boolean archived,notified;
    public boolean reassessmentPending;
    public String finishedAt;
    public int watchIndex;
    public List<Map<String,Object>> changes=new ArrayList<>();
    public DiscoveryState() {}
    public static class Query {
        public String text,parent,status="OPEN",stopReason;
        public int depth,pages,lowNovelty,total,returned,usable,novel;
        public double parentYield;
        public boolean refined,successful;
        public UUID refinementJob;
        public Task refinementRequest;
        public List<Listing> sample=new ArrayList<>();
        public Map<String,Object> baseline=new LinkedHashMap<>(),latest=new LinkedHashMap<>();
        public List<String> families=new ArrayList<>();
        public String workerStatus;
        public long nextAttemptAt;
        public Query() {}
        Query(String text,Query parent) {
            this.text=text;this.parent=parent==null?null:parent.text;depth=parent==null?0:parent.depth+1;
            parentYield=parent==null?0:yieldOf(parent);
        }
        public double usableYield(){return returned==0?0:(double)usable/returned;}
        public static double yieldOf(Query q){return q.usableYield();}
    }
    public static class Batch {
        public String key;
        public UUID job;
        public List<String> ids=new ArrayList<>();
        public Task request;
        public String status="PENDING";
        public long nextAttemptAt;
        public Batch() {}
    }
    public static class Handoff {
        public String key,status="PENDING";
        public ProductDefinition.Request request;
        public UUID job;
        public long nextAttemptAt;
        public ProductDefinition.Brief brief;
        public Handoff() {}
    }
}
