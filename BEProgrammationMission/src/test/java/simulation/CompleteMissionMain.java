package simulation;

import java.util.Map;

import org.slf4j.Logger;

import fr.cnes.sirius.patrius.attitudes.AttitudeLawLeg;
import fr.cnes.sirius.patrius.attitudes.AttitudeLeg;
import fr.cnes.sirius.patrius.attitudes.StrictAttitudeLegsSequence;
import fr.cnes.sirius.patrius.events.postprocessing.Timeline;
import fr.cnes.sirius.patrius.utils.exception.PatriusException;
import progmission.CompleteMission;
import reader.Site;
import utils.LogUtils;

/**
 * Class used to launch a simuation using {@link CompleteMission}
 * 
 * @author herberl
 *
 */
public class CompleteMissionMain {

	/**
	 * Main method to run your simulation.
	 * 
	 * @param args No arg
	 * @throws PatriusException If a {@link PatriusException} occurs.
	 */
	public static void main(String[] args) throws PatriusException {

		final Logger logger = LogUtils.GLOBAL_LOGGER;

		logger.info("##################################################");
		double t0 = System.currentTimeMillis();

		// Instantiating our mission using the CompleteMission object.
		final CompleteMission mission = new CompleteMission("BE Supaero mission", 20);
		logger.info("Complete simulation starting ...");
		logger.info(mission.toString());

		// First step is to compute when the satellite can access the targets. Each
		// access is an observation opportunity to be consider in the later scheduling
		// process.
		Map<Site, Timeline> accessPlan = mission.computeAccessPlan();
		logger.info("Access plan : " + accessPlan.toString());

		// Then we compute the observation plan, that is to say we fill a plan with
		// Observation objects that can be achieved one after each other by the
		// satellite without breaking the cinematic constraints imposed by the
		// satellite agility.
		Map<Site, AttitudeLawLeg> observationPlan = mission.computeObservationPlan();
		logger.info("Observation plan : " + observationPlan.toString());

		// Then, we compute the cinematic plan, which is the whole cinematic sequence of
		// attitude law legs for our satellite during the mission horizon
		StrictAttitudeLegsSequence<AttitudeLeg> cinematicPlan = mission.computeCinematicPlan();
		logger.info("Cinematic plan : " + cinematicPlan.toPrettyString());

		// Checking the validity our cinematic plan
		boolean validity = mission.checkCinematicPlan(cinematicPlan);
		logger.info("Plan validity : " + validity);

		// Only if the cinematic plan is valid : we compute the score of our
		// observationPlan
		logger.info("Number of cities visited : " + observationPlan.size());
		double beforeComputingScore = System.currentTimeMillis();
		logger.info("Final score :" + mission.computeFinalScore(observationPlan));
		double afterComputingScore = System.currentTimeMillis();
		logger.info("Duration of the score computation : " + 0.001 * (afterComputingScore - beforeComputingScore));

		// Computing the time of execution
		double t1 = System.currentTimeMillis();
		logger.info("Total duration : " + 0.001 * (t1 - t0));

		// Finally, we write the VTS outputs to visualize and validate our plan
		mission.generateVTSVisualization(cinematicPlan);

		logger.info("\n\nSimulation done");

		logger.info("##################################################");

	}

}
