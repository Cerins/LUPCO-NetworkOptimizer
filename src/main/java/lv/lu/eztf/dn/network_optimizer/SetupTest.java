package lv.lu.eztf.dn.network_optimizer;

import ai.timefold.solver.core.api.score.ScoreExplanation;
import ai.timefold.solver.core.api.score.ScoreManager;
import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.timefold.solver.core.api.solver.SolutionManager;
import ai.timefold.solver.core.api.solver.Solver;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.solver.EnvironmentMode;
import ai.timefold.solver.core.config.solver.SolverConfig;
import lv.lu.eztf.dn.network_optimizer.domain.*;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class SetupTest {
    public static void main(String[] args) {

        SolverFactory<DeploymentPlan> solverFactory = SolverFactory.create(
                new SolverConfig()
                        .withSolutionClass(DeploymentPlan.class)
                        .withEntityClasses(Deployment.class)
                        .withConstraintProviderClass(NetworkOptimizationConstraintProvider.class)
                        .withEnvironmentMode(EnvironmentMode.FULL_ASSERT)
                        .withTerminationSpentLimit(Duration.ofSeconds(5))
        );
        Solver<DeploymentPlan> solver = solverFactory.buildSolver();
        DeploymentPlan plan = new DeploymentPlan();
        Region rg1 = new Region("Europe", 50);
        Region rg2 = new Region("North America", 200);
        Cost c1 = new Cost(1, BigDecimal.valueOf(2), BigDecimal.valueOf(5), BigDecimal.valueOf(3));
        Cost c2 = new Cost(1, BigDecimal.valueOf(1), BigDecimal.valueOf(1), BigDecimal.valueOf(2));
        // SERVERS
        Server s1 = new Server(1, "s1", 8, 32f, 500f, rg1, c1);
        Server s2 = new Server(2, "s2", 1, 16f, 250f, rg2, c2);
        plan.setServerList(List.of(s1, s2));
        // SERVICES
        Service svc1DB = new Service(1, "a1-db", 2f, 4f, 10f, 10, null);
        Service svc1 = new Service(1, "a1", 2f, 4f, 10f, 20, null);
        svc1.dependsOn(svc1DB);
        Service svc2 = new Service(2, "a2", 4f, 8f, 50f, 50, null);
        plan.setServiceList(List.of(svc1, svc2));
        // REQUESTS
        Request r1 = new Request(1, "a1", new Date(), 10, 60f, rg1);
        Request r2 = new Request(2, "a2", new Date(), 12, 150f, rg2);
        plan.setRequests(List.of(r1, r2));
        // DATE RANGE
        Date now = new Date();
        Calendar cal = Calendar.getInstance();
        cal.setTime(now);
        cal.add(Calendar.DAY_OF_MONTH, 3);
        Date dPlus3 = cal.getTime();
        cal.setTime(now);
        cal.add(Calendar.DAY_OF_MONTH, 7);
        Date dPlus7 = cal.getTime();
        plan.setAvailableDates(List.of(now, dPlus3, dPlus7));
        // PRE-CREATE DEPLOYMENT SLOTS
        // (solver decides which ones become active)
        int deploymentSlots = 5;
        List<Deployment> deployments = new ArrayList<>();
        for (int i = 1; i <= deploymentSlots; i++) {
            deployments.add(new Deployment(i, null, null, null, null, new ArrayList<>()));
        }
        plan.setDeployments(deployments);
        // INTER-REGION LATENCY FACTS
        InterRegionLatency ir1 = new InterRegionLatency(50, rg1, rg2);
        List<InterRegionLatency> latencyList = new ArrayList<>();
        latencyList.add(ir1);
        // (not used in constraints yet)
        plan.setLatencies(latencyList);
        // SOLVE
        DeploymentPlan result = solver.solve(plan);
        SolutionManager<DeploymentPlan, HardSoftScore> solutionManager = SolutionManager.create(solverFactory);
        System.out.println("\n===== SOLUTION =====");
        System.out.println("Score: " + result.getScore());
        for (Deployment d : result.getDeployments()) {
            System.out.println("\nDeployment " + d.getId());
            System.out.println("  Service: " +
                    (d.getService() == null ? "null" :
                            d.getService().getName()));
            System.out.println("  Server: " +
                    (d.getServer() == null ? "null" :
                            d.getServer().getName()));
            System.out.println("  Start: " + d.getDateFrom());
            System.out.println("  End:   " + d.getDateTo());
            System.out.println("  Requests: " + d.getRequests().size());
            for (Request r : d.getRequests()) {
                System.out.println("  Request: " + r.toString());
            }
        }
        System.out.println("======================\n");
        System.out.println("===== VIOLATIONS =====\n");
        ScoreExplanation<DeploymentPlan, HardSoftScore> explanation = solutionManager.explain(result);
        explanation.getConstraintMatchTotalMap().forEach((constraint, matchTotal) -> {

            System.out.println(
                    constraint +
                            "  |  Impact: " + matchTotal.getScore() +
                            "  |  Matches: " + matchTotal.getConstraintMatchSet().size()
            );

            matchTotal.getConstraintMatchSet().forEach(match -> {
                System.out.println("    → " + match.getIdentificationString()
                        + "  |  " + match.getScore());
            });
        });

    }
}
