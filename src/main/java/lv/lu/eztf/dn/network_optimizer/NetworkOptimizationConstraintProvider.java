package lv.lu.eztf.dn.network_optimizer;

import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintCollectors;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.ConstraintProvider;
import ai.timefold.solver.core.api.score.stream.common.ConnectedRange;
import lv.lu.eztf.dn.network_optimizer.domain.*;

import java.math.BigDecimal;
import java.util.Date;

import static ai.timefold.solver.core.api.score.stream.ConstraintCollectors.sum;
import static ai.timefold.solver.core.api.score.stream.Joiners.*;

public class NetworkOptimizationConstraintProvider implements ConstraintProvider {

    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
        return new Constraint[] {
                // Hard constraints
                everyRequestAssigned(factory),
                noRequestMultipleAssignments(factory),
                requestMustHaveService(factory),
                requestMustHaveServer(factory),
                validDateRange(factory),
                enoughCPU(factory),
                enoughMemory(factory),
                enoughStorage(factory),
                // Soft constraints
                serverActiveIntervalsCost(factory)
        };
    }
    // HARD
    Constraint everyRequestAssigned(ConstraintFactory factory) {
        return factory.forEach(Request.class)
                .ifNotExists(Deployment.class,
                        filtering((request, deployment) ->
                                deployment.getRequests().contains(request)
                        )
                )
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Request must be assigned at least once");
    }
    Constraint noRequestMultipleAssignments(ConstraintFactory factory) {
        return factory.forEachUniquePair(
                        Deployment.class,
                        filtering((d1, d2) ->
                                d1 != d2 && d1.getRequests().stream().anyMatch(d2.getRequests()::contains)
                        )
                )
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Request must not be assigned more than once");
    }

    Constraint requestMustHaveService(ConstraintFactory factory) {
        return factory.forEach(Deployment.class)
                .penalize(HardSoftScore.ONE_HARD,
                        deployment -> {
                            if (deployment.getService() == null ||
                                    deployment.getService().getName() == null) {
                                // one penalty per request
                                return deployment.getRequests().size();
                            }
                            return 0;
                        })
                .asConstraint("Assigned request must have a valid service");
    }
    Constraint requestMustHaveServer(ConstraintFactory factory) {
        return factory.forEach(Deployment.class)
                .penalize(HardSoftScore.ONE_HARD,
                        deployment -> {
                            if (deployment.getServer() == null) {
                                // one penalty per request
                                return deployment.getRequests().size();
                            }
                            return 0;
                        })
                .asConstraint("Assigned request must have a valid server");
    }
    Constraint validDateRange(ConstraintFactory factory) {
        return factory.forEach(Deployment.class)
                .filter(d -> !d.hasValidDates())
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Invalid date range");
    }
    Constraint enoughCPU(ConstraintFactory factory) {
        return factory.forEach(Deployment.class)
                // only count real placements
                .filter(d -> d.getServer() != null && d.getService() != null)
                // group by server, sum CPU used by its deployments
                .groupBy(Deployment::getServer,
                        sum(d -> (int) d.getService().getCpuPerInstance()))
                // keep only servers that are over capacity
                .filter((server, usedCpu) -> usedCpu > server.getCpuCores())
                // penalize by the overload amount
                .penalize(HardSoftScore.ONE_HARD,
                        (server, usedCpu) -> usedCpu - server.getCpuCores())
                .asConstraint("CPU capacity exceeded");
    }
    Constraint enoughMemory(ConstraintFactory factory) {
        return factory.forEach(Deployment.class)
                .filter(d -> d.getServer() != null && d.getService() != null)
                .groupBy(Deployment::getServer,
                        sum(d -> (int) d.getService().getRamPerInstance()))
                .filter((server, usedRam) -> usedRam > server.getRamGB())
                .penalize(HardSoftScore.ONE_HARD,
                        (server, usedRam) -> (int) (usedRam - server.getRamGB()))
                .asConstraint("RAM capacity exceeded");
    }
    Constraint enoughStorage(ConstraintFactory factory) {
        return factory.forEach(Deployment.class)
                .filter(d -> d.getServer() != null && d.getService() != null)
                .groupBy(Deployment::getServer,
                        sum(d -> (int) d.getService().getStoragePerInstance()))
                .filter((server, usedStorage) -> usedStorage > server.getStorageGB())
                .penalize(HardSoftScore.ONE_HARD,
                        (server, usedStorage) -> (int) (usedStorage - server.getStorageGB()))
                .asConstraint("Storage capacity exceeded");
    }


    /**
     * Cost per active interval:
     *  - allocation cost when interval starts
     *  - daily * number_of_days
     *  - deallocation when interval ends
     */
    private Constraint serverActiveIntervalsCost(ConstraintFactory factory) {

        return factory.forEach(Deployment.class)
                .filter(Deployment::isActive)
                .filter(Deployment::hasValidDates)

                .groupBy(
                        Deployment::getServer,
                        ConstraintCollectors.toConnectedRanges(
                                Deployment::getDateFrom,
                                Deployment::getDateTo,
                                (a, b) -> b.getTime() - a.getTime()   // MUST return a comparable numeric difference
                        )
                )

                .flattenLast(connectedRangeChain -> connectedRangeChain.getConnectedRanges())

                .penalize(HardSoftScore.ONE_SOFT,
                        (server, range) -> computeServerIntervalCost(server, range))

                .asConstraint("Deployments cost money");
    }

    /**
     * Cost calculation performed inside CS constraint:
     * allocation + daily × days + deallocation
     */
    private int computeServerIntervalCost(Server server, ConnectedRange range) {
        var cost = server.getCost();

        long millis = ((Date)(range.getEnd())).getTime() - ((Date)range.getStart()).getTime();
        long days = Math.max(1, millis / (24L * 60 * 60 * 1000));  // treat same-day as 1 day

        BigDecimal allocation  = cost.getAllocation();
        BigDecimal daily       = cost.getDaily().multiply(BigDecimal.valueOf(days));
        BigDecimal deallocation = cost.getDeallocation();

        BigDecimal total = allocation.add(daily).add(deallocation);

        return total.intValue(); // CS needs an int/long score impact
    }

}
