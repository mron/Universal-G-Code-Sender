package com.willwinder.universalgcodesender;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

import com.willwinder.universalgcodesender.listeners.ControllerState;
import com.willwinder.universalgcodesender.listeners.ControllerStatus;
import com.willwinder.universalgcodesender.model.PartialPosition;
import com.willwinder.universalgcodesender.model.Position;
import com.willwinder.universalgcodesender.model.UnitUtils.Units;

public class MarlinUtils {
	public static boolean isOkErrorAlarmResponse(String response) {
		return isOkResponse(response); // || isErrorResponse(response) || isAlarmResponse(response);
	}

	public static boolean isOkResponse(String response) {
		return StringUtils.equalsIgnoreCase(response, "ok");
	}

	// Real time
	public static final byte GRBL_PAUSE_COMMAND = '!';
	public static final byte GRBL_RESUME_COMMAND = '~';
	public static final byte GRBL_STATUS_COMMAND = '?';
	
	/**
	 * Check if a string contains a Marlin position string.
	 */
	private static final String STATUS_REGEX = "^<<X\\:";
	private static final Pattern STATUS_PATTERN = Pattern.compile(STATUS_REGEX);
	private static final String GCODE_SET_COORDINATE = "G92";
	public static final String GCODE_ABS_COORDS = "G90";
	public static final String GCODE_REL_COORDS = "G91";
	public static final String GCODE_RETURN_TO_XY_ZERO_LOCATION = "G0 X0 Y0";
	public static final String GCODE_RETURN_TO_Z_ZERO_LOCATION = "G0 Z0";

	static protected Boolean isMarlinStatusString(final String response) {
		return STATUS_PATTERN.matcher(response).find();
	}

	/*
	 * M114 response...
	 *
	 * X:0.00 Y:0.00 Z:0.00 E:0.00 Count X:0 Y:0 Z:0
	 * 
	 * ? response...
	 * <<X:10.00 Y:0.00 Z:0.00 E:0.00 F:1.00 S_XYZ:3>>// The feedrate is mm/s 
	 */

	static protected ControllerStatus getStatusFromStatusString(
			ControllerStatus lastStatus, final String status,
			final Capabilities version, Units reportingUnits) {
		Double xpos = 0.0;
		Double ypos = 0.0;
		Double zpos = 0.0;
		Double wxpos = xpos;
		Double wypos = ypos;
		Double wzpos = zpos;
		Double fr = 0.0; // Feedrate
		Integer s = 0; //Status
		// final Pattern splitterPattern = Pattern.compile("^X\\:([^ ]+) Y\\:([^ ]+) Z\\:([^ ]+) E");
		//final Pattern splitterPattern = Pattern.compile("^<<X:([^ ]+) Y:([^ ]+) Z:([^ ]+) E:[^ ]+ F:([^ ]+) S_XYZ:([^ ])>>" );
		//final Pattern splitterPattern = Pattern.compile("<<X:([^ ]+) Y:([^ ]+) Z:([^ ]+) NX:([^ ]+) NY:([^ ]+) NZ:([^ ]+) E:[^ ]+ F:([^ ]+) S_XYZ:([^ ])>>" );
		final Pattern splitterPattern = Pattern.compile("([XYZ_SFN]+):([^ >]+)");
		Matcher matcher = splitterPattern.matcher(status);
		boolean match_found = false;
		while( matcher.find() ){
			match_found = true;
			String s1 = matcher.group(1);
			String s2 = matcher.group(2);
			if( matcher.group(1).matches("X") ) { xpos = getCoord(matcher, 2); wxpos = xpos; continue; }
			if( matcher.group(1).matches("Y") ) { ypos = getCoord(matcher, 2); wypos = ypos; continue; }
			if( matcher.group(1).matches("Z") ) { zpos = getCoord(matcher, 2); wzpos = zpos; continue; }
			if( matcher.group(1).matches("WX") ) { wxpos = getCoord(matcher, 2); continue; }
			if( matcher.group(1).matches("WY") ) { wypos = getCoord(matcher, 2); continue; }
			if( matcher.group(1).matches("WZ") ) { wzpos = getCoord(matcher, 2); continue; }
			if( matcher.group(1).matches("F") ) { fr = getCoord(matcher, 2); continue; }
			if( matcher.group(1).matches("S_XYZ") ) { s = Integer.parseInt( matcher.group(2)); continue; }
		}
		if( match_found ){
			ControllerState cs = lastStatus.getState();
			/*
			  Here's what Marlin thinks
			  enum M_StateEnum : uint8_t {
				M_INIT = 0, //  0 machine is initializing
				M_RESET,    //  1 machine is ready for use
				M_ALARM,    //  2 machine is in alarm state (soft shut down)
				M_IDLE,     //  3 program stop or no more blocks (M0, M1, M60)
				M_END,      //  4 program end via M2, M30
				M_RUNNING,  //  5 motion is running
				M_HOLD,     //  6 motion is holding
				M_PROBE,    //  7 probe cycle active
				M_CYCLING,  //  8 machine is running (cycling)
				M_HOMING,   //  9 machine is homing
				M_JOGGING,  // 10 machine is jogging
				M_ERROR     // 11 machine is in hard alarm state (shut down)
			};
			*/
			switch(s){
				case 0 : case 1 : break; // Skip these
				case 2 : cs = ControllerState.ALARM ; break ;
				case 3 : cs = ControllerState.IDLE ; break ;
				case 4 : break ; // Skip
				case 5 : cs = ControllerState.RUN ; break ; // Doesn't mean the same in Marlin
				case 6 : cs = ControllerState.HOLD ; break ;
				case 7 : break ;
				case 8 : break ; // Could be useful
				case 9 : cs = ControllerState.HOME ; break ;
				case 10 : cs = ControllerState.JOG ; break ;
				case 11 : cs = ControllerState.ALARM ; break ;
				default: break;
			}

			Position pos = new Position(xpos, ypos, zpos, Units.MM);
			Position npos = new Position(wxpos, wypos, wzpos, Units.MM);
			return new ControllerStatus( cs, pos, npos, fr);
		}
		return lastStatus;
	}

	private static Double getCoord(Matcher matcher, int idx) {
		String str = matcher.group(idx);
		Double pos = Double.parseDouble(str);
		return pos;
	}

	public static boolean isMarlinEchoMessage(String response) {
		return StringUtils.startsWith(response, "echo:");
	}

	public static boolean isBusyResponse(String response) {
		return StringUtils.startsWith(response, "echo:busy:");
	}

	public static boolean isPausedResponse(String response) {
		//return StringUtils.contains(response, "echo:busy: paused for user");
		return StringUtils.contains(response, "S_XYZ:6");
	}

	/**
	 * Generate a command to set the work coordinate position for multiple axis.
	 *
	 * @param offsets
	 *            the new work position to use (one ore more axis)
	 * @return a string with the gcode command
	 */
	protected static String getSetCoordCommand(PartialPosition offsets) {
		String coordsString = offsets.getFormattedGCode();
		return MarlinUtils.GCODE_SET_COORDINATE + " " + coordsString;
	}

    /**
     * Determines version of GRBL position capability.
     */
    protected static Capabilities getMarlinStatusCapabilities() {
        Capabilities ret = new Capabilities();
        ret.addCapability(CapabilitiesConstants.JOGGING);
        // ret.addCapability(CapabilitiesConstants.CHECK_MODE);
        // ret.addCapability(CapabilitiesConstants.FIRMWARE_SETTINGS);
        ret.addCapability(CapabilitiesConstants.RETURN_TO_ZERO);
        // ret.addCapability(CapabilitiesConstants.X_AXIS);
        // ret.addCapability(CapabilitiesConstants.Y_AXIS);
        // ret.addCapability(CapabilitiesConstants.Z_AXIS);

		ret.addCapability(CapabilitiesConstants.HOMING);
		// ret.addCapability(CapabilitiesConstants.HARD_LIMITS);

		ret.addCapability(GrblCapabilitiesConstants.REAL_TIME);

		// ret.addCapability(GrblCapabilitiesConstants.REAL_TIME);
		// ret.addCapability(CapabilitiesConstants.SOFT_LIMITS);
		// ret.addCapability(CapabilitiesConstants.SETUP_WIZARD);


		// ret.addCapability(GrblCapabilitiesConstants.V1_FORMAT);
		// ret.addCapability(GrblCapabilitiesConstants.HARDWARE_JOGGING);
		// ret.addCapability(CapabilitiesConstants.OVERRIDES);
		// ret.addCapability(CapabilitiesConstants.CONTINUOUS_JOGGING);
        return ret;
    }
}
