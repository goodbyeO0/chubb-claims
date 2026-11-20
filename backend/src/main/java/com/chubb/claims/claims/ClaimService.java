package com.chubb.claims.claims;

import com.chubb.claims.auth.Identity;
import com.chubb.claims.api.ApiException;
import com.chubb.claims.persistence.Claim;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ClaimService {
    private final ClaimRepository repository;
    private final ClaimViews views;
    private final ClaimAudit audit;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    public ClaimService(ClaimRepository repository, ClaimViews views, ClaimAudit audit, JdbcTemplate jdbc, Clock clock) {
        this.repository=repository; this.views=views; this.audit=audit; this.jdbc=jdbc; this.clock=clock;
    }
    private static final Map<Commands.Market,String> ZONES = Map.of(Commands.Market.MY,"Asia/Kuala_Lumpur",Commands.Market.SG,"Asia/Singapore",Commands.Market.TH,"Asia/Bangkok",Commands.Market.ID,"Asia/Jakarta",Commands.Market.PH,"Asia/Manila",Commands.Market.HK,"Asia/Hong_Kong");
    private static final Map<Commands.Market,String> CURRENCIES = Map.of(Commands.Market.MY,"MYR",Commands.Market.SG,"SGD",Commands.Market.TH,"THB",Commands.Market.ID,"IDR",Commands.Market.PH,"PHP",Commands.Market.HK,"HKD");
    public Map<String,Object> submit(Identity actor, Commands.Submit input) {
        if (input.incidentOn().isAfter(LocalDate.now(clock.withZone(ZoneId.of(ZONES.get(input.market())))))) throw ApiException.invalid();
        UUID id=UUID.randomUUID();
        // Explicit insertion avoids Hibernate's primitive @Version default of zero.
        jdbc.update("insert into claim(id,claimant_id,kind,market,currency,incident_on,description,status,version,submitted_at) values (?,?,?,?,?,?,?,'SUBMITTED',1,?)",
            id,actor.id(),input.kind().name(),input.market().name(),CURRENCIES.get(input.market()),input.incidentOn(),input.description(),java.sql.Timestamp.from(clock.instant()));
        Claim claim=repository.get(id);
        audit.append(claim,actor.id(),"CLAIM_SUBMITTED","Claim received.",Map.of(),true);
        return views.view(claim,false);
    }
    @Transactional(readOnly=true) public List<Map<String,Object>> mine(Identity actor) {
        return repository.owned(actor.id()).stream().map(c -> views.view(c,false)).toList();
    }
    @Transactional(readOnly=true) public Map<String,Object> own(Identity actor, UUID id) {
        return views.view(owned(actor,id),false);
    }
    private Claim owned(Identity actor,UUID id) {
        Claim claim=repository.get(id);
        if (!claim.claimantId.equals(actor.id())) throw ApiException.missing();
        return claim;
    }
}
