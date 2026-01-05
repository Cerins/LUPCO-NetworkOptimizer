package lv.lu.eztf.dn.network_optimizer;

import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.timefold.solver.core.api.score.stream.*;
import ai.timefold.solver.core.api.score.stream.common.ConnectedRange;
import ai.timefold.solver.core.api.score.stream.uni.UniConstraintStream;
import lv.lu.eztf.dn.network_optimizer.domain.*;

import java.math.BigDecimal;
import java.util.*;
import static ai.timefold.solver.core.api.score.stream.ConstraintCollectors.sum;
import static ai.timefold.solver.core.api.score.stream.Joiners.*;


public class NetworkOptimizationConstraintProvider implements ConstraintProvider {

    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
        return new Constraint[] {
                // Hard constraints
                everyRequestAssigned(factory),
                noRequestMultipleAssignments(factory),
                requestServiceMustMatchDeploymentService(factory),
                requestMustHaveServer(factory),
                validDateRange(factory),
                enoughCPU(factory),
                enoughMemory(factory),
                enoughStorage(factory),
                // allRequestsAssigned(factory),
                allDependenciesAssigned(factory),
                allRequestsProcessed(factory),
                serviceOnMultipleServersAtSameTime(factory),
                // Soft constraints
                serverActiveIntervalsCost(factory),
                latencyViolation(factory),
                //rewardFastLatency(factory)
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


    private Constraint requestServiceMustMatchDeploymentService(ConstraintFactory factory) {
        return factory.forEach(Request.class)
                .join(Deployment.class,
                        filtering((request, deployment) ->
                                deployment.getRequests() != null
                                        && deployment.getRequests().contains(request)))
                .filter((request, deployment) ->
                        deployment.getService() == null
                                || deployment.getService().getName() == null
                                || !request.getServiceName().equals(deployment.getService().getName()))
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Request must match deployment service");
    }

    Constraint requestMustHaveServer(ConstraintFactory factory) {
        return factory.forEach(Deployment.class)
                .map((deployment)->{
                    if (deployment.getServer() == null) {
                        // one penalty per request
                        return deployment.getRequests().size();
                    }
                    return 0;
                })
                .filter((val)->val > 0)
                .penalize(HardSoftScore.ONE_HARD,
                        val -> {
                            return val;
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
     * allocation + daily × days + deallocation/
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

    private Constraint allRequestsProcessed(ConstraintFactory factory) {
        return factory.forEach(Deployment.class)
                // depA
                .filter(depA ->
                        depA.getService() != null &&
                                depA.getRequests() != null &&
                                depA.getServer() != null
                )

                // (depA, req)
                .join(Request.class,
                        Joiners.filtering((parent, child) -> parent.getRequests().contains(child)))
                .filter((depA, req) ->
                        depA.getDateFrom() != null &&
                                depA.getDateTo() != null &&
                                !req.getDate().before(depA.getDateFrom()) &&
                                !req.getDate().after(depA.getDateTo())
                )

                // Expand to "services that must process the load":
                // A itself + all dependencies of A
                // (depA, req, serviceX)
                .join(Service.class, filtering(
                        (depA, req, serviceX) -> depA.getImpactedServices().contains(serviceX)

                ))
                // Find the deployment depX of serviceX on the same server
                // (depA, req, serviceX, depX)
                .join(
                        Deployment.class,
                        equal(
                                (depA, req, serviceX) -> serviceX,
                                Deployment::getService
                        ),
                        equal(
                                (depA, req, serviceX) -> depA.getServer(),
                                Deployment::getServer
                        ),
                        filtering((depA, req, serviceX, depX) ->
                                depX.getDateFrom() != null &&
                                        depX.getDateTo() != null &&
                                        !req.getDate().before(depX.getDateFrom()) &&
                                        !req.getDate().after(depX.getDateTo())
                        )
                )

                // Aggregate demand per actual deployment + time
                .groupBy(
                        (depA, req, serviceX, depX) -> depX,
                        (depA, req, serviceX, depX) -> req.getDate(),
                        ConstraintCollectors.sum(
                                (depA, req, serviceX, depX) -> req.getEstimatedQueryCount()
                        )
                )

                // Capacity check
                .filter((depX, date, demand) ->
                        demand > depX.getService().maxRequests()
                )
                .penalize(
                        HardSoftScore.ONE_HARD,
                        (depX, date, demand) ->
                                demand - depX.getService().maxRequests()
                )
                .asConstraint("Service capacity violated during request moments");
    }

    private Constraint allDependenciesAssigned(ConstraintFactory factory) {
        return factory.forEach(Deployment.class)
                .map(Deployment::getService)
                .filter(Objects::nonNull)
                .flattenLast(Service::totalContainedServices)
                .ifNotExists(Deployment.class,
                        filtering((Service service, Deployment dep) ->
                                dep.getService() == service))
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Dependencies must be assigned");
    }

    private Constraint serviceOnMultipleServersAtSameTime(ConstraintFactory factory) {
        return factory.forEachUniquePair(Deployment.class,

                        // Same service
                        equal(d -> d.getService() == null ? null : d.getService().getName(),
                                d -> d.getService() == null ? null : d.getService().getName()),

                        // Overlapping intervals
                        overlapping(Deployment::getDateFrom, Deployment::getDateTo),

                        // Different servers
                        filtering((d1, d2) ->
                                d1.getServer() != null &&
                                        d2.getServer() != null &&
                                        d1.getServer().getId() != d2.getServer().getId())
                )

                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Service duplicated on multiple servers at same time");
    }
    private Constraint latencyViolation(ConstraintFactory factory) {

        // Single tuple containing all latencies(timefold does not have issues with this)
        UniConstraintStream<List<InterRegionLatency>> latencyListStream =
                factory.forEach(InterRegionLatency.class)
                        .groupBy(ConstraintCollectors.toList());

        var depWithRequests = factory.forEach(Deployment.class)
                .filter(dep -> dep.getService() != null
                        && dep.getServer() != null
                        && dep.getRequests() != null
                        && !dep.getRequests().isEmpty())
                .join(Request.class, Joiners.filtering((dep, req)->dep.getRequests().contains(req)));

        // Case 1 there exists at least one dependency deployment match -> compute max dep latency via max() collector.
        var withDeps = depWithRequests
                // Ugh this is slightly inaccurate, the case A->B->C should have been A<->B+B<->C but instead it becomes
                // max(A<->B, A<-C> here
                .join(Deployment.class,
                        filtering((dep, request, depOther) ->
                                depOther.getServer() != null
                                        && depOther.getService() != null
                                        && dep.getImpactedServices() != null
                                        && dep.getImpactedServices().contains(depOther.getService())
                        ))
                .join(latencyListStream)
                .groupBy(
                        (dep, request, depOther, il) -> dep,
                        (dep, request, depOther, il) -> request,
                        ConstraintCollectors.max((Deployment dep, Request request, Deployment depOther, List<InterRegionLatency> il) -> latencyBetweenDeploymentsMs(dep, depOther, il)),
                        ConstraintCollectors.max((Deployment dep, Request request, Deployment depOther, List<InterRegionLatency> il) -> requestLatencyToDeploymentMs(request, dep, il))
                )
                .map((dep, request, maxDepLatencyMs, requestLatencyMs) ->
                        slaViolationMs(request, requestLatencyMs, maxDepLatencyMs));

        // Case 2 no dependency deployment match -> treat max dependency latency as 0.
        var noDeps = depWithRequests
                .ifNotExists(Deployment.class,
                        filtering((dep, request, depOther) ->
                                depOther.getServer() != null
                                        && depOther.getService() != null
                                        && dep.getImpactedServices() != null
                                        && dep.getImpactedServices().contains(depOther.getService())
                        ))
                // (dep, request, ilList)
                .join(latencyListStream)
                .map((dep, request, il) ->
                        slaViolationMs(request, requestLatencyToDeploymentMs(request, dep, il), 0));

        return withDeps
                .concat(noDeps)
                .filter(penaltyMs -> penaltyMs > 0)
                .penalize(HardSoftScore.ONE_SOFT, penaltyMs -> penaltyMs)
                .asConstraint("Request latency violated");
    }

    private int slaViolationMs(Request request, int requestLatencyMs, int maxDependencyLatencyMs) {
        if (request == null) {
            return 0;
        }
        int slaMs = floatToIntMs(request.getMaxLatencySLA());
        long total = (long) requestLatencyMs + (long) maxDependencyLatencyMs; // avoid int overflow

        if (total > slaMs) {
            long diff = total - slaMs;
            return diff > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) diff;
        }
        return 0;
    }

    private int requestLatencyToDeploymentMs(Request request, Deployment dep, List<InterRegionLatency> il) {
        if (request == null || dep == null || dep.getServer() == null || request.getSourceRegion() == null) {
            return Integer.MAX_VALUE;
        }
        Region serverRegion = dep.getServer().getRegion();
        Region requestRegion = request.getSourceRegion();
        if (serverRegion == null || requestRegion == null || serverRegion.getName() == null || requestRegion.getName() == null) {
            return Integer.MAX_VALUE;
        }

        if (serverRegion.getName().equals(requestRegion.getName())) {
            return floatToIntMs(serverRegion.getLatency());
        }
        return interRegionLatencyMs(requestRegion, serverRegion, il);
    }

    private int latencyBetweenDeploymentsMs(Deployment dep1, Deployment dep2, List<InterRegionLatency> il) {
        if (dep1 == null || dep2 == null || dep1.getServer() == null || dep2.getServer() == null) {
            return Integer.MAX_VALUE;
        }
        if (dep1.getServer().getId() == dep2.getServer().getId()) {
            return 0;
        }
        Region r1 = dep1.getServer().getRegion();
        Region r2 = dep2.getServer().getRegion();
        if (r1 == null || r2 == null || r1.getName() == null || r2.getName() == null) {
            return Integer.MAX_VALUE;
        }

        if (r1.getName().equals(r2.getName())) {
            return floatToIntMs(r1.getLatency());
        }
        return interRegionLatencyMs(r1, r2, il);
    }

    private int interRegionLatencyMs(Region r1, Region r2, List<InterRegionLatency> il) {
        if (il == null || r1 == null || r2 == null || r1.getName() == null || r2.getName() == null) {
            return Integer.MAX_VALUE;
        }
        String a = r1.getName();
        String b = r2.getName();
        for (InterRegionLatency latency : il) {
            if (latency == null || latency.getRegion1() == null || latency.getRegion2() == null) {
                continue;
            }
            String x = latency.getRegion1().getName();
            String y = latency.getRegion2().getName();
            if (x == null || y == null) {
                continue;
            }

            boolean match = (x.equals(a) && y.equals(b)) || (x.equals(b) && y.equals(a));
            if (match) {
                return floatToIntMs(latency.getLatency());
            }
        }
        return Integer.MAX_VALUE;
    }

    private int floatToIntMs(float value) {
        if (!Float.isFinite(value)) {
            return Integer.MAX_VALUE;
        }
        if (value <= 0f) {
            return 0;
        }
        if (value >= (float) Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) value;
    }
}
