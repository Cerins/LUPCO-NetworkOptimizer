package lv.lu.eztf.dn.network_optimizer.domain;

import ai.timefold.solver.core.api.domain.solution.PlanningEntityCollectionProperty;
import ai.timefold.solver.core.api.domain.solution.PlanningScore;
import ai.timefold.solver.core.api.domain.solution.PlanningSolution;
import ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@PlanningSolution
@Getter @Setter
public class DeploymentPlan {

    private List<Server> serverList = new ArrayList<>();

    @ValueRangeProvider(id = "serverRange")
    public List<Server> getServerList() {
        return serverList;
    }

    @ValueRangeProvider(id = "requestRange")
    private List<Request> requests = new ArrayList<>();

    @ValueRangeProvider(id = "servicePlacementRange")
    private List<ServicePlacement> placements = new ArrayList<>();

    @PlanningEntityCollectionProperty
    private List<Deployment> deployments = new ArrayList<>();

    @PlanningScore
    private HardSoftScore score;
}


