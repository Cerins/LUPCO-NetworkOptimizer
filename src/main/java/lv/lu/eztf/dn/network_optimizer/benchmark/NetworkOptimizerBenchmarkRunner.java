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
            // Create benchmark factory from XML configuration
            PlannerBenchmarkFactory benchmarkFactory = PlannerBenchmarkFactory.createFromXmlResource("benchmarkConfig.xml");
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
