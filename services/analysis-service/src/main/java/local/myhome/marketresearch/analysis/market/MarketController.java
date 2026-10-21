package local.myhome.marketresearch.analysis.market;
import local.myhome.marketresearch.contracts.analysis.MarketResearch.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/v1")
public class MarketController {
    @PostMapping("/refinement-plans") public RefinementPlan refine(@RequestBody RefinementRequest request) {
        try { return SearchRefiner.refine(request); }
        catch (IllegalArgumentException e) {throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid refinement evidence");}
    }
    @PostMapping("/business-analyses") public BusinessReport analyze(@RequestBody BusinessRequest request) {
        if(request.listings()==null || request.listings().size()>1000 || request.groups()==null || request.groups().size()>10)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Evidence budget exceeded");
        try { return BusinessAnalyzer.analyze(request); }
        catch(IllegalArgumentException e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid business evidence");}
    }
}
