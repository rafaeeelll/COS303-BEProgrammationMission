package unittest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import fr.cnes.sirius.patrius.attitudes.AttitudeLawLeg;
import fr.cnes.sirius.patrius.attitudes.AttitudeLeg;
import fr.cnes.sirius.patrius.attitudes.StrictAttitudeLegsSequence;
import fr.cnes.sirius.patrius.events.Phenomenon;
import fr.cnes.sirius.patrius.events.postprocessing.Timeline;
import fr.cnes.sirius.patrius.time.AbsoluteDate;
import fr.cnes.sirius.patrius.utils.exception.PatriusException;
import progmission.CompleteMission;
import reader.Site;
import utils.ConstantsBE;

/**
 * Integration tests for the access, observation and cinematic plans of a
 * {@link CompleteMission}.
 */
public class CompleteMissionTest {

	/** Tolerance used when comparing dates computed by event detectors. */
	private static final double DATE_TOLERANCE_SECONDS = 1.0e-5;

	/**
	 * Builds a small mission and checks the essential invariants of all three
	 * computed plans.
	 *
	 * @throws PatriusException if PATRIUS cannot build or propagate the mission
	 */
	@Test
	public void testCompleteMissionPlans() throws PatriusException {
		final String missionName = "BE Supaero mission test";
		final int siteNumber = 10;
		final CompleteMission mission = new CompleteMission(missionName, siteNumber);

		assertEquals("The mission should contain the requested sites", siteNumber, mission.getSiteList().size());
		assertEquals("The mission name should be retained", missionName, mission.getName());

		mission.computeAccessPlan();
		assertAccessPlanIsUsable(mission);

		mission.computeObservationPlan();
		assertObservationPlanIsUsable(mission);

		mission.computeCinematicPlan();
		assertCinematicPlanIsUsable(mission);

		final double score = mission.computeFinalScore(mission.getObservationPlan());
		assertTrue("The mission score must be finite", !Double.isNaN(score) && !Double.isInfinite(score));
		assertTrue("The mission score must not be negative", score >= 0.0);
	}

	/** Checks that every site has a timeline and that its access windows are in horizon. */
	private void assertAccessPlanIsUsable(final CompleteMission mission) {
		final Map<Site, Timeline> accessPlan = mission.getAccessPlan();
		assertNotNull("The access plan must be initialized", accessPlan);
		assertEquals("There must be one timeline for every site", mission.getSiteList().size(), accessPlan.size());

		int accessCount = 0;
		for (final Site site : mission.getSiteList()) {
			final Timeline timeline = accessPlan.get(site);
			assertNotNull("Missing access timeline for " + site.getName(), timeline);
			for (final Phenomenon access : timeline.getPhenomenaList()) {
				final AbsoluteDate start = access.getStartingEvent().getDate();
				final AbsoluteDate end = access.getEndingEvent().getDate();
				assertTrue("An access must have positive duration", end.durationFrom(start) > 0.0);
				assertDateInHorizon(start, mission, "Access start for " + site.getName());
				assertDateInHorizon(end, mission, "Access end for " + site.getName());
				accessCount++;
			}
		}
		assertTrue("At least one access window should be found", accessCount > 0);
	}

	/** Checks observation duration, access membership, unique sites and no overlap. */
	private void assertObservationPlanIsUsable(final CompleteMission mission) {
		final Map<Site, AttitudeLawLeg> observationPlan = mission.getObservationPlan();
		assertNotNull("The observation plan must be initialized", observationPlan);
		assertFalse("At least one observation should be scheduled", observationPlan.isEmpty());
		assertEquals("Each site may be observed at most once", observationPlan.size(),
				new HashSet<Site>(observationPlan.keySet()).size());

		final List<AttitudeLawLeg> legs = new ArrayList<AttitudeLawLeg>(observationPlan.values());
		for (final Map.Entry<Site, AttitudeLawLeg> entry : observationPlan.entrySet()) {
			final Site site = entry.getKey();
			final AttitudeLawLeg observation = entry.getValue();
			assertNotNull("Missing observation leg for " + site.getName(), observation);
			final AbsoluteDate start = observation.getDate();
			final AbsoluteDate end = observation.getEnd();
			assertEquals("Every observation must last the integration time", ConstantsBE.INTEGRATION_TIME,
					end.durationFrom(start), DATE_TOLERANCE_SECONDS);
			assertDateInHorizon(start, mission, "Observation start for " + site.getName());
			assertDateInHorizon(end, mission, "Observation end for " + site.getName());
			assertTrue("Observation for " + site.getName() + " must fit inside one of its access windows",
					isContainedInAnAccess(start, end, mission.getAccessPlan().get(site)));
		}

		for (int first = 0; first < legs.size(); first++) {
			for (int second = first + 1; second < legs.size(); second++) {
				final AttitudeLawLeg a = legs.get(first);
				final AttitudeLawLeg b = legs.get(second);
				final boolean disjoint = a.getEnd().durationFrom(b.getDate()) <= DATE_TOLERANCE_SECONDS
						|| b.getEnd().durationFrom(a.getDate()) <= DATE_TOLERANCE_SECONDS;
				assertTrue("Two observation legs must not overlap", disjoint);
			}
		}
	}

	/** Checks that cinematic legs form a valid, gap-free cover of the mission. */
	private void assertCinematicPlanIsUsable(final CompleteMission mission) throws PatriusException {
		final StrictAttitudeLegsSequence<AttitudeLeg> plan = mission.getCinematicPlan();
		assertNotNull("The cinematic plan must be initialized", plan);
		assertFalse("The cinematic plan must contain at least one leg", plan.isEmpty());

		AbsoluteDate previousEnd = null;
		for (final AttitudeLeg leg : plan) {
			assertNotNull("The cinematic plan must not contain null legs", leg);
			final AbsoluteDate start = leg.getDate();
			final AbsoluteDate end = leg.getEnd();
			assertTrue("Every cinematic leg must have positive duration", end.durationFrom(start) > 0.0);
			assertDateInHorizon(start, mission, "Cinematic leg start");
			assertDateInHorizon(end, mission, "Cinematic leg end");
			if (previousEnd != null) {
				assertEquals("Cinematic legs must be contiguous", 0.0, start.durationFrom(previousEnd),
						DATE_TOLERANCE_SECONDS);
			}
			previousEnd = end;
		}

		final AttitudeLeg firstLeg = plan.first();
		final AttitudeLeg lastLeg = plan.last();
		assertEquals("The cinematic plan must start at the mission start", 0.0,
				firstLeg.getDate().durationFrom(mission.getStartDate()), DATE_TOLERANCE_SECONDS);
		assertEquals("The cinematic plan must end at the mission end", 0.0,
				lastLeg.getEnd().durationFrom(mission.getEndDate()), DATE_TOLERANCE_SECONDS);
		assertTrue("The mission's cinematic validity check must pass", mission.checkCinematicPlan(plan));
	}

	/** Returns whether an observation interval fits wholly within one access. */
	private boolean isContainedInAnAccess(final AbsoluteDate observationStart, final AbsoluteDate observationEnd,
			final Timeline timeline) {
		if (timeline == null) {
			return false;
		}
		for (final Phenomenon access : timeline.getPhenomenaList()) {
			final AbsoluteDate accessStart = access.getStartingEvent().getDate();
			final AbsoluteDate accessEnd = access.getEndingEvent().getDate();
			if (observationStart.durationFrom(accessStart) >= -DATE_TOLERANCE_SECONDS
					&& accessEnd.durationFrom(observationEnd) >= -DATE_TOLERANCE_SECONDS) {
				return true;
			}
		}
		return false;
	}

	/** Asserts that a date lies inside the mission horizon, within tolerance. */
	private void assertDateInHorizon(final AbsoluteDate date, final CompleteMission mission, final String message) {
		assertTrue(message + " must be after mission start",
				date.durationFrom(mission.getStartDate()) >= -DATE_TOLERANCE_SECONDS);
		assertTrue(message + " must be before mission end",
				mission.getEndDate().durationFrom(date) >= -DATE_TOLERANCE_SECONDS);
	}
}
