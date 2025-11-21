package lv.lu.eztf.dn.network_optimizer.domain;

import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.variable.PlanningVariable;
import lombok.*;

import java.util.Date;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@PlanningEntity
public class Deployment {

    int id;

    // Planning variables
    @PlanningVariable(valueRangeProviderRefs = {"requestRange"})
    Request request;

    @PlanningVariable(valueRangeProviderRefs = {"servicePlacementRange"})
    ServicePlacement servicePlacement;

    // Fixed
    Date dateFrom;
    Date dateTo;
}
