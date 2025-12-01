package lv.lu.eztf.dn.network_optimizer.domain;

import ai.timefold.solver.core.api.domain.solution.PlanningEntityCollectionProperty;
import ai.timefold.solver.core.api.domain.solution.PlanningScore;
import ai.timefold.solver.core.api.domain.solution.PlanningSolution;
import ai.timefold.solver.core.api.domain.solution.ProblemFactCollectionProperty;
import ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.timefold.solver.core.api.solver.SolverStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@PlanningSolution
@Getter @Setter @NoArgsConstructor
@AllArgsConstructor
public class DeploymentPlan {

    public SolverStatus solverStatus;

    // Value range: Available servers
    @ValueRangeProvider(id = "serverRange")
    private List<Server> serverList = new ArrayList<>();

    // Value range: Available services (instances to deploy)
    @ValueRangeProvider(id = "serviceRange")
    private List<Service> serviceList = new ArrayList<>();

    // Value range: Available dates for deployment windows
    @ValueRangeProvider(id = "dateRange")
    private List<Date> availableDates = new ArrayList<>();

    // Value range: Requests to be assigned to deployments
    @ValueRangeProvider(id = "requestRange")
    @ProblemFactCollectionProperty
    private List<Request> requests = new ArrayList<>();

    // Planning entities: Deployments (placement + timing + request assignments)
    @PlanningEntityCollectionProperty
    private List<Deployment> deployments = new ArrayList<>();

    // Other problem facts
    @ProblemFactCollectionProperty
    private List<InterRegionLatency> latencies = new ArrayList<>();

    @PlanningScore
    private HardSoftScore score;
}


