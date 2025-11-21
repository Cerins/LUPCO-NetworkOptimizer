package lv.lu.eztf.dn.network_optimizer;


import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.timefold.solver.core.api.score.calculator.EasyScoreCalculator;
import lv.lu.eztf.dn.network_optimizer.domain.DeploymentPlan;

public class TrivialScoreCalculation implements EasyScoreCalculator<DeploymentPlan, HardSoftScore> {

    @Override
    public HardSoftScore calculateScore(DeploymentPlan solution) {
        return HardSoftScore.ZERO;
    }
}