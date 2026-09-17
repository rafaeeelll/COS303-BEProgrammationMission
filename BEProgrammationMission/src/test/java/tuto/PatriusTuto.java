package tuto;

import java.io.File;
import java.util.Arrays;

import fr.cnes.sirius.addons.patriusdataset.PatriusDataset;
import fr.cnes.sirius.patrius.attitudes.Attitude;
import fr.cnes.sirius.patrius.attitudes.BodyCenterGroundPointing;
import fr.cnes.sirius.patrius.bodies.ExtendedOneAxisEllipsoid;
import fr.cnes.sirius.patrius.bodies.GeodeticPoint;
import fr.cnes.sirius.patrius.bodies.OneAxisEllipsoid;
import fr.cnes.sirius.patrius.events.CodedEventsLogger;
import fr.cnes.sirius.patrius.events.GenericCodingEventDetector;
import fr.cnes.sirius.patrius.events.postprocessing.AndCriterion;
import fr.cnes.sirius.patrius.events.postprocessing.ElementTypeFilter;
import fr.cnes.sirius.patrius.events.postprocessing.Timeline;
import fr.cnes.sirius.patrius.frames.Frame;
import fr.cnes.sirius.patrius.frames.FramesFactory;
import fr.cnes.sirius.patrius.frames.TopocentricFrame;
import fr.cnes.sirius.patrius.frames.transformations.TIRFProvider;
import fr.cnes.sirius.patrius.math.geometry.euclidean.threed.Vector3D;
import fr.cnes.sirius.patrius.math.util.FastMath;
import fr.cnes.sirius.patrius.orbits.CircularOrbit;
import fr.cnes.sirius.patrius.orbits.KeplerianOrbit;
import fr.cnes.sirius.patrius.orbits.Orbit;
import fr.cnes.sirius.patrius.orbits.PositionAngle;
import fr.cnes.sirius.patrius.orbits.pvcoordinates.PVCoordinates;
import fr.cnes.sirius.patrius.orbits.pvcoordinates.PVCoordinatesProvider;
import fr.cnes.sirius.patrius.propagation.BoundedPropagator;
import fr.cnes.sirius.patrius.propagation.Propagator;
import fr.cnes.sirius.patrius.propagation.SpacecraftState;
import fr.cnes.sirius.patrius.propagation.analytical.KeplerianPropagator;
import fr.cnes.sirius.patrius.propagation.events.DistanceDetector;
import fr.cnes.sirius.patrius.propagation.events.EventDetector;
import fr.cnes.sirius.patrius.propagation.events.EventDetector.Action;
import fr.cnes.sirius.patrius.time.AbsoluteDate;
import fr.cnes.sirius.patrius.time.AbsoluteDateInterval;
import fr.cnes.sirius.patrius.time.DateTimeComponents;
import fr.cnes.sirius.patrius.time.TimeScale;
import fr.cnes.sirius.patrius.time.TimeScalesFactory;
import fr.cnes.sirius.patrius.utils.Constants;
import fr.cnes.sirius.patrius.utils.exception.PatriusException;
import fr.cnes.sirius.patrius.utils.exception.PropagationException;
import utils.ConstantsBE;
import utils.ProjectUtils;
import utils.VTSTools;

/**
 * This class is a simplified tutorial for Patrius. Here we explore in a very
 * synthetic way all the main Patrius classes and methods that are useful to
 * complete the BEProgrammationMission.
 * 
 * It contains a series of "private static void" methods that can be executed in
 * the "main" in order to manipulate different aspects of the Patrius library.
 * 
 * Simply complete the code of each tutorial method and execute it to understand
 * the basics of Patrius.
 * 
 */
public class PatriusTuto {

	/**
	 * This method loads the Patrius Dataset. It must be done before executing other
	 * methods to supply Patrius with all models and ressources
	 */
	private static void loadRessources() {
		// Loading Patrius Dataset resources
		// Note that the PatriusDataset is necessary to provide all the physical context
		// (offsets bewteen Timescales, definition of the Frames, basic ephemeris of the
		// Celestial bodies, etc.
		PatriusDataset.addResourcesFromPatriusDataset();
	}

	/**
	 * This tutorial targets the manipulation of the {@link AbsoluteDate} and
	 * {@link TimeScale} objects.
	 * 
	 * @throws PatriusException If the UTC {@link TimeScale} cannot be retrieved.
	 */
	private static void tuto1TimeScales() throws PatriusException {
		// Get the UTC TimeScale using the TimeScalesFactory
		final TimeScale utc = TimeScalesFactory.getUTC();

		// Create an AbsoluteDate from the date "2025-07-21T10:23:00" (ISO-8601
		// standard), in the UTC Timescale
		final String dateString = // your code here
		final AbsoluteDate dateUtc = // your code here

		// Print your date using toString(), default toString() uses TAI Timescale
		System.out.println("Date (TAI) : " + // your code here );

		// Now print your date in the UTC Timescale using toString(utc), you should see
		// the difference
		System.out.println("Date (UTC) : " + // your code here );

		// Now create a date 10 seconds after your date, using your date and the
		// AbsoluteDate methods, and print it.
		final AbsoluteDate shiftedDate = // your code here
		System.out.println("Shifted date (UTC) : " + // your code here

		// Print the duration between the to dates
		// Use AbsoluteDate methods to compute this duration
		System.out.println("Shifted date duration from date : " + // your code here

		// Create an AbsoluteDateInterval using both dates
		final AbsoluteDateInterval interval = // your code here

		// Check if your date is in the interval using AbsoluteDateInterval methods
		System.out.println("Is date in interval ? " + // your code here

		// You can explore further using the AbsoluteDate constructors and method and
		// other associated classes methods
	}

	/**
	 * This tutorial targets the manipulation of the {@link Frame} objects, with an
	 * introduction to the {@link PVCoordinates} object.
	 * 
	 * @throws PatriusException If the UTC {@link TimeScale} cannot be retrieved.
	 */
	private static void tuto2Frames() throws PatriusException {
		// First, create the "2000-01-01T00:00:00.000" date in UTC TimeScale
		// We will use this date later
		final TimeScale utc = // your code here
		final AbsoluteDate date = // your code here

		// Then you are going to create the Earth model and its ITRF attached Frame
		// Simplified Earth constants, will be used to create the Earth model
		final double ae = Constants.WGS84_EARTH_EQUATORIAL_RADIUS;
		final double f = 0;

		// Get the ITRF Frame that will be used for the Earth attached Frame using FramesFactory
		final Frame itrf = // your code here

		// Check if the ITRF Frame is inertial using Frames methods
		System.out.println("Is ITRF pseudo-inertial ? " + // your code here

		// Create the Earth shape using the ExtendedOneAxisEllipsoid object and the
		// previous objects
		final ExtendedOneAxisEllipsoid earth = // your code here

		// Now you are going to create the geodetic point of Toulouse at the surface of
		// the Earth

		// Toulouse coordinates and altitude. Hint : latitude is 43.617°, longitude is
		// 1.450° and altitude is 200m.
		// Careful : angles must be converted to radians, you can use
		// FastMath.toRadians(angle)
		final double lat = // your code here
		final double lon = // your code here
		final double alt = // your code here

		// Now, create Toulouse as a GeodeticPoint object
		final GeodeticPoint tls = // your code here

		// Create a TopocentricFrame using the GeodeticPoint of Toulouse
		final TopocentricFrame tlsFrame = // your code here

		// Now you have a Toulouse model in ITRF, we are going to compare its
		// coordinates in ITRF to the coordinates in EME2000 Frame

		// Get the EME2000 frame using FramesFactory
		final Frame eme2000 = // your code here

		// Check if the ITRF Frame is inertial
		System.out.println("Is EME200 pseudo-inertial ? " + // your code here

		// Get the PV coordinates of the Toulouse frame in the EME2000 frame at the
		// created date. Use the fact that tlsFrame is a TopocentricFrame and thus is also 
		// a PVCoordinatesProvider
		// Don't forget to provide the target Frame when getting PVCoordinates 
		final PVCoordinates pvEme2000 = // your code here

		// Compute the speed's norm using methods on the PVCoordinates object 
		final double velEme2000 = // your code here

		// Print the coordinates and speed in EME2000
		System.out.println("Toulouse complete coordinates vetor (EME2000): " + pvEme2000.toString());
		System.out.println("Toulouse velocity norm (EME2000): " + velEme2000);

		// Calculate the expected speed using the Earth rotational rate and basic geometry
		final double exp = // your code here
		System.out.println("Expected velocity from analytical calculation : " + exp);

		// Now get Toulouse's PVCoordinates in the ITRF frame
		final PVCoordinates pvItrf = // your code here

		// Get the computed velocity
		final Vector3D velItrf = // your code here

		// Print the coordinates and velocity in ITRF
		System.out.println("Toulouse complete coordinates vetor (ITRF): " + pvItrf.toString());
		System.out.println("Toulouse velocity norm (EME2000): " + velItrf);

		// Check that Toulouse is not moving in the ITRF Frame
	}

	/**
	 * This tutorial targets the manipulation of {@link Orbit}, {@link Propagator}
	 * and {@link PVCoordinatesProvider} objects in a strictly Keplerian case.
	 * 
	 * @throws PropagationException If an error occurs during the orbit propagation
	 */
	private static void tuto3KeplerianPropagation() throws PropagationException {

		// In this method, we are going to see how to create a simple Keplerian orbit
		// and propagate it between two dates

		// Constants
		final double mu = Constants.GRIM5C1_EARTH_MU;

		// Orbital parameters
		final double a = 42164173.550572;
		final double e = .41;
		final double i = FastMath.toRadians(63.388);
		final double pa = FastMath.toRadians(270);
		final double raan = FastMath.toRadians(188);
		final double w = 0;

		// Initial date of the orbit propagation : create an AbsoluDate in TAI TimeScale
		// representing the date "2000-01-01T06:00:00.000"
		final AbsoluteDate initialDate = // your code here);

		// Get the EME2000 Reference frame
		final Frame eme2000 = // your code here);

		// Create a KeplerianOrbit object at initial date using all the previously built
		// objects and variables (see KeplerianOrbit constructors)
		final KeplerianOrbit initialOrbit = // your code here);

		// Get the orbital period and print it
		final double orbitalPeriod = // your code here);
		System.out.println("Orbital period [s] : " + orbitalPeriod);

		// Print initial position/velocity/acceleration. use tha fact that initialOrbit is 
		// also a PVCoordinatesProvider
		final PVCoordinates initialPv = // your code here
		System.out.println("Initial coordinates : " + initialPv);

		// Create the final date for propagation, using a date shifted by a number of
		// periods
		final double numberOfPeriods = 10.0;
		final AbsoluteDate finalDate = // your code here

		// Now create a KeplerianPropagator object using the initial orbit
		final KeplerianPropagator propagator = // your code here

		// Propagate the orbit from the initial to the final date. Any intermediate
		// state is not stored. Use the propagate() method of your propagator
		final SpacecraftState result = // your code here

		// Get the final PVCoordinates and print it
		final PVCoordinates finalPv = // your code here
		System.out.println("Final coordinates : " + finalPv);

		// Compute and print the difference between the initial and final PVCoordinates
		// Use the methods of the Vector3D class to compare Vector3D instances
		final Vector3D posDiff = // your code here
		System.out.println("Difference : " + posDiff + " ; Norm of the difference : " + // your code here);

		// Try to modify numberOfPeriods and check the behavior is this number is an int
		// or not (complete revolutions vs partial revolutions)

	}

	/**
	 * This tutorial method helps us understand how to propagate an {@link Orbit}
	 * using a {@link KeplerianPropagator} and then visualize the results as a
	 * tabulated ephemeris.
	 * 
	 * @throws PatriusException If the ITRF {@link Frame} cannot be built due to
	 *                          missing data or if a propagation error occurs
	 */
	private static void tuto4EphemerisProduction() throws PatriusException {
		// Declaring the orbital parameters
		final double a = 42164173.550572;
		final double e = .41;
		final double i = FastMath.toRadians(63.388);
		final double pa = FastMath.toRadians(270);
		final double raan = FastMath.toRadians(188);
		final double w = 0;

		// Earth
		final double ae = Constants.GRIM5C1_EARTH_EQUATORIAL_RADIUS;
		final double f = Constants.GRIM5C1_EARTH_FLATTENING;
		final double mu = Constants.GRIM5C1_EARTH_MU;

		// Create a OneAxisEllipsoid object for the Earth model
		final OneAxisEllipsoid earth = // your code here

		// Attitude law = here our target object always points the center of the Earth
		// Create the BodyCenterGroundPointing object which is an AttitudeLaw pointing
		// the center of the input BodyShape object
		final BodyCenterGroundPointing law = // your code here

		// Create the initial Date using TAI TimeScale : "2000-01-01T06:00:00.000"
		final AbsoluteDate initialDate = // your code here

		// Reference frame (inertial)
		final Frame eme2000 = // your code here

		// Create the initial Orbit using the KeplerianOrbit object (see KeplerianOrbit
		// constructors)
		final KeplerianOrbit initialOrbit = // your code here

		// Get the orbital period and print it
		final double orbitalPeriod = // your code here
		System.out.println("Orbital period [s] : " + orbitalPeriod);

		// Create the final date for propagation, using a date shifted by a number of
		// periods
		final double numberOfPeriods = 10.0;
		final AbsoluteDate finalDate = // your code here

		// Create the Keplerian Propagator using both the initial Orbit and the
		// previously created attitude law (see KeplerianPropagator constructors)
		final KeplerianPropagator propagator = // your code here

		// Propagate from initial date to final date
		// your code here

		// Now you are going to print all the intermediate coordinates
		// Declare local variables for printing
		PVCoordinates pv;
		double t, x, y, z, vx, vy, vz;
		Attitude att;
		final String sep = "  ";
		AbsoluteDate currentDate = initialDate;

		// Printing the labels for each column
		System.out.println("Time [s] | x [m] | y [m] | z [m] | vx [m/s] | vy [m/s] | vz [m/s] | [quaternions]");

		// For loop to print intermediate states
		while (currentDate.offsetFrom(finalDate, tai) < 0) {

			// Getting current date's PVCoordinates using the propagator
			// For that, propagate to the current date and get the PVCoordinates
			pv = // your code here

			// Getting the number of seconds elapsed since initial date
			t = currentDate.offsetFrom(initialDate, tai);

			// Getting all the coordinates
			x = pv.getPosition().getX();
			y = pv.getPosition().getY();
			z = pv.getPosition().getZ();
			vx = pv.getVelocity().getX();
			vy = pv.getVelocity().getX();
			vz = pv.getVelocity().getX();

			// Computing the current attitude
			// For that, use the getAttitude(final PVCoordinatesProvider pvProv,
            // final AbsoluteDate date, final Frame frame) method of the propagator's 
			// AttitudeProvider (BodyCenterGroundPointing). The pvProv is the 
			// propagator's PVCoordinatesProvider and you have to use the eme2000
			// Frame
			att = //your code here

			// Creating current date's line
			final StringBuffer bf = new StringBuffer();
			bf.append(t);
			bf.append(sep);
			bf.append(x);
			bf.append(sep);
			bf.append(y);
			bf.append(sep);
			bf.append(z);
			bf.append(sep);
			bf.append(vx);
			bf.append(sep);
			bf.append(vy);
			bf.append(sep);
			bf.append(vz);
			bf.append(sep);
			bf.append(Arrays.toString(att.getRotation().getQi()));
			bf.append(sep);

			// Printing current date's coordinates
			System.out.println(bf.toString());

			// Updating the current date (1 hour shift)
			currentDate = currentDate.shiftedBy(3600.);
		}

	}

	private static void tuto5EventDetection() throws PatriusException {
		/**
		 * First we create an Orbit and an AttitudeLaw for propagation as you did in
		 * tuto4. Here, the code is already complete so you can read this part for your
		 * understanding and skip to the next blue comment.
		 * 
		 * ________________________________________________________________________
		 */

		// Orbital parameters
		// Keplerian orbital parameters for a sun-synchronous orbit like Pleiades orbit
		final double a = Constants.WGS84_EARTH_EQUATORIAL_RADIUS + ConstantsBE.ALTITUDE;
		final double i = FastMath.toRadians(ConstantsBE.INCLINATION);
		final double raan = FastMath.toRadians(ConstantsBE.ASCENDING_NODE_LONGITUDE);
		final double e = FastMath.toRadians(ConstantsBE.MEAN_ECCENTRICITY);

		// TAI TimeScale
		final TimeScale tai = TimeScalesFactory.getTAI();

		// Earth
		final double ae = Constants.GRIM5C1_EARTH_EQUATORIAL_RADIUS;
		final double f = Constants.GRIM5C1_EARTH_FLATTENING;
		final double mu = Constants.GRIM5C1_EARTH_MU;
		final OneAxisEllipsoid earth = new OneAxisEllipsoid(ae, f, FramesFactory.getITRF());

		// Attitude law = here our propagated target (satellite) object always points
		// the center of the Earth
		final BodyCenterGroundPointing law = new BodyCenterGroundPointing(earth);

		// Initial Date
		final AbsoluteDate initialDate = new AbsoluteDate(2000, 1, 1, 6, 0, 0, tai);

		// Reference frame (inertial)
		final Frame eme2000 = FramesFactory.getEME2000();

		// Initial orbit, we use a simple circular orbit with the CircularOrbit object
		final CircularOrbit initialOrbit = new CircularOrbit(a, e, e, i, raan, 0.0, PositionAngle.TRUE, eme2000,
				initialDate, mu);

		// Orbital period
		final double orbitalPeriod = initialOrbit.getKeplerianPeriod();
		System.out.println("Orbital period (seconds) : " + orbitalPeriod);

		// Final date
		final double numberOfPeriods = 20.0;
		final AbsoluteDate finalDate = initialDate.shiftedBy(orbitalPeriod * numberOfPeriods);

		// Create the Keplerian Propagator
		final KeplerianPropagator propagator = new KeplerianPropagator(initialOrbit, law);

		// Constants for event detection
		// Maximum checking interval [s] for the event detection during the orbit
		// propagation
		final double maxcheck = 10.0;
		// Default convergence threshold [s] for the event computation during the orbit
		// propagation
		final double treshold = 1.e-4; //

		/**
		 * ___________________________________________________________________
		 *
		 * Now you are going to manipulate events detection during the orbital
		 * propagation. For this simplified scenario you are going to create 2
		 * DistanceDetector objects that will detect each time their target (Toulouse
		 * and Paris) are in a certain range. From that event detection you will log the
		 * events in a Timeline (one for each city) and then combine those Timelines to
		 * obtain a list of events indicating when both cities are in range of the
		 * satellite (represented by its position on the orbit). Finally, using VTS, you
		 * will be able to visualize the satellite's position and check if the event
		 * detection is persistent visually.
		 * 
		 * 
		 */

		// You are going to create a pair of DistanceDetector objects for the city of
		// Toulouse and Paris

		// First, declare the target detection distance at 2000000m (2000km) as a double
		// which we will use later
		final double targetDistance = // your code here

		// Now, create a TopocentricFrame object for Toulouse, which will serve as an
		// instance of PVCoordinatesProvider for Toulouse, based on the GeodeticPoint
		// object, as in previous tutorials.
		// Hint : Toulouse coordinates and altitude. Latitude is 43.617°,
		// longitude is 1.450° and altitude is 200m.
		// Careful : angles must be converted to radians, you can use
		// FastMath.toRadians(angle)
		final double latToulouse = // your code here
		final double lonToulouse = // your code here
		final double altToulouse = // your code here
		final TopocentricFrame toulousePvProv = // your code here

		// Now you should be able to create your DistanceDetector (see constructors)
		// Hint : the ACTION when the event is detected is to CONTINUE propagation
		final DistanceDetector distanceDetectorToulouse = // your code here

		// Then you can add the detector to the propagator using
		// AbstractPropagator#addEventDetector() method
		// your code here

		// Then you can create a GenericCodingEventDetector to log the events detected.
		// Explore the given constructors to use the most appropriate one.
		// Hints : you need to create a code (String) which is the label for the
		// increasing of the g function of the input detector and one for the decreasing
		// of g. Increasing and decreasing of g function will become your start/end
		// events based on the value of increasingIsStartIn. The last String
		// phenomenonCodeIn is the name of the phenomenon formed by the 2 events
		// [start_event => end_event].
		// Here the events are linked with the distance between the target (Toulouse) and
		// the satellite represented by the propagator 
		final GenericCodingEventDetector codingEventDetectorToulouse = // your code here

		// Create a CodedEventsLogger using the simple constructor
		final CodedEventsLogger eventLoggerToulouse = // your code here

		// Tell this object to monitor the previously created DistanceDetector 
		// using CodedEventsLogger#monitorDetector(),
		// thus you should be able to get a new instance of EventDetector
		// which will be used to log any Event linked with the DistanceDetector
		final EventDetector eventLoggingDetectorToulouse = // your code here

		// Finally, add your new EventDetector to the propagator
		// your code here

		// Create a DistanceDetector for Paris as you did with Toulouse and create the
		// associated DistanceDetector, GenericCodingEventDetector, CodedEventsLogger
		// and EventDetector that you will also add to the propagator
		// Hint : Paris coordinates and altitude. Latitude is 48.86°,
		// longitude is 2.34445° and altitude can be taken around 50m.
		final double latParis = // your code here
		final double lonParis = // your code here
		final double altParis = // your code here
		final TopocentricFrame parisPvProv = // your code here
		final DistanceDetector distanceDetectorParis = // your code here
		// Add the detector to the propagator
		// your code here
		// Then create a GenericCodingEventDetector to log the events detected
		final GenericCodingEventDetector codingEventDetectorParis = // your code here
		final CodedEventsLogger eventLoggerParis = // your code here
		final EventDetector eventLoggingDetectorParis = // your code here
		// And add the EventDetector for logging to the propagator 
		// your code here

		// Finally, you can propagate between initial and final date.
		// your code here
		System.out.println("Propagation interval : " + initialDate.toString() + " - " + finalDate.toString());

		// Finally you can create a Timeline from all events logged during propagation
		// for each logger : one for Toulouse and one for Paris
		// For that, use the appropriate Timeline constructor and your
		// event logger (EventDetector), you can put null for the SpacecraftState and
		// take the propagation interval for the inputs.
		final Timeline timelineToulouse = // your code here
		
		// Print the timeline using ProjectUtils#printTimeline()
		ProjectUtils.printTimeline(timelineToulouse);
		final Timeline timelineParis = // your code here
		ProjectUtils.printTimeline(timelineParis);

		// Now, create a global Timeline with all events by merging one into the other
		// Hint : you should find a method to do that among the Timeline object's
		// methods
		final Timeline globalTimeline = // your code here
		// Merge Toulouse's timeline into the global Timeline
		// your code here
		// Merge Paris's timeline into the global Timeline
		// your code here

		// Then try to combine phenomena when both Toulouse AND Paris are in range in
		// the global Timeline.
		// Hint : use a AndCriterion and the method AndCriterion#applyTo()
		// That action should add all "and events" to the global timeline, you can
		// verify using the printTimeline method.
		// Careful : the codes of the events must be the same as the ones 
		// used previously when creating the GenericCodingEventDetector instances
		final AndCriterion andCritetion = // your code here
		// Apply the AndCriterion to your Timeline 
		// your code here
		ProjectUtils.printTimeline(globalTimeline);

		// Then try to filter only phenomena when both cities are in range
		// Hint : use an ElementTypeFilter object and its applyTo() method
		// Print the timeline after filtering to check that only the "and events" are
		// left. Careful : the code used when building the ElementTypeFilter should be the 
		// same as the one used to build the AndCriterion
		final ElementTypeFilter filter = // your code here
		// Apply the ElementTypeFilter to the global Timeline
		// your code here
		ProjectUtils.printTimeline(globalTimeline);

		/**
		* Finally you can generate VTS ephemeris for visualization
		* Thus you can check visually that the events detected by your detectors are
		* persistent. For that we use VTSTools methods, you don't have to complete the
		* code.
		*/
		final String pathOEM = ConstantsBE.PATH_VTS_DIRECTORY + File.separator
				+ "BE_Supaero_Satellite_Trajectory_OEM.txt";
		final String pathAEMNadir = ConstantsBE.PATH_VTS_DIRECTORY + File.separator
				+ "BE_Supaero_Nadir_Pointing_AEM.txt";
		final String pathAEMCinematicPlan = ConstantsBE.PATH_VTS_DIRECTORY + File.separator
				+ "BE_Supaero_Cinematic_Plan_AEM.txt";
		final String pathMEM = ConstantsBE.PATH_VTS_DIRECTORY + File.separator
				+ "BE_Supaero_Cinematic_Plan_Events_MEM.txt";
		VTSTools.generateOEMFile(pathOEM, initialDate, finalDate, propagator);
		VTSTools.generateAEMFile(pathAEMNadir, initialDate, finalDate, propagator);
		VTSTools.generateAEMFile(pathAEMCinematicPlan, initialDate, finalDate, propagator);
		VTSTools.generateTimelineMEMFile(pathMEM, globalTimeline);
	}

	/**
	 * Main method
	 * 
	 * @param args
	 * @throws PatriusException If a {@link PatriusException} occurs when calling a
	 *                          tutorial method
	 */
	public static void main(String[] args) throws PatriusException {
		// Loading Patrius Dataset resources
		loadRessources();

		// Here you can execute any static method of the PatriusTuto class
		// Simply comment/uncomment the method you want to execute.
		tuto1TimeScales();
		// tuto2Frames();
		// tuto3KeplerianPropagation();
		// tuto4EphemerisProduction();
		// tuto5EventDetection();
	}

}
