Renoise {
	var <ip, <port, <jackName;
	var netAddr;
	var usedMidiChannels;
	var <>midiFunctions;

	*new { arg ip = "127.0.0.1", port = 8000, jackName = "renoise";
		^super.newCopyArgs(ip, port, jackName).init;
	}

	init {
		netAddr = NetAddr(ip, port);
		usedMidiChannels = 0;
		midiFunctions = ();
	}

	ip_ { arg newIp;
		ip = newIp;
		netAddr = NetAddr(ip, port);
	}

	port_ { arg newPort;
		port = newPort;
		netAddr = NetAddr(ip, newPort);
	}

	// filter jack ports

	// get all free ports
	getFreeJackPorts {
		var freePorts;
		
		freePorts = SCJConnection.getallports.asArray;

		// filter used ports
		SCJConnection.getconnections.keysValuesDo { |k, v|
			freePorts.remove(k);
			v.do { |j| freePorts.remove(j) };
		};
		
		^freePorts;
	}

	// get all free renoise inputs
	getFreeRenoiseInputs {
		^this
		.getFreeJackPorts
		.select({ |p| 
			p.asString.beginsWith(jackName ++ ":input") 
		})
		.sort;	
	}

	// get all free sc outputs
	getFreeSCOutputs {
		^this
		.getFreeJackPorts
		.select({ |p| 
			p.asString.beginsWith("SuperCollider:out") 
		})
		.sort;	
	}

	// make jack connection
	jackConnect { arg source, destination;
		("jack_connect" + source + destination).unixCmd;
	}

	// create midi instrument
	createMIDIInstrument { arg name, midiOutChannel;
		this.evaluate(
			// create an instrument
			"local i = renoise.song():insert_instrument_at(1);" ++
			// set it's name
			"i.name = \"sc: " ++ name ++ "\";" ++
			// route it's midi in to SC
			"local mop = i.midi_output_properties;" ++
			"mop.device_name = \"SuperCollider: in0\";" ++
			"mop.channel = " ++ midiOutChannel
		);
	}

	// add track with line in
	addLineInTrack { arg input;
		this.evaluate(
			// create a track
			"local t = renoise.song():insert_track_at(renoise.song().sequencer_track_count+1);" ++
			// add a line in device to it
			"local d = t:insert_device_at(\"Audio/Effects/Native/#Line Input\", 2);" ++
			// route appropriate channel in to it.
			"d.active_preset_data = string.gsub(d.active_preset_data, " ++
			"\"<InputChannel>0</InputChannel>\"," ++
			"\"<InputChannel>" ++ input ++ "</InputChannel>\");"
		);
	}

	// wrap synth def in renoise instrument
	// - set up new midi instrument in renoise
	// - route audio in from SC to new track
	// - set up appropriate midi routing in SC
	createSynthDefInstrument { arg synthDef = "default";
		var unusedRenoiseInputLeft, unusedRenoiseInputRight, unusedRenoiseInputNo;
		var unusedSCOutputLeft, unusedSCOutputRight, unusedSCOutputNo;

		// create and route midi instrument in renoise
		usedMidiChannels = usedMidiChannels + 1;
		
		this.createMIDIInstrument(synthDef, usedMidiChannels);

		// find pair of renoise inputs
		unusedRenoiseInputLeft = this.getFreeRenoiseInputs[0];
		unusedRenoiseInputRight = this.getFreeRenoiseInputs[1];

		// get pairs number
		unusedRenoiseInputNo = unusedRenoiseInputLeft
		.asString
		.findRegexp("[0-9]+")[0][1]
		.asInt - 1;

		// route audio in to renoise
		this.addLineInTrack(unusedRenoiseInputNo);

		// find unused SC ourputs
		unusedSCOutputLeft = this.getFreeSCOutputs[0];
		unusedSCOutputRight = this.getFreeSCOutputs[1];

		unusedSCOutputNo = unusedSCOutputLeft
		.asString
		.findRegexp("[0-9]+")[0][1]
		.asInt - 1;

		// if synth def is mono
		if(SynthDescLib.global[synthDef].outputs[0].numberOfChannels == 1) {
			// route 1 SC output to 2 renoise inputs
			this.jackConnect(unusedSCOutputLeft, unusedRenoiseInputLeft);
			this.jackConnect(unusedSCOutputLeft, unusedRenoiseInputRight);
		} {
			// else route 2 SC output to 2 renoise inputs
			this.jackConnect(unusedSCOutputLeft, unusedRenoiseInputLeft);
			this.jackConnect(unusedSCOutputRight, unusedRenoiseInputRight);
		};
		
		midiFunctions.put(synthDef, 
			MIDIFunc.noteOn({ |velocity, note| 
				Synth(synthDef, [\freq, note.midicps, \amp, velocity / 127.0, \out, unusedSCOutputNo]) 
			}, nil, usedMidiChannels - 1);
		);
	}

	// standard renoise OSC messages

	// Evaluate a custom Lua expression,
	evaluate { arg luaExpression;
		netAddr.sendMsg("/renoise/evaluate", luaExpression);
	}

	// Stop playback and reset all playing instruments and DSPs
	panic {
		netAddr.sendMsg("/renoise/transport/panic");
	}

	// Start playback or restart playing the current pattern
	start {
		netAddr.sendMsg("/renoise/transport/start");
	}

	// Stop playback
	stop {
		netAddr.sendMsg("/renoise/transport/stop");
	}

	// Continue playback
	continue {
		netAddr.sendMsg("/renoise/transport/continue");
	}

	// Enable or disable looping the current pattern
	loopPattern_ { arg onOff;
		netAddr.sendMsg("/renoise/transport/loop/pattern", onOff);
	}

	// Enable or disable pattern block looping
	loopBlock_ { arg onOff;
		netAddr.sendMsg("/renoise/transport/loop/block", onOff);
	}

	// Move loop block one segment forwards
	moveLoopBlockForwards {
		netAddr.sendMsg("/renoise/transport/loop/block_move_forwards");
	}

	// Move loop block one segment backwards
	moveLoopBlockBackwards {
		netAddr.sendMsg("/renoise/transport/loop/block_move_backwards");
	}
	
	// Disable or set a new sequence loop range
	loopSequence { arg start, end;
		netAddr.sendMsg("/renoise/transport/loop/sequence", start, end);	
	}

	// Set the songs current BPM [32 - 999]
	bpm_ { arg bpm;
		netAddr.sendMsg("/renoise/song/bpm", bpm);
	}

	// Set the songs current Lines Per Beat [1 - 255]
	lpb_ { arg lpb; 
		netAddr.sendMsg("/renoise/song/lpb", lpb);
	}

	// Set the songs global edit mode on or off
	editMode_ { arg onOff;
		netAddr.sendMsg("/renoise/song/edit/mode", onOff);
	}

	// Set the songs current octave [0 - 8]
	octave_ { arg octave;
		netAddr.sendMsg("/renoise/song/edit/octave", octave);
	}

	// Set the songs current edit_step [0 - 8]
	editStep_ { arg editStep;
		netAddr.sendMsg("/renoise/song/edit/step", editStep);
	}

	// Enable or disable the global pattern follow mode
	patternFollow_ { arg onOff;
		netAddr.sendMsg("/renoise/song/edit/pattern_follow", onOff);
	}

	// Enable or disable the global record quantization
	quantization_ { arg onOff;
		netAddr.sendMsg("/renoise/song/record/quantization", onOff);
	}

	// Set the global record quantization step [1 - 32]
	quantizationStep_ { arg quantizationStep;
		netAddr.sendMsg("/renoise/song/record/quantization_step", quantizationStep);
	}

	// Enable or disable the global chord mode
	chordMode_ { arg onOff;
		netAddr.sendMsg("/renoise/song/record/chord_mode", onOff);
	}

	// Set playback pos to the specified sequence pos
	triggerSequence { arg sequence;
		netAddr.sendMsg("/renoise/song/sequence/trigger", sequence);
	}

	// Replace the current schedule playback pos
	scheduleSequence { arg sequence;
		netAddr.sendMsg("/renoise/song/sequence/schedule_set", sequence);
	}

	// Add a scheduled sequence playback pos
	scheduleSequenceAdd { arg sequence;
		netAddr.sendMsg("/renoise/song/sequence/schedule_add", sequence);
	}

	// Mute the given track, sequence slot in the matrix
	muteSlot { arg track, slot;
		netAddr.sendMsg("/renoise/song/sequence/slot_mute", track, slot);
	}

	// Unmute the given track, sequence slot in the matrix
	unmuteSlot { arg track, slot;
		netAddr.sendMsg("/renoise/song/sequence/slot_unmute", track, slot);
	}

	// standard renoise OSC messages that operate on tracks

	// Set track XXX's pre FX volume [0 - db2lin(3)]
	trackPreFxVolume { arg track, volume;
		netAddr.sendMsg("/renoise/song/track/" ++ track ++ "/prefx_volume", volume);
	}

	// Set track XXX's pre FX volume in dB [-200 - 3]
	trackPreFxVolumeDb { arg track, volume;
		netAddr.sendMsg("/renoise/song/track/" ++ track ++ "/prefx_volume_db", volume);
	}

	// Set track XXX's post FX volume [0 - db2lin(3)]
	trackPostFxVolume { arg track, volume;
		netAddr.sendMsg("/renoise/song/track/" ++ track ++ "/postfx_volume", volume);
	}

	// Set track XXX's post FX volume in dB [-200 - 3]
	trackPostFxVolumeDB { arg track, volume;
		netAddr.sendMsg("/renoise/song/track/" ++ track ++ "/postfx_volume_db", volume);
	}

	// Set track XXX's pre FX panning [-50 - 50]
	trackPreFxPanning { arg track, panning;
		netAddr.sendMsg("/renoise/song/track/" ++ track ++ "/prefx_panning", panning);
	}

	// Set track XXX's pre FX panning [-50 - 50]
	trackPostFxPanning { arg track, panning;
		netAddr.sendMsg("/renoise/song/track/" ++ track ++ "/postfx_panning", panning);
	}
	
	// Set track XXX's pre FX width [0, 1]
	trackPreFxWidth { arg track, width;
		netAddr.sendMsg("/renoise/song/track/" ++ track ++ "/prefx_width", width);
	}

	// Set track XXX's delay in ms [-100 - 100]
	trackOutputDelay { arg track, outputDelay;
		netAddr.sendMsg("/renoise/song/track/" ++ track ++ "/output_delay", outputDelay);
	}

	// Mute track XXX
	muteTrack { arg track;
		netAddr.sendMsg("/renoise/song/track/" ++ track ++ "/mute");
	}

	// Unmute track XXX
	unmuteTrack { arg track;
		netAddr.sendMsg("/renoise/song/track/" ++ track ++ "/unmute");
	}

	// Solo track XXX
	soloTrack { arg track;
		netAddr.sendMsg("/renoise/song/track/" ++ track ++ "/solo");
	}	

	// standard renoise messages that operate on devices

	// Set bypass status of an device [true or false]
	bypassDevice { arg track, device, onOff;
		netAddr.sendMsg(
			"/renoise/song/track/" ++ track 
			++ "/device/" ++ device 
			++ "/bypass", onOff
		);
	}


	// Set parameter value of an device [0 - 1]
	// device is the device index, -1 the currently selected device
	setDeviceParameterByIndex { arg track, device, parameter, value;
		netAddr.sendMsg(
			"/renoise/song/track/" ++ track 
			++ "/device/" ++ device 
			++ "/set_parameter_by_index", parameter, value
		);
	}

	// Set parameter value of an device [0 - 1]
	// device is the device index, -1 the currently selected device
	setDeviceParameterByName { arg track, device, parameter, value;
		netAddr.sendMsg(
			"/renoise/song/track/" ++ track 
			++ "/device/" ++ device 
			++ "/set_parameter_by_name", parameter, value
		);
	}

	// standard renoise realtime note messages

	// send note on
	noteOn { arg instrument, track, note, velocity; 
		netAddr.sendMsg("/renoise/trigger/note_on", instrument, track, note, velocity);
	}

	// send note off
	noteOff { arg instrument, track, note; 
		netAddr.sendMsg("/renoise/trigger/note_off", instrument, track, note);
	}	
}