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
                requestServiceMustMatchDeploymentService(factory),
                requestMustHaveServer(factory),
                validDateRange(factory),
                enoughCPU(factory),
                enoughMemory(factory),
                enoughStorage(factory),
                allRequestsProcessed(factory),
                serviceOnMultipleServersAtSameTime(factory),
                // Soft constraints
                serverActiveIntervalsCost(factory),
                latencyViolation(factory),
                rewardFastLatency(factory)
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

    // Or in other words the demand for service s and the moment t does not exceed to capacity
    // This is actually not used since it does not take into account the fact that we need to deploy dependencies
    // Help i dont know how to implement this
    private Constraint allRequestsProcessed(ConstraintFactory factory) {
// 1) Requests paired with matching deployments (req, dep)
        var matched = factory.forEach(Request.class)
                .join(Deployment.class,
                        equal(Request::getServiceName,
                                d -> d.getService() != null ? d.getService().getName() : null),
                        filtering((req, dep) ->
                                dep.getDateFrom() != null &&
                                        dep.getDateTo() != null &&
                                        !req.getDate().before(dep.getDateFrom()) &&
                                        !req.getDate().after(dep.getDateTo())))
                .map((req, dep) -> new Object[]{req, dep});

// 2) Requests with NO matching deployment: (req, null)
        var unmatched = factory.forEach(Request.class)
                .ifNotExists(Deployment.class,
                        equal(Request::getServiceName,
                                d -> d.getService() != null ? d.getService().getName() : null),
                        filtering((req, dep) ->
                                dep.getDateFrom() != null &&
                                        dep.getDateTo() != null &&
                                        !req.getDate().before(dep.getDateFrom()) &&
                                        !req.getDate().after(dep.getDateTo())))
                .map(req -> new Object[]{req, null});

// 3) Concatenate
        var leftJoinStream = matched.concat(unmatched);

        // 4) Now group by (serviceName, moment)
        return leftJoinStream
                .groupBy(
                        tuple -> ((Request) tuple[0]).getServiceName(),
                        tuple -> ((Request) tuple[0]).getDate(),
                        ConstraintCollectors.sum(tuple ->
                                ((Request) tuple[0]).getEstimatedQueryCount()),
                        ConstraintCollectors.sum(tuple -> {
                            Deployment dep = (Deployment) tuple[1];
                            return dep == null ? 0 : dep.getService().maxRequests();
                        })
                )
                .filter((serviceName, moment, demand, capacity) -> demand > capacity)
                .penalize(HardSoftScore.ONE_HARD,
                        (serviceName, moment, demand, capacity) -> demand - capacity)
                .asConstraint("Service capacity violated during request moments");
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

    private float computeLatency(Request req, Server server, InterRegionLatency fact) {
        Region rReq = req.getSourceRegion();
        Region rSrv = server.getRegion();
        if (rReq == null || rSrv == null) return Float.MAX_VALUE;
        // same region
        if (rReq.getName().equals(rSrv.getName())) {
            return rSrv.getLatency();
        }
        // cross-region
        return rSrv.getLatency() + fact.getLatency() + rReq.getLatency();
    }
    private Constraint latencyViolation(ConstraintFactory factory) {
        return factory.forEach(Request.class)
                .join(InterRegionLatency.class)        // join all latency facts
                .join(Deployment.class, filtering((req, l, dep) ->
                        dep.getRequests() != null
                                && dep.getRequests().contains(req)
                                && dep.getServer() != null))
                .filter((req, latencyFact, dep) -> {
                    boolean sameRegion = req.getSourceRegion() == dep.getServer().getRegion();
                    boolean validLatencyFact = (
                            (latencyFact.getRegion1() == req.getSourceRegion() && latencyFact.getRegion2() == dep.getServer().getRegion())
                            || (latencyFact.getRegion1() == dep.getServer().getRegion() && latencyFact.getRegion2() == req.getSourceRegion())
                            );
                    if(sameRegion || validLatencyFact)         {
                        Server server = dep.getServer();
                        float latency = computeLatency(req, server, latencyFact);
                        return latency > req.getMaxLatencySLA();
                    } else {
                        return false;
                    }
                })
                .penalize(HardSoftScore.ONE_SOFT,
                        (req, latencyFact, dep) -> (int) (computeLatency(req, dep.getServer(), latencyFact) - req.getMaxLatencySLA()) )
                .asConstraint("Request latency violated");
    }
    private Constraint rewardFastLatency(ConstraintFactory factory) {
        return factory.forEach(Request.class)
                .join(InterRegionLatency.class)        // join all latency facts
                .join(Deployment.class, filtering((req, l, dep) ->
                        dep.getRequests() != null
                                && dep.getRequests().contains(req)
                                && dep.getServer() != null))
                .filter((req, latencyFact, dep) -> {
                    boolean sameRegion = req.getSourceRegion() == dep.getServer().getRegion();
                    boolean validLatencyFact = (
                            (latencyFact.getRegion1() == req.getSourceRegion() && latencyFact.getRegion2() == dep.getServer().getRegion())
                                    || (latencyFact.getRegion1() == dep.getServer().getRegion() && latencyFact.getRegion2() == req.getSourceRegion())
                    );
                    if(sameRegion || validLatencyFact)         {
                        Server server = dep.getServer();
                        float latency = computeLatency(req, server, latencyFact);
                        return latency < req.getMaxLatencySLA();
                    } else {
                        return false;
                    }
                })
                .reward(HardSoftScore.ONE_SOFT,
                        (req, latencyFact, dep) -> (int) (-computeLatency(req, dep.getServer(), latencyFact) + req.getMaxLatencySLA()) )
                .asConstraint("Request latency faster");
    }





}
