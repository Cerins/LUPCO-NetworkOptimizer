package lv.lu.eztf.dn.network_optimizer.domain;

import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.variable.PlanningVariable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@PlanningEntity
public class ServicePlacement {
    int id;

    //Service assigned to server
    @PlanningVariable(valueRangeProviderRefs = {"serverRange"})
    Server server;

    //Fixed
    Service service;
}
