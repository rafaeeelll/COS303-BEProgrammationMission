package progmission;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.slf4j.Logger;

import fr.cnes.sirius.patrius.assembly.models.SensorModel;
import fr.cnes.sirius.patrius.attitudes.Attitude;
import fr.cnes.sirius.patrius.attitudes.AttitudeLaw;
import fr.cnes.sirius.patrius.attitudes.AttitudeLawLeg;
import fr.cnes.sirius.patrius.attitudes.AttitudeLeg;
import fr.cnes.sirius.patrius.attitudes.AttitudeProvider;
import fr.cnes.sirius.patrius.attitudes.ConstantSpinSlew;
import fr.cnes.sirius.patrius.attitudes.StrictAttitudeLegsSequence;
import fr.cnes.sirius.patrius.events.CodedEvent;
import fr.cnes.sirius.patrius.events.CodedEventsLogger;
import fr.cnes.sirius.patrius.events.GenericCodingEventDetector;
import fr.cnes.sirius.patrius.events.Phenomenon;
import fr.cnes.sirius.patrius.events.postprocessing.AndCriterion;
import fr.cnes.sirius.patrius.events.postprocessing.ElementTypeFilter;
import fr.cnes.sirius.patrius.events.postprocessing.Timeline;
import fr.cnes.sirius.patrius.events.sensor.SensorVisibilityDetector;
import fr.cnes.sirius.patrius.frames.FramesFactory;
import fr.cnes.sirius.patrius.frames.TopocentricFrame;
import fr.cnes.sirius.patrius.math.geometry.euclidean.threed.Vector3D;
import fr.cnes.sirius.patrius.math.util.FastMath;
import fr.cnes.sirius.patrius.propagation.analytical.KeplerianPropagator;
import fr.cnes.sirius.patrius.propagation.events.ConstantRadiusProvider;
import fr.cnes.sirius.patrius.propagation.events.EventDetector;
import fr.cnes.sirius.patrius.propagation.events.ThreeBodiesAngleDetector;
import fr.cnes.sirius.patrius.time.AbsoluteDate;
import fr.cnes.sirius.patrius.time.AbsoluteDateInterval;
import fr.cnes.sirius.patrius.time.AbsoluteDateIntervalsList;
import fr.cnes.sirius.patrius.utils.exception.PatriusException;
import reader.Site;
import utils.ConstantsBE;
import utils.LogUtils;
import utils.ProjectUtils;

/**
 * This class implements the context of an Earth Observation mission.
 * 
 * @author herberl
 */
public class CompleteMission extends SimpleMission {

	private static final String VISIBILITY_CODE = "VISIBILITY";
	private static final String SUN_INCIDENCE_CODE = "SUN_INCIDENCE";
	private static final String PHASE_ANGLE_CODE = "PHASE_ANGLE";
	private static final String VISIBILITY_AND_SUN_CODE = "VISIBILITY_AND_SUN";
	private static final String ACCESS_CODE = "SITE_ACCESS";

	/** One feasible observation interval and its estimated contribution to score. */
	private static final class ObservationCandidate {
		private final Site site;
		private final AttitudeLawLeg leg;
		private final double score;

		private ObservationCandidate(final Site site, final AttitudeLawLeg leg, final double score) {
			this.site = site;
			this.leg = leg;
			this.score = score;
		}
	}

	/**
	 * Maximum checking interval (s) for the event detection during the orbit
	 * propagation.
	 */
	public static final double MAXCHECK_EVENTS = 120.0;

	/**
	 * Default convergence threshold (s) for the event computation during the orbit
	 * propagation.
	 */
	public static final double TRESHOLD_EVENTS = 1.e-4;

	/**
	 * A hash based on all the constants of the BE. This hash is unique and ensures
	 * we get the right filename for a given {@link Site} and a given set of
	 * {@link ConstantsBE} parameters. It is used in the names of the files
	 * containing the serialized accesses for all {@link Site}.
	 *
	 */
	private static final int HASH_CONSTANT_BE;

	/**
	 * [DO NOT MODIFY THIS METHOD]
	 * 
	 * This block of code is static, shared by all instances of CompleteMission. It
	 * is executed only once when the class is first instantiated, to produce an
	 * unique hash which encodes the whole constants of the BE in one single code.
	 * You don't have to (and must not) modify this code if you want to benefit from
	 * automatic serialization and reading of you access Timelines.
	 */
	static {
		// Creating an unique hash code to be used for serialization
		int hash = 17;

		long doubleBits;
		int doubleHash;

		// The hash code depends from all the parameters from a given simulation : start
		// and end date and all the ConstantBE values. We build a hashcode from all
		// those values converting them as int

		// Start and end dates
		hash = 31 * hash + ConstantsBE.START_DATE.hashCode();
		hash = 31 * hash + ConstantsBE.END_DATE.hashCode();

		// ConstantBE values
		double[] doubles = new double[] { ConstantsBE.ALTITUDE, ConstantsBE.INCLINATION, ConstantsBE.MEAN_ECCENTRICITY,
				ConstantsBE.ASCENDING_NODE_LONGITUDE, ConstantsBE.POINTING_CAPACITY, ConstantsBE.SPACECRAFT_MASS,
				ConstantsBE.MAX_SUN_INCIDENCE_ANGLE, ConstantsBE.MAX_SUN_PHASE_ANGLE, ConstantsBE.INTEGRATION_TIME,
				ConstantsBE.POINTING_AGILITY_DURATIONS[0], ConstantsBE.POINTING_AGILITY_DURATIONS[1],
				ConstantsBE.POINTING_AGILITY_DURATIONS[2], ConstantsBE.POINTING_AGILITY_DURATIONS[3],
				ConstantsBE.POINTING_AGILITY_DURATIONS[4], ConstantsBE.POINTING_AGILITY_ROTATIONS[0],
				ConstantsBE.POINTING_AGILITY_ROTATIONS[1], ConstantsBE.POINTING_AGILITY_ROTATIONS[2],
				ConstantsBE.POINTING_AGILITY_ROTATIONS[3], ConstantsBE.POINTING_AGILITY_ROTATIONS[4], MAXCHECK_EVENTS,
				TRESHOLD_EVENTS };
		for (Double d : doubles) {
			doubleBits = Double.doubleToLongBits(d);
			doubleHash = (int) (doubleBits ^ (doubleBits >>> 32));
			hash = 31 * hash + doubleHash;
		}

		// Finally assigning the current simulation hashConstantBE value
		HASH_CONSTANT_BE = hash;
	}

	/**
	 * Logger for this class.
	 */
	private static final Logger logger = LogUtils.GLOBAL_LOGGER;

	/**
	 * This {@link Map} will be used to enumerate each site access {@link Timeline},
	 * that is to say a {@link Timeline} with access windows respecting all
	 * observation conditions. This object corresponds to the access plan, which
	 * will be computed in the computeAccessPlan() method.
	 */
	private final Map<Site, Timeline> accessPlan;

	/**
	 * This {@link Map} will be used to enumerate each site's programmed
	 * observation. We suggest to use an {@link AttitudeLawLeg} to encapsulate the
	 * guidance law of each observation. This object corresponds to the observation
	 * plan, which will be computed in the computeObservationPlan() method.
	 */
	private final Map<Site, AttitudeLawLeg> observationPlan;

	/**
	 * {@link StrictAttitudeLegsSequence} representing the cinematic plan during the
	 * whole mission horizon. Each {@link AttitudeLeg} corresponds to a diffrent
	 * attitude law : either nadir pointing, target pointing or a slew between two
	 * laws. This object corresponds to the cinematic plan, which will be computed
	 * in the computeCinematicPlan() method.
	 */
	private final StrictAttitudeLegsSequence<AttitudeLeg> cinematicPlan;

	/**
	 * Constructor for the {@link CompleteMission} class.
	 *
	 * @param missionName   Name of the mission
	 * @param numberOfSites Number of target {@link Site} to consider, please give a
	 *                      number between 1 and 100.
	 * @throws PatriusException      Can be raised by Patrius when building
	 *                               particular objects. Here it comes from
	 *                               {@link FramesFactory}
	 * @throws IllegalStateException if the mission horizon is too short
	 */
	public CompleteMission(final String missionName, int numberOfSites) throws PatriusException {

		// Since this class extends the SimpleMission class, we need to use the super
		// constructor to instantiate our instance of CompleteMission. All the
		// attributes of the super class will be instantiated during this step.
		super(missionName, numberOfSites);

		// Initialize the mission plans with empty maps. You will fill those HashMaps in
		// the "compute****Plan()" methods.
		this.accessPlan = new HashMap<>();
		this.observationPlan = new HashMap<>();
		this.cinematicPlan = new StrictAttitudeLegsSequence<>();

	}

	/**
	 * [COMPLETE THIS METHOD TO ACHIEVE YOUR PROJECT]
	 * 
	 * Compute the access plan.
	 * 
	 * Reminder : the access plan corresponds to the object gathering all the
	 * opportunities of access for all the sites of interest during the mission
	 * horizon. One opportunity of access is defined by an access window (an
	 * interval of time during which the satellite can observe the target and during
	 * which all the observation conditions are achieved : visibility, incidence
	 * angle, illumination of the scene,etc.). Here, we suggest you use the Patrius
	 * class {@link Timeline} to encapsulate all the access windows of each site of
	 * interest. One access window will then be described by the {@link Phenomenon}
	 * object, itself defined by two {@link CodedEvent} objects giving the start and
	 * end of the access window. Please find more tips and help in the submethods of
	 * this method.
	 * 
	 * @return the sites access plan with one {@link Timeline} per {@link Site}
	 * @throws PatriusException If a {@link PatriusException} occurs during the
	 *                          computations
	 */
	public Map<Site, Timeline> computeAccessPlan() throws PatriusException {
		/**
		 * Here you need to compute one access Timeline per target Site. You can start
		 * with only one site and then try to compute all of them.
		 * 
		 * Note : when computing all the sites, try to make sure you don't decrease the
		 * performance of the code too much. You might have some modifications to do in
		 * order to ensure a reasonable time of execution.
		 */
		logger.info("============= Computing Access Plan =============");

		/**
		 * Step 1 - Before the propagation; we load all already serialized Timelines or
		 * create the associated loggers
		 */
		// We create a Map containing all the loggers for each Site's constraint, that
		// we be used to optimize propagation
		final Map<Site, ArrayList<CodedEventsLogger>> sitesEventsLoggers = new HashMap<>();
		final KeplerianPropagator propagator = this.createDefaultPropagator();

		for (final Site targetSite : this.getSiteList()) {
			logger.info(" Site : " + targetSite.getName());

			/*
			 * You can check if the Site access has already been computed and serialized or
			 * not. If so, you can simply load the .ser file to load the timeline, else you
			 * need to compute it. This will prevent you from computing the whole access
			 * timelines each time you want to compute an observation plan and save time
			 * later in the BE.
			 */

			// Checking if the Site access Timeline has already been serialized or not
			final String filename = generateSerializationName(targetSite, HASH_CONSTANT_BE);
			File file = new File(filename);
			boolean loaded = false;

			// If the file exist for the current Site, we try to load its content
			if (false) { // Intentionally bypass legacy serialized timelines.
				try {
					// Load the timeline from the file and add it to the accessPlan
					final Timeline siteAccessTimeline = loadSiteAccessTimeline(filename);
					this.accessPlan.put(targetSite, siteAccessTimeline);
					ProjectUtils.printTimeline(siteAccessTimeline);
					loaded = true; // the Site has been loaded, no need to compute the access again
					logger.info(filename + "has been loaded successfully!");
				} catch (ClassNotFoundException | IOException e) {
					logger.warn(filename + " could not be loaded !");
					logger.warn(e.getMessage());
				}
			}

			// If it was not serialized or if loading has failed, we need to compute and
			// serialize the site access Timeline so we will create the asscoiated loggers
			if (!loaded) {
				logger.info(targetSite.getName() + " has not been serialized, launching access computation...");

				/*/
				 * Complete the code below
				 */
				// Create the loggers of the targetSite and the associated constraint
				ArrayList<CodedEventsLogger> siteLoggers = new ArrayList<>();
				// Create one logger per constraint, by completing and adapting
				// createSiteXConstraintLogger for each constraint, and add all
				// loggers to the loggers List for this Site
				siteLoggers.add(createVisibilityLogger(targetSite, propagator));
				siteLoggers.add(createSunIncidenceLogger(targetSite, propagator));
				siteLoggers.add(createPhaseAngleLogger(targetSite, propagator));
				// For example : createSiteXConstraintLogger => createVisibilityConstraintLogger

				// Finally, store the Site's loggers in the global Map
				sitesEventsLoggers.put(targetSite, siteLoggers);
			}
		}

		/**
		 * Step 2 - Propagating all Sites's detectors and loggers in parallel to
		 * optimize runtime
		 */

		// Now that every detector and logger has been added to the propagator for all
		// Sites, we can propagate the Orbit for all Sites at once
		propagator.propagate(this.getStartDate(), this.getEndDate());

		/**
		 * Step 3 - After the propagation, we can create all non-existing Timelines and
		 * serialize them
		 */
		// After the propagation, all the loggers can be used to create the access
		// Timelines and serialize them
		for (Entry<Site, ArrayList<CodedEventsLogger>> entry : sitesEventsLoggers.entrySet()) {
			final Site site = entry.getKey();
			final ArrayList<CodedEventsLogger> eventsLoggersList = entry.getValue();

			// Create the timeline using the 3 loggers previously created
			// Make sure to call the method using the loggers in the right order compared to
			// what you declared previously
			final Timeline siteAccessTimeline = createSiteAccessTimeline(site, eventsLoggersList.get(0),
					eventsLoggersList.get(1), eventsLoggersList.get(2));
			this.accessPlan.put(site, siteAccessTimeline);

			logger.info("Access windows for " + site.getName() + ": "
					+ siteAccessTimeline.getPhenomenaList().size());
		}
		return this.accessPlan;
	}

	/** Creates the visibility timeline logger for one site. */
	private CodedEventsLogger createVisibilityLogger(final Site site, final KeplerianPropagator propagator) {
		final TopocentricFrame siteFrame = new TopocentricFrame(this.getEarth(), site.getPoint(), site.getName());
		final SensorModel sensor = new SensorModel(this.getSatellite().getAssembly(), Satellite.SENSOR_NAME);
		sensor.setMainTarget(siteFrame, new ConstantRadiusProvider(0.0));
		sensor.addMaskingCelestialBody(this.getEarth());
		final EventDetector detector = new SensorVisibilityDetector(sensor, MAXCHECK_EVENTS, TRESHOLD_EVENTS,
				EventDetector.Action.CONTINUE, EventDetector.Action.CONTINUE);
		return monitor(detector, true, "VISIBILITY_START", "VISIBILITY_END", VISIBILITY_CODE, propagator);
	}

	/** Creates the local solar-incidence logger. g >= 0 means incidence <= limit. */
	private CodedEventsLogger createSunIncidenceLogger(final Site site, final KeplerianPropagator propagator) {
		final TopocentricFrame siteFrame = new TopocentricFrame(this.getEarth(), site.getPoint(), site.getName());
		final double allowedAngle = FastMath.PI - FastMath.toRadians(ConstantsBE.MAX_SUN_INCIDENCE_ANGLE);
		final EventDetector detector = new ThreeBodiesAngleDetector(this.getEarth(), siteFrame, this.getSun(),
				allowedAngle, MAXCHECK_EVENTS, TRESHOLD_EVENTS, EventDetector.Action.CONTINUE);
		return monitor(detector, true, "SUN_INCIDENCE_START", "SUN_INCIDENCE_END", SUN_INCIDENCE_CODE,
				propagator);
	}

	/** Creates the target-satellite-Sun phase-angle logger. */
	private CodedEventsLogger createPhaseAngleLogger(final Site site, final KeplerianPropagator propagator) {
		final TopocentricFrame siteFrame = new TopocentricFrame(this.getEarth(), site.getPoint(), site.getName());
		final EventDetector detector = new ThreeBodiesAngleDetector(null, siteFrame, this.getSun(),
				FastMath.toRadians(ConstantsBE.MAX_SUN_PHASE_ANGLE), MAXCHECK_EVENTS, TRESHOLD_EVENTS,
				EventDetector.Action.CONTINUE);
		return monitor(detector, false, "PHASE_ANGLE_END", "PHASE_ANGLE_START", PHASE_ANGLE_CODE, propagator);
	}

	/** Wraps a detector in coded events and attaches it to the mission propagator. */
	private CodedEventsLogger monitor(final EventDetector detector, final boolean increasingStarts,
			final String increasingCode, final String decreasingCode, final String phenomenonCode,
			final KeplerianPropagator propagator) {
		final GenericCodingEventDetector coded = new GenericCodingEventDetector(detector, increasingCode,
				decreasingCode, increasingStarts, phenomenonCode);
		final CodedEventsLogger logger = new CodedEventsLogger();
		propagator.addEventDetector(logger.monitorDetector(coded));
		return logger;
	}

	/**
	 * [COMPLETE THIS METHOD TO ACHIEVE YOUR PROJECT]
	 * 
	 * Computes the cinematic plan.
	 * 
	 * Here you need to compute the cinematic plan, which is the cinematic chain of
	 * attitude law legs (observation, default law and slews) needed to perform the
	 * mission. Usually, we start and end the mission in default law and during the
	 * horizon, we alternate between default law, observation legs and slew legs.
	 * 
	 * @return a {@link StrictAttitudeLegsSequence} that gives all the cinematic
	 *         plan of the {@link Satellite}. It is a chronological sequence of all
	 *         the {@link AttitudeLawLeg} that are necessary to define the
	 *         {@link Attitude} of the {@link Satellite} during all the mission
	 *         horizon. Those legs can have 3 natures : pointing a target site,
	 *         pointing nadir and performing a slew between one of the two previous
	 *         kind of legs.
	 * @throws PatriusException
	 */
	public StrictAttitudeLegsSequence<AttitudeLeg> computeCinematicPlan() throws PatriusException {
		if (this.observationPlan != null) {
			return computeCinematicPlanLevel2();
		}

		/**
		 * Now we want to assemble a continuous attitude law which is valid during all
		 * the mission horizon. For that, we will use to object
		 * StrictAttitudeLegsSequence<AttitudeLeg> which is a chronological sequence of
		 * AttitudeLeg. In our case, each AttitudeLeg will be an AttitudeLawLeg, either
		 * a leg of site observation, a slew, or the nadir pointing attitude law (see
		 * the Satellite constructor and the BodyCenterGroundPointing class, it's the
		 * Satellite default attitude law). For more help about the Attitude handling,
		 * use the module 11 of the patrius formation.
		 * 
		 * Tip 1 : Please give names to the different AttitudeLawLeg you build so that
		 * you can visualize them with VTS later on. For example "OBS_Paris" when
		 * observing Paris or "SlEW_Paris_Lyon" when adding a slew from Paris
		 * observation AttitudeLawLeg to Lyon observation AttitudeLawLeg.
		 * 
		 * Tip 2 : the sequence you want to obtain should look like this :
		 * [nadir-slew-obs1-slew-obs2-slew-obs3-slew-nadir] for the simple version where
		 * you don't try to fit nadir laws between observations or
		 * [nadir-slew-obs1-slew-nadir-selw-obs2-slew-obs3-slew-nadir] for the more
		 * complexe version with nadir laws if the slew during two observation is long
		 * enough.
		 * 
		 * Tip 3 : You can use the class ConstantSpinSlew(initialAttitude,
		 * finalAttitude, slewName) for the slews. This an AtittudeLeg so you will be
		 * able to add it to the StrictAttitudeLegsSequence as every other leg.
		 */
		logger.info("============= Computing Cinematic Plan =============");
		/*
		 * Example of code using our observation plan, let's say we only have one obs
		 * pointing Paris.
		 * 
		 * Then we are going to create a very basic cinematic plan : nadir law => slew
		 * => obsParis => slew => nadir law
		 * 
		 * To do that, we need to compute the slew duration from the end of nadir law to
		 * the begining of Paris obs and then from the end of Paris obs to the begining
		 * of nadir law. For that, we use the Satellite#computeSlewDurationMethod() as
		 * before. We know we have to the time to perform the slew thanks to the
		 * cinematic checks we already did during the observation plan computation.
		 */
		// Getting the Paris Site
		final Site paris = this.getSiteList().get(0);
		// Getting the associated observation leg defined previously
		final AttitudeLeg parisObsLeg = observationPlan.get(paris);

		// Getting our nadir law
		final AttitudeLaw nadirLaw = this.getSatellite().getDefaultAttitudeLaw();

		// Getting all the dates we need to compute our slews
		final AbsoluteDate start = this.getStartDate();
		final AbsoluteDate end = this.getEndDate();
		final AbsoluteDate obsStart = parisObsLeg.getDate();
		final AbsoluteDate obsEnd = parisObsLeg.getEnd();

		// For the slew nadir => paris and paris => nadir, we will use the maximum
		// duration because we have a lot of time here. In practice, you will use either
		// the maximum possible time if you have nothing else planned around or the
		// available time coming from the duration until next observation programmed.
		final AbsoluteDate endNadirLaw1 = obsStart.shiftedBy(-getSatellite().getMaxSlewDuration());
		final AbsoluteDate startNadirLaw2 = obsEnd.shiftedBy(+getSatellite().getMaxSlewDuration());

		// The propagator will be used to compute Attitudes
		final KeplerianPropagator propagator = this.createDefaultPropagator();

		// Computing the Attitudes used to compute the slews
		final Attitude startObsAttitude = parisObsLeg.getAttitude(propagator, obsStart, getEme2000());
		final Attitude endObsAttitude = parisObsLeg.getAttitude(propagator, obsEnd, getEme2000());
		final Attitude endNadir1Attitude = nadirLaw.getAttitude(propagator, endNadirLaw1, getEme2000());
		final Attitude startNadir2Attitude = nadirLaw.getAttitude(propagator, startNadirLaw2, getEme2000());

		// Finally computing the slews
		// From nadir law 1 to Paris observation
		final ConstantSpinSlew slew1 = new ConstantSpinSlew(endNadir1Attitude, startObsAttitude, "Slew_Nadir_to_Paris");
		// From Paris observation to nadir law 2
		final ConstantSpinSlew slew2 = new ConstantSpinSlew(endObsAttitude, startNadir2Attitude, "Slew_Paris_to_Nadir");

		// We create our two Nadir legs using the dates we computed
		final AttitudeLawLeg nadir1 = new AttitudeLawLeg(nadirLaw, start, endNadirLaw1, "Nadir_Law_1");
		final AttitudeLawLeg nadir2 = new AttitudeLawLeg(nadirLaw, startNadirLaw2, end, "Nadir_Law_2");

		// Finally we can add all those legs to our cinametic plan, in the chronological
		// order
		this.cinematicPlan.add(nadir1);
		this.cinematicPlan.add(slew1);
		this.cinematicPlan.add(parisObsLeg);
		this.cinematicPlan.add(slew2);
		this.cinematicPlan.add(nadir2);

		/**
		 * Now your job is finished, the two following methods will finish the job for
		 * you : checkCinematicPlan() will check that each slew's duration is longer
		 * than the theoritical duration it takes to perform the same slew. Then, if the
		 * cinematic plan is valid, computeFinalScore() will compute the score of your
		 * observation plan. Finaly, generateVTSVisualization will write all the
		 * ephemeris (Position/Velocity + Attitude) and generate a VTS simulation that
		 * you will be able to play to visualize and validate your plans.
		 */
		return this.cinematicPlan;
	}

	/**
	 * [COMPLETE THIS METHOD TO ACHIEVE YOUR PROJECT]
	 * 
	 * Compute the observation plan.
	 * 
	 * Reminder : the observation plan corresponds to the sequence of observations
	 * programmed for the satellite during the mission horizon. Each observation is
	 * defined by an observation window (start date; end date defining an
	 * {@link AbsoluteDateInterval}), a target (target {@link Site}) and an
	 * {@link AttitudeLawLeg} giving the attitude guidance to observe the target.
	 * 
	 * @return the sites observation plan with one {@link AttitudeLawLeg} per
	 *         {@link Site}
	 * @throws PatriusException If a {@link PatriusException} occurs during the
	 *                          computations
	 */
	public Map<Site, AttitudeLawLeg> computeObservationPlan() throws PatriusException {
		if (this.getSiteList().size() > 0) {
			return computeObservationPlanLevel2();
		}
		/**
		 * Here are the big constraints and informations you need to build an
		 * observation plan.
		 * 
		 * Reminder : we can perform only one observation per site of interest during
		 * the mission horizon.
		 * 
		 * Objective : Now we have our access plan, listing for each Site all the access
		 * windows. There might be up to one access window per orbit pass above each
		 * site, so we have to decide for each Site which access window will be used to
		 * achieve the observation of the Site. Then, during one access window, we have
		 * to decide when precisely we perform the observation, which lasts a constant
		 * duration which is much smaller than the access window itself (see
		 * ConstantsBE.INTEGRATION_TIME for the duration of one observation). Finally,
		 * we must respect the cinematic constraint : using the
		 * Satellite#computeSlewDuration() method, we need to ensure that the
		 * theoritical duration of the slew between two consecutive observations is
		 * always smaller than the actual duration between those consecutive
		 * observations. Same goes for the slew between a Nadir pointing law and another
		 * poiting law. Of course, we cannot point two targets at once, so we cannot
		 * perform two observations during the same AbsoluteDateInterval !
		 * 
		 * Tip 1 : Here you can use the greedy algorithm presented in class, or any
		 * method you want. You just have to ensure that all constraints are respected.
		 * This is a non linear, complex optimization problem (scheduling problem), so
		 * there is no universal answer. Even if you don't manage to build an optimal
		 * plan, try to code a suboptimal algorithm anyway, we will value any idea you
		 * have. For example, try with a plan where you have only one observation per
		 * satellite pass over France. With that kind of plan, you make sure all
		 * cinematic constraint are respected (no slew to fast for the satellite
		 * agility) and you have a basic plan to use to build your cinematic plan and
		 * validate with VTS visualization.
		 * 
		 * Tip 2 : We provide the observation plan format : a Map of AttitudeLawLeg. In
		 * doing so, we give you the structure that you must obtain in order to go
		 * further. If you check the Javadoc of the AttitudeLawLeg class, you see that
		 * you have two inputs. First, you must provide a specific interval of time that
		 * you have to chose inside one of the access windows of your access plan. Then,
		 * we give you which law to use for observation legs : TargetGroundPointing.
		 * 
		 */
		logger.info("============= Computing Observation Plan =============");
		/*
		 * We provide a basic and incomplete code that you can use to compute the
		 * observation plan.
		 * 
		 * Here the only thing we do is printing all the access opportunities using the
		 * Timeline objects. We get a list of AbsoluteDateInterval from the Timelines,
		 * which is the basis of the creation of AttitudeLawLeg objects since you need
		 * an AbsoluteDateInterval or two AbsoluteDates to do it.
		 */
		for (final Entry<Site, Timeline> entry : this.accessPlan.entrySet()) {
			// Scrolling through the entries of the accessPlan
			// Getting the target Site
			final Site target = entry.getKey();
			logger.info("Current target site : " + target.getName());
			// Getting its access Timeline
			final Timeline timeline = entry.getValue();
			// Getting the access intervals
			final AbsoluteDateIntervalsList accessIntervals = new AbsoluteDateIntervalsList();
			for (final Phenomenon accessWindow : timeline.getPhenomenaList()) {
				// The Phenomena are sorted chronologically so the accessIntervals List is too
				final AbsoluteDateInterval accessInterval = accessWindow.getTimespan();
				accessIntervals.add(accessInterval);
				logger.info(accessInterval.toString());

				// Use this method to create your observation leg, see more help inside the
				// method.
				final AttitudeLaw observationLaw = createObservationLaw(target);

				/**
				 * Now that you have your observation law, you can compute at any AbsoluteDate
				 * the Attitude of your Satellite pointing the target (using the getAttitude()
				 * method). You can use those Attitudes to compute the duration of a slew from
				 * one Attitude to another, for example the duration of the slew from the
				 * Attitude at the end of an observation to the Atittude at the start of the
				 * next one. That's how you will be able to choose a valid AbsoluteDateInterval
				 * during which the observation will actually be performed, lasting
				 * ConstantsBE.INTEGRATION_TIME seconds. When you have your observation
				 * interval, you can build an AttitudeLawLeg using the observationLaw and this
				 * interval and finally add this leg to the observation plan.
				 */
				/*
				 * Here is an example of how to compute an Attitude. You need a
				 * PVCoordinatePropagator (which we provide we the method
				 * SimpleMission#createDefaultPropagator()), an AbsoluteDate and a Frame (which
				 * we provide with this.getEME2000()).
				 */
				// Getting the begining/end of the accessIntervall as AbsoluteDate objects
				final AbsoluteDate date1 = accessInterval.getLowerData();
				final AbsoluteDate date2 = accessInterval.getUpperData();
				final Attitude attitude1 = observationLaw.getAttitude(this.createDefaultPropagator(), date1,
						this.getEme2000());
				final Attitude attitude2 = observationLaw.getAttitude(this.createDefaultPropagator(), date2,
						this.getEme2000());
				/*
				 * Now here is an example of code showing how to compute the duration of the
				 * slew from attitude1 to attitude2 Here we compare two Attitudes coming from
				 * the same AttitudeLaw which is a TargetGroundPointing so the
				 */
				final double slew12Duration = this.getSatellite().computeSlewDuration(attitude1, attitude2);
				logger.info("Maximum possible duration of the slew : " + slew12Duration);
				final double actualDuration = date2.durationFrom(date1);
				logger.info("Actual duration of the slew : " + actualDuration);
				/**
				 * Of course, here the actual duration is less than the maximum possible
				 * duration because the TargetGroundPointing mode is a very slow one and the
				 * Satellite is very agile. But sometimes when trying to perform a slew from one
				 * target to another, you will find that the Satellite doesn't have enough time,
				 * then you need to either translate one of the observations or just don't
				 * perform one of the observation.
				 */

				/**
				 * Let's say after comparing several observation slews, you find a valid couple
				 * of dates defining your observation window : {obsStart;obsEnd}, with
				 * obsEnd.durationFrom(obsStart) == ConstantsBE.INTEGRATION_TIME.
				 * 
				 * Then you can use those dates to create your AtittudeLawLeg that you will
				 * insert inside the observaiton pla, for this target. Reminder : only one
				 * observation in the observation plan per target !
				 * 
				 * WARNING : what we do here doesn't work, we didn't check that there wasn't
				 * another target observed while inserting this target observation, it's up to
				 * you to build your observation plan using the methods and tips we provide. You
				 * can also only insert one observation for each pass of the satellite and it's
				 * fine.
				 */
				// Here we use the middle of the accessInterval to define our dates of
				// observation
				final AbsoluteDate middleDate = accessInterval.getMiddleDate();
				final AbsoluteDate obsStart = middleDate.shiftedBy(-ConstantsBE.INTEGRATION_TIME / 2);
				final AbsoluteDate obsEnd = middleDate.shiftedBy(ConstantsBE.INTEGRATION_TIME / 2);
				final AbsoluteDateInterval obsInterval = new AbsoluteDateInterval(obsStart, obsEnd);
				// Then, we create our AttitudeLawLeg, that we name using the name of the target
				final String legName = "OBS_" + target.getName();
				final AttitudeLawLeg obsLeg = new AttitudeLawLeg(observationLaw, obsInterval, legName);

				// Finally, we add our leg to the plan
				this.observationPlan.put(target, obsLeg);

			}

		}

		return this.observationPlan;
	}

	/**
	 * [COPY-PASTE AND COMPLETE THIS METHOD TO ACHIEVE YOUR PROJECT]
	 * 
	 * Create an adapted instance of {@link EventDetector} matching the input need
	 * for monitoring the events defined by the X constraint. (X can be a lot of
	 * things).
	 * 
	 * You can copy-paste this method to adapt it to the {@link EventDetector} X
	 * that you want to create.
	 * 
	 * Note: this can have different inputs that we don't define here
	 * 
	 * @return An {@link EventDetector} answering the constraint (for example a
	 *         {@link SensorVisibilityDetector} for a visibility constraint).
	 */
	private EventDetector createConstraintXDetector() {
		/**
		 * Here you build an EventDetector object that corresponds to the constraint X:
		 * visibility of the target from the satellite, target is in day time, whatever.
		 *
		 * Note that when you create a detector, you choose the actions that it will
		 * perform when the target event is detected. See the module 5 for more
		 * informations about this.
		 * 
		 * Visibility: For the visibility detector, you can use a SensorModel. You will
		 * have to add the Earth as a masking body with the method
		 * addMaskingCelestialBody and to set the main target of the SensorModel with
		 * the method setMainTarget. Then, you can use the class
		 * SensorVisibilityDetector with your SensorModel.
		 * 
		 * Sun incidence: For the sun incidence angle detector (illumination condition),
		 * you can use the class ThreeBodiesAngleDetector, the three bodies being the
		 * ground target, the Earth and the Sun. See the inputs of this class to build
		 * the object properly.
		 * 
		 * Dazzling: Your satellite needs to be protected from dazzling. As a good
		 * approximation, dazzling is avoided if the angle satellite - target - the Sun
		 * is below the maximum phase angle (90 degrees, see {@link ConstantsBE}). The
		 * class ThreeBodiesAngleDetector is suitable for this condition as well.
		 * 
		 * Tip 1 : When you create the detectors listed above, you can use the two
		 * public final static fields MAXCHECK_EVENTS and TRESHOLD_EVENTS to configure
		 * the detector (those values are often asked in input of the EventDectector
		 * classes). You will also indicate the Action to perform when the detection
		 * occurs, which is Action.CONTINUE.
		 * 
		 * Tip 2 : The Satellite uses the Assembly class to represent its model. To
		 * access this Assembly, you have a getter in the Satellite class. Then, to
		 * access any part of an Assembly, you can call Assembly#getPart(String
		 * partName). The parts name for our Satellite are declared in the Satellite
		 * class.
		 * 
		 * Tip 3 : when you need an object which is an interface (let's say for example
		 * a PVCoordinatesProvider) you have to find a class implementing this interface
		 * and which models what you want to do (here which models the target's
		 * position/coordinates). To find all the classes implementing an interface :
		 * "Right Clic", then "Open Type Hierarchy". For example for a
		 * PVCoordinatesProvider, you have a lot of classes : AbstractCelestialBody if
		 * your target is a planet for example, or any Propagator if you are propagating
		 * the PV of a Target like a satellite, or TopocentricFrame if the target is a
		 * location at the surface of a celestial body, etc.
		 * 
		 * Tip 4 : be careful, Patrius methods always use angles in rad and not in deg.
		 * All BE constants are defined in deg for easier understading of the values. To
		 * convert degrees into rad, you can use the following method :
		 * MathLib.toRadians(double x)
		 * 
		 */
		/*
		 * Create your detector and return it.
		 */

		final Site site = this.getSiteList().get(0);
		final SensorModel sensor = new SensorModel(this.getSatellite().getAssembly(), Satellite.SENSOR_NAME);
		final TopocentricFrame target = new TopocentricFrame(this.getEarth(), site.getPoint(), site.getName());
		sensor.setMainTarget(target, new ConstantRadiusProvider(0.0));
		sensor.addMaskingCelestialBody(this.getEarth());
		return new SensorVisibilityDetector(sensor, MAXCHECK_EVENTS, TRESHOLD_EVENTS,
				EventDetector.Action.CONTINUE, EventDetector.Action.CONTINUE);
	}

	/**
	 * [COMPLETE THIS METHOD TO ACHIEVE YOUR PROJECT]
	 * 
	 * Create an observation leg, that is to say an {@link AttitudeLaw} that give
	 * the {@link Attitude} (pointing direction) of the {@link Satellite} in order
	 * to perform the observation of the input target {@link Site}.
	 * 
	 * An {@link AttitudeLaw} is an {@link AttitudeProvider} providing the method
	 * {@link AttitudeProvider#getAttitude()} which can be used to compute the
	 * {@link Attitude} of the {@link Satellite} at any given {@link AbsoluteDate}
	 * (instant) during the mission horizon.
	 * 
	 * An {@link AttitudeLaw} is valid at anu time in theory.
	 * 
	 * @param target Input target {@link Site}
	 * @return An {@link AttitudeLawLeg} adapted to the observation.
	 */
	private AttitudeLaw createObservationLaw(Site target) {
		/**
		 * To perform an observation, the satellite needs to point the target for a
		 * fixed duration.
		 * 
		 * Here, you will use the {@link TargetGroundPointing}. This law provides a the
		 * Attitude of a Satellite that only points one target at the surface of a
		 * BodyShape. The earth object from the SimpleMission is a BodyShape and we
		 * remind you that the Site object has an attribute which is a GeodeticPoint.
		 * Use those informations to your advantage to build a TargetGroundPointing.
		 * 
		 * Note : to avoid unusual behavior of the TargetGroundPointing law, we advise
		 * you use the following constructor : TargetGroundPointing(BodyShape, Vector3D,
		 * Vector3D, Vector3D) specifying the line of sight axis and the normal axis.
		 */
		/*
		 * Complete the code below to create your observation law and return it
		 */
		return new fr.cnes.sirius.patrius.attitudes.TargetGroundPointing(this.getEarth(), target.getPoint(),
				this.getSatellite().getSensorAxis(), this.getSatellite().getFrameXAxis());
	}

	/** Selects one 10-second observation per site with the level-2 slew margin. */
	private Map<Site, AttitudeLawLeg> computeObservationPlanLevel2() throws PatriusException {
		logger.info("============= Computing Observation Plan (level 2) =============");
		this.observationPlan.clear();
		if (this.accessPlan.isEmpty()) {
			this.computeAccessPlan();
		}

		final List<ObservationCandidate> candidates = new ArrayList<>();
		for (final Entry<Site, Timeline> entry : this.accessPlan.entrySet()) {
			final Site site = entry.getKey();
			final AttitudeLaw law = this.createObservationLaw(site);
			for (final Phenomenon access : entry.getValue().getPhenomenaList()) {
				final AbsoluteDateInterval window = access.getTimespan();
				if (window.getDuration() + 1.0e-9 < ConstantsBE.INTEGRATION_TIME) {
					continue;
				}
				final AbsoluteDate middle = window.getMiddleDate();
				final AbsoluteDate obsStart = middle.shiftedBy(-ConstantsBE.INTEGRATION_TIME / 2.0);
				final AbsoluteDate obsEnd = obsStart.shiftedBy(ConstantsBE.INTEGRATION_TIME);
				if (!window.contains(obsStart) || !window.contains(obsEnd)) {
					continue;
				}
				final AttitudeLawLeg leg = new AttitudeLawLeg(law, obsStart, obsEnd, "OBS_" + site.getName());
				final double contribution = this.computeFinalScore(java.util.Collections.singletonMap(site, leg));
				candidates.add(new ObservationCandidate(site, leg, contribution));
			}
		}

		// Highest estimated contribution first; ties are resolved deterministically.
		Collections.sort(candidates, new Comparator<ObservationCandidate>() {
			@Override
			public int compare(final ObservationCandidate left, final ObservationCandidate right) {
				final int byScore = Double.compare(right.score, left.score);
				if (byScore != 0) {
					return byScore;
				}
				final int byDate = left.leg.getDate().compareTo(right.leg.getDate());
				return byDate != 0 ? byDate : left.site.getName().compareTo(right.site.getName());
			}
		});

		final List<ObservationCandidate> selected = new ArrayList<>();
		final double slewMargin = this.getSatellite().getMaxSlewDuration();
		for (final ObservationCandidate candidate : candidates) {
			if (this.observationPlan.containsKey(candidate.site)) {
				continue;
			}
			final AbsoluteDate obsStart = candidate.leg.getDate();
			final AbsoluteDate obsEnd = candidate.leg.getEnd();
			if (obsStart.durationFrom(this.getStartDate()) < slewMargin
					|| this.getEndDate().durationFrom(obsEnd) < slewMargin) {
				continue;
			}

			int insertion = 0;
			while (insertion < selected.size()
					&& selected.get(insertion).leg.getDate().compareTo(obsStart) < 0) {
				insertion++;
			}
			final boolean hasPrevious = insertion > 0;
			final boolean hasNext = insertion < selected.size();
			if (hasPrevious && obsStart.durationFrom(selected.get(insertion - 1).leg.getEnd()) < slewMargin) {
				continue;
			}
			if (hasNext && selected.get(insertion).leg.getDate().durationFrom(obsEnd) < slewMargin) {
				continue;
			}
			selected.add(insertion, candidate);
			this.observationPlan.put(candidate.site, candidate.leg);
		}

		for (final ObservationCandidate candidate : selected) {
			logger.info("Observation " + candidate.site.getName() + " : " + candidate.leg.getDate() + " -> "
					+ candidate.leg.getEnd() + " (contribution estimée=" + candidate.score + ")");
		}
		logger.info("Observations retained : " + this.observationPlan.size());
		return this.observationPlan;
	}

	/** Builds a continuous nadir/observation/slew sequence over the mission horizon. */
	private StrictAttitudeLegsSequence<AttitudeLeg> computeCinematicPlanLevel2() throws PatriusException {
		logger.info("============= Computing Cinematic Plan (level 2) =============");
		this.cinematicPlan.clear();
		final AttitudeLaw nadir = this.getSatellite().getDefaultAttitudeLaw();
		final AbsoluteDate missionStart = this.getStartDate();
		final AbsoluteDate missionEnd = this.getEndDate();
		final KeplerianPropagator propagator = this.createDefaultPropagator();
		final List<Entry<Site, AttitudeLawLeg>> observations = new ArrayList<>(this.observationPlan.entrySet());
		Collections.sort(observations, new Comparator<Entry<Site, AttitudeLawLeg>>() {
			@Override
			public int compare(final Entry<Site, AttitudeLawLeg> left, final Entry<Site, AttitudeLawLeg> right) {
				return left.getValue().getDate().compareTo(right.getValue().getDate());
			}
		});

		if (observations.isEmpty()) {
			this.cinematicPlan.add(new AttitudeLawLeg(nadir, missionStart, missionEnd, "NADIR_MISSION"));
			return this.cinematicPlan;
		}

		final double maxSlew = this.getSatellite().getMaxSlewDuration();
		final Entry<Site, AttitudeLawLeg> first = observations.get(0);
		final AbsoluteDate firstStart = first.getValue().getDate();
		final AbsoluteDate firstSlewStart = firstStart.shiftedBy(-maxSlew);
		if (firstSlewStart.compareTo(missionStart) > 0) {
			this.cinematicPlan.add(new AttitudeLawLeg(nadir, missionStart, firstSlewStart,
					"NADIR_BEFORE_" + first.getKey().getName()));
		}
		final Attitude initialNadir = nadir.getAttitude(propagator, firstSlewStart, this.getEme2000());
		final Attitude firstObservation = first.getValue().getAttitude(propagator, firstStart, this.getEme2000());
		this.cinematicPlan.add(new ConstantSpinSlew(initialNadir, firstObservation, maxSlew,
				"SLEW_NADIR_TO_" + first.getKey().getName()));
		this.cinematicPlan.add(first.getValue());

		Entry<Site, AttitudeLawLeg> previous = first;
		for (int index = 1; index < observations.size(); index++) {
			final Entry<Site, AttitudeLawLeg> next = observations.get(index);
			final AbsoluteDate previousEnd = previous.getValue().getEnd();
			final AbsoluteDate nextStart = next.getValue().getDate();
			final double gap = nextStart.durationFrom(previousEnd);
			final Attitude previousAttitude = previous.getValue().getAttitude(propagator, previousEnd,
					this.getEme2000());
			final Attitude nextAttitude = next.getValue().getAttitude(propagator, nextStart, this.getEme2000());

			if (gap > 2.0 * maxSlew) {
				final AbsoluteDate firstNadirDate = previousEnd.shiftedBy(maxSlew);
				final AbsoluteDate secondSlewDate = nextStart.shiftedBy(-maxSlew);
				final Attitude firstNadir = nadir.getAttitude(propagator, firstNadirDate, this.getEme2000());
				final Attitude secondNadir = nadir.getAttitude(propagator, secondSlewDate, this.getEme2000());
				this.cinematicPlan.add(new ConstantSpinSlew(previousAttitude, firstNadir, maxSlew,
						"SLEW_" + previous.getKey().getName() + "_TO_NADIR"));
				this.cinematicPlan.add(new AttitudeLawLeg(nadir, firstNadirDate, secondSlewDate,
						"NADIR_BETWEEN_" + previous.getKey().getName() + "_AND_" + next.getKey().getName()));
				this.cinematicPlan.add(new ConstantSpinSlew(secondNadir, nextAttitude, maxSlew,
						"SLEW_NADIR_TO_" + next.getKey().getName()));
			} else {
				this.cinematicPlan.add(new ConstantSpinSlew(previousAttitude, nextAttitude, gap,
						"SLEW_" + previous.getKey().getName() + "_TO_" + next.getKey().getName()));
			}
			this.cinematicPlan.add(next.getValue());
			previous = next;
		}

		final AbsoluteDate lastEnd = previous.getValue().getEnd();
		final AbsoluteDate lastSlewEnd = lastEnd.shiftedBy(maxSlew);
		final Attitude lastObservation = previous.getValue().getAttitude(propagator, lastEnd, this.getEme2000());
		final Attitude finalNadir = nadir.getAttitude(propagator, lastSlewEnd, this.getEme2000());
		this.cinematicPlan.add(new ConstantSpinSlew(lastObservation, finalNadir, maxSlew,
				"SLEW_" + previous.getKey().getName() + "_TO_NADIR"));
		if (lastSlewEnd.compareTo(missionEnd) < 0) {
			this.cinematicPlan.add(new AttitudeLawLeg(nadir, lastSlewEnd, missionEnd,
					"NADIR_AFTER_" + previous.getKey().getName()));
		}
		return this.cinematicPlan;
	}

	/**
	 * [COMPLETE THIS METHOD TO ACHIEVE YOUR PROJECT]
	 * 
	 * This method should compute the input {@link Site}'s access {@link Timeline}.
	 * That is to say the {@link Timeline} which contains all the {@link Phenomenon}
	 * respecting the access conditions for this site : good visibility + corrrect
	 * illumination of the {@link Site}.
	 * 
	 * For that, we suggest you create as many {@link Timeline} as you need and
	 * combine them with logical gates to filter only the access windows phenomenon.
	 * 
	 * @param targetSite Input target {@link Site}
	 * @return The {@link Timeline} of all the access {@link Phenomenon} for the
	 *         input {@link Site}.
	 * @throws PatriusException If a {@link PatriusException} occurs.
	 */
	private Timeline createSiteAccessTimeline(Site targetSite, CodedEventsLogger visibilityLogger,
			CodedEventsLogger sunIncidenceDetector, CodedEventsLogger dazzlingDetector) throws PatriusException {

		/**
		 * Step 1 :
		 * 
		 * Create one Timeline per constraint you want to monitor.
		 */
		final Timeline visibilityTimeline = new Timeline(visibilityLogger,
				new AbsoluteDateInterval(this.getStartDate(), this.getEndDate()), null);
		final Timeline sunIncidenceTimeline = new Timeline(sunIncidenceDetector,
				new AbsoluteDateInterval(this.getStartDate(), this.getEndDate()), null);
		final Timeline dazzlingTimeline = new Timeline(dazzlingDetector,
				new AbsoluteDateInterval(this.getStartDate(), this.getEndDate()), null);

		/**
		 * Step 2 :
		 * 
		 * Combine the 3 timelines with logical gates and retrieve only the access
		 * conditions through a refined Timeline object.
		 * 
		 * For that, you can use the classes in the events.postprocessing module : for
		 * example, the AndCriterion or the NotCriterion.
		 * 
		 * Finally, you can filter only the Phenomenon matching a certain condition
		 * using the ElementTypeFilter
		 */
		/*
		 * Code your logical operations on Timeline objects and filter only the access
		 * Phenomenon (gathering all constraints you need to define an access condition)
		 * below.
		 */
		// Combining all Timelines
		// Creating a global Timeline containing all phenomena, this Timeline will be
		// filtered and processed to that only the access Phenomennon remain, this is
		// our siteAccessTimeline
		final Timeline siteAccessTimeline = new Timeline(
				new AbsoluteDateInterval(this.getStartDate(), this.getEndDate()));
		// Adding the phenomena of all the considered timelines
		for (final Phenomenon phenom : visibilityTimeline.getPhenomenaList()) {
			siteAccessTimeline.addPhenomenon(phenom);
		}
		for (final Phenomenon phenom : sunIncidenceTimeline.getPhenomenaList()) {
			siteAccessTimeline.addPhenomenon(phenom);
		}
		for (final Phenomenon phenom : dazzlingTimeline.getPhenomenaList()) {
			siteAccessTimeline.addPhenomenon(phenom);
		}

		/*/
		 * Complete the code below
		 */
		// Define and use your own criteria, here is an example (use the right strings
		// defined when naming the phenomenon in the GenericCodingEventDetector)
		final AndCriterion visibilityAndSun = new AndCriterion(VISIBILITY_CODE, SUN_INCIDENCE_CODE,
				VISIBILITY_AND_SUN_CODE, "Visible and sufficiently illuminated target");
		visibilityAndSun.applyTo(siteAccessTimeline);
		final AndCriterion allConstraints = new AndCriterion(VISIBILITY_AND_SUN_CODE, PHASE_ANGLE_CODE,
				ACCESS_CODE, "Visible, illuminated and phase-angle-safe access");
		allConstraints.applyTo(siteAccessTimeline);

		// Then create an ElementTypeFilter that will filter all phenomenon not
		// respecting the input condition you gave it
		final ElementTypeFilter obsConditionFilter = new ElementTypeFilter(ACCESS_CODE, false);
		// Finally, we filter the global timeline to keep only X1 AND X2 phenomena
		obsConditionFilter.applyTo(siteAccessTimeline);

		/*
		 * Now make sure your globalTimeline represents the access Timeline for the
		 * input target Site and it's done ! You can print the Timeline using the
		 * utility module of the BE as below
		 */

		// Log the final access timeline associated to the current target
		logger.info("\n" + targetSite.getName());
		ProjectUtils.printTimeline(siteAccessTimeline);

		return siteAccessTimeline;
	}

	/**
	 * [COPY-PASTE AND COMPLETE THIS METHOD TO ACHIEVE YOUR PROJECT]
	 * 
	 * This method should create a {@link CodedEventsLogger} object which be used to
	 * log all events computed for a given constraint. This logger can then be used
	 * to create a {@link Timeline} after propagation by logging all events linked
	 * with the constraint in the {@link Timeline}
	 * 
	 * You can copy-paste this method and adapt it for every X constraint you need
	 * to implement. The global process described here stays the same.
	 * 
	 * @param targetSite Input target {@link Site}
	 * @return The {@link CodedEventsLogger} used to monitor all events linked with
	 *         the chosen constraint
	 */
	private CodedEventsLogger createSiteXConstraintLogger(Site targetSite) {
		/**
		 * More context : here is a quick idea of how to create your logger to monitor a
		 * given constraint in order to the associated Timeline. A Timeline contains a
		 * PhenomenaList, which is list of Phenomenon objects. Each Phenomenon object
		 * represents a phenomenon in orbit which is defined between two AbsoluteDate
		 * objects and their associated CodedEvent which define the begin and the end of
		 * the Phenomenon. For example, the Sun visibility can be defined as a
		 * phenomenon beginning with the start of visibility and ending with the end of
		 * visibility, itself defined using geometrical rules.
		 */

		/**
		 * Step 1 :
		 * 
		 * Here we deal with event detection. As explain in the module 05, this is done
		 * with EventDetector objects. If you look at the Javadoc, you'll find all sorts
		 * of detectors. You need to translate the X input constraint (for example an
		 * incidence angle between the sensor and the target, sun incidence angle,
		 * masking of the target by the Earth, etc.) into an EventDetector object.
		 * Scroll through the event detection modules to find the one adapted to your
		 * problem (represented by the X constraint which describe the X phenomenon you
		 * want to detect) and then look at the inputs you need to build it.
		 * 
		 * Please note that in order to facilitate the task for you, we provide the
		 * object Satellite. If you look how the constructor build this object, you will
		 * find that our Satellite already has an Assembly filled with a lot of
		 * properties. Among those properties, there is a SensorProperty that you can
		 * use to your advantage when trying to build you detector (for example when
		 * trying to build a visibility detector). See the module 7 of the formation to
		 * learn more about the Assembly object. You can use the SensorProperty via the
		 * Assembly of the Satellite and its name to define appropriate detectors.
		 * 
		 */
		/*
		 * Complete the method below to build your detector. More indications are given
		 * in the method. Here you can create on createConstraintXDetector method for
		 * each kind of detector and then create your detector using your method, the
		 * process is generic.
		 */
		final EventDetector constraintXDetector = createConstraintXDetector();

		/**
		 * Step 2 :
		 * 
		 * When you have your detector, you can add it on an Orbit Propagator such as
		 * the KeplerianPropagator of your Satellite. If you give the detector the right
		 * parameters, you can then propagate the orbit (see the SimpleMission code and
		 * the module 03 from the Patrius formation) and the detector will automatically
		 * perform actions when a particular orbital event happens (you need to
		 * configure the right detector to detect the event you want to monitor).
		 * 
		 * You can add several detectors to the propagator (one per constraint per Site
		 * for example).
		 */
		/*
		 * This is how you add a detector to a propagator, feel free to add several
		 * detectors to the satellite propagator, enabling you to propagate all the
		 * detector in parallel.
		 */
		this.getSatellite().getPropagator().addEventDetector(constraintXDetector);

		/**
		 * Step 3 :
		 * 
		 * Now you need to use the detector to create CodedEvent objects to actually
		 * detect the events and visualize them. You can obtain CodedEvents with a
		 * CodedEventsLogger that you plug on an EventDetector with the
		 * CodedEventsLogger.monitorDetector() method. For that, you will need the
		 * GenericCodingEventDetector class. See the module 09 to understand how to use
		 * those objects in order to detect events.
		 */
		/*
		 * Modify the code below to create your GenericCodingEventDetector and use it
		 * to create a CodedEventsLogger here. Adapt the inputs for your constraint.
		 */
		final GenericCodingEventDetector codingEventXDetector = new GenericCodingEventDetector(constraintXDetector,
				"Event starting the X phenomenon", "Event ending the X phenomenon", true, "Name of the X phenomenon");
		final CodedEventsLogger eventXLogger = new CodedEventsLogger();
		final EventDetector eventXDetector = eventXLogger.monitorDetector(codingEventXDetector);
		// Then you add your logger to the propagator, it will monitor the event coded
		// by the codingEventDetector
		this.getSatellite().getPropagator().addEventDetector(eventXDetector);

		return eventXLogger;
	}

	/**
	 * @return the accessPlan
	 */
	public Map<Site, Timeline> getAccessPlan() {
		return this.accessPlan;
	}

	/**
	 * @return the cinematicPlan
	 */
	public StrictAttitudeLegsSequence<AttitudeLeg> getCinematicPlan() {
		return this.cinematicPlan;
	}

	/**
	 * @return the observationPlan
	 */
	public Map<Site, AttitudeLawLeg> getObservationPlan() {
		return this.observationPlan;
	}

	@Override
	public String toString() {
		return "CompleteMission [name=" + this.getName() + ", startDate=" + this.getStartDate() + ", endDate="
				+ this.getEndDate() + ", satellite=" + this.getSatellite() + "]";
	}
}
