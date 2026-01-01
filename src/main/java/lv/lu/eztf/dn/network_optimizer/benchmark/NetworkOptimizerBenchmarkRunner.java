package lv.lu.eztf.dn.network_optimizer.benchmark;

import ai.timefold.solver.benchmark.api.PlannerBenchmark;
import ai.timefold.solver.benchmark.api.PlannerBenchmarkFactory;
import ai.timefold.solver.core.config.solver.EnvironmentMode;
import ai.timefold.solver.core.config.solver.SolverConfig;
import lv.lu.eztf.dn.network_optimizer.NetworkOptimizationConstraintProvider;
import lv.lu.eztf.dn.network_optimizer.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * Main class for running benchmarks on the Network Optimizer.
 *
 * This class loads the benchmark configuration and executes benchmarks
 * to compare different solver configurations and find the optimal settings.
 *
 * Usage:
 *   Run this class directly from your IDE or via Maven:
 *   mvn exec:java -Dexec.mainClass="lv.lu.eztf.dn.network_optimizer.benchmark.NetworkOptimizerBenchmarkRunner"
 *
 * The benchmark will:
 *   - Test multiple solver configurations (Tabu Search, Late Acceptance, etc.)
 *   - Run each configuration on all datasets in src/main/resources/data/
 *   - Generate an HTML report in local/benchmarkReport/
 *   - Automatically open the report in your default browser
 */
public class NetworkOptimizerBenchmarkRunner {

    private static final Logger logger = LoggerFactory.getLogger(NetworkOptimizerBenchmarkRunner.class);

    public static void main(String[] args) {
        logger.info("Starting Network Optimizer Benchmark...");
        logger.info("This may take several minutes depending on the number of datasets and solver configurations.");

        try {
            SolverConfig test =  new SolverConfig()
                    .withSolutionClass(DeploymentPlan.class)
                    .withEntityClasses(Deployment.class)
                    .withConstraintProviderClass(NetworkOptimizationConstraintProvider.class)
                    .withEnvironmentMode(EnvironmentMode.PHASE_ASSERT)
                    .withTerminationSpentLimit(Duration.ofSeconds(10));
            // Create benchmark factory from XML configuration
            PlannerBenchmarkFactory benchmarkFactory = PlannerBenchmarkFactory.createFromXmlResource("benchmarkConfig.xml");

            // Build and run the benchmark
            //PlannerBenchmarkFactory benchmarkFactory = PlannerBenchmarkFactory.createFromSolverConfig(test, new File("local/benchmarkReport"));
            DeploymentPlan plan = new DeploymentPlan();
            Region rg1 = new Region("Europe", 50);
            Region rg2 = new Region("North America", 200);
            Cost c1 = new Cost(1, BigDecimal.valueOf(2), BigDecimal.valueOf(5), BigDecimal.valueOf(3));
            Cost c2 = new Cost(1, BigDecimal.valueOf(1), BigDecimal.valueOf(1), BigDecimal.valueOf(2));
            // SERVERS
            Server s1 = new Server(1, "s1", 6, 32f, 500f, rg1, c1);
            Server s2 = new Server(2, "s2", 2, 16f, 250f, rg2, c2);
            plan.setServerList(List.of(s1, s2));
            // SERVICES
            Service svc1DB = new Service(1, "a1-db", 2f, 4f, 10f, 10, null);
            Service svc1 = new Service(1, "a1", 2f, 4f, 10f, 20, null);
            svc1.dependsOn(svc1DB);
            Service svc2 = new Service(2, "a2", 4f, 8f, 50f, 50, null);
            plan.setServiceList(List.of(svc1, svc2, svc1DB));
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
            InterRegionLatency ir1 = new InterRegionLatency(250, rg1, rg2);
            List<InterRegionLatency> latencyList = new ArrayList<>();
            latencyList.add(ir1);
            // (not used in constraints yet)
            plan.setLatencies(latencyList);
            // PlannerBenchmark benchmark = benchmarkFactory.buildPlannerBenchmark(plan);
            PlannerBenchmark benchmark = benchmarkFactory.buildPlannerBenchmark();

            logger.info("Benchmark configuration loaded. Starting benchmark execution...");

            // Run the benchmark and automatically open the report in the browser
            benchmark.benchmarkAndShowReportInBrowser();

            logger.info("Benchmark completed successfully!");
            logger.info("Results have been written to local/benchmarkReport/");

        } catch (Exception e) {
            logger.error("Benchmark failed with error: {}", e.getMessage(), e);
            System.exit(1);
        }
    }
}
