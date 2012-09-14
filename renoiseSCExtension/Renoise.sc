Renoise {
	var <ip, <port, <jackName;
	var <netAddr;

	var usedMidiChannels, usedMidiPorts;
	
	var <>midiOnFuncs, <>midiOffFuncs;
	var <>monophonicSynths, <>polyphonicSynths;

	*new { arg ip = "127.0.0.1", port = 8000, jackName = "renoise";
		^super.newCopyArgs(ip, port, jackName).init;
	}

	init {
		netAddr = NetAddr(ip, port);
		
		usedMidiChannels = 0;

		midiOnFuncs = IdentityDictionary();
		midiOffFuncs = IdentityDictionary();

		monophonicSynths = IdentityDictionary();
		polyphonicSynths = IdentityDictionary();
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

	// make jack connection between source and destination
	//
	// source - the name of a jack output port
	// destonation - the name of a jack input port
	//
	// TODO: understand JACK Quark's method for doing this
	//       and perhaps use it
	jackConnect { arg source, destination;
		("jack_connect" + source + destination).unixCmd;
	}

	// create midi instrument in Renoise song
	//
	// name - the name of instrument in Renoise
	// midiOutChannel - midi channel that SC and Renoise instrument
	//                  communication will take place on.
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

	// add track with a line in device to Renoise song
	//
	// input - the input channel number the Line In device should be routing in.
	addLineInTrack { arg input, name;
		this.evaluate(
			// find new number for track
			"local n = renoise.song().sequencer_track_count+1;" ++
			// create a track
			"local t = renoise.song():insert_track_at(n);" ++
			"t.name = \"" ++ name ++ "\";" ++
			// add a line in device to it
			"local d = t:insert_device_at(\"Audio/Effects/Native/#Line Input\", 2);" ++
			// route appropriate channel in to it.
			"d.active_preset_data = string.gsub(d.active_preset_data, " ++
			"\"<InputChannel>0</InputChannel>\"," ++
			"\"<InputChannel>" ++ input ++ "</InputChannel>\");" ++
			"renoise.song().selected_track_index = n;"
		);
	}

	// return's a dictionary containing information about the next free set of
	// renoise inputs.
	getNextFreeRenoiseInput {
		var freeInputs, nextFreeInput;

		nextFreeInput = IdentityDictionary();

		// find a free pair of renoise inputs
		freeInputs = this.getFreeRenoiseInputs();

		freeInputs.size.postln;
		if(freeInputs.size < 2) { 
			"Renoise has no more free inputs".throw;
		};

		nextFreeInput.put(\left, freeInputs[0]);
		nextFreeInput.put(\right, freeInputs[1]);

		// findpairs index as referenced by Line Input in Renoise
		nextFreeInput.put(\number,  
			freeInputs[0]
			.asString
			.findRegexp("[0-9]+")[0][1]
			.asInt - 1
		);

		^nextFreeInput;
	}

	// return's a dictionary containing information about the next free set of
	// SuperCollider outputs.
	getNextFreeSCOutput {
		var freeOutputs, nextFreeOutput;

		nextFreeOutput = IdentityDictionary();

		// get pair of SC outputs
		freeOutputs = this.getFreeSCOutputs();

		// 2 currently as we aren't dealing with mono/stereo as
		// nicely as we could be yet
		if(freeOutputs.size < 2) { 
			"SuperCollider has no more free outputs".throw;
		};

		nextFreeOutput.put(\left, freeOutputs[0]);
		nextFreeOutput.put(\right, freeOutputs[1]);

		// find output number that correspond's to above SC outputs.
		nextFreeOutput.put(\number, 
			freeOutputs[0]
			.asString
			.findRegexp("[0-9]+")[0][1]
			.asInt - 1
		);
		
		^nextFreeOutput;
	}

	makeJackConnectionForSynthDesc { arg synthDesc, scOutput, renoiseInput;
		// if synth def is mono
		if(synthDesc.outputs[0].numberOfChannels == 1) {
			// route 1 SC output to 2 renoise inputs
			// TODO: Understand how to set L+R -> L on #Line Input
			//       device and only use one of Renoise's free inputs.
			this.jackConnect(scOutput[\left], renoiseInput[\left]);
			this.jackConnect(scOutput[\left], renoiseInput[\right]);
		} {
			// else route 2 SC outputs to 2 renoise inputs
			this.jackConnect(scOutput[\left], renoiseInput[\left]);
			this.jackConnect(scOutput[\right], renoiseInput[\right]);
		};
	}

	// wrap synth def in renoise instrument
	// - set up new midi instrument in renoise
	// - route audio in from SC to new track
	// - set up appropriate midi routing in SC
	createSynthDefInstrument { arg synthDefName = "default";
		var renoiseInput, scOutput, synthDesc;

		synthDesc = SynthDescLib.global[synthDefName];

		if(synthDesc.notNil) {
			renoiseInput = this.getNextFreeRenoiseInput();
			scOutput = this.getNextFreeSCOutput();

			// create and route midi instrument in renoise
			usedMidiChannels = usedMidiChannels + 1;
			this.createMIDIInstrument(synthDefName, usedMidiChannels);

			// route audio in to renoise
			this.addLineInTrack(renoiseInput[\number], synthDefName);
			this.makeJackConnectionForSynthDesc(synthDesc, scOutput, renoiseInput);
			
			// create required MIDI responders and synths required to handle 
			// different types of SynthDef.
			if(synthDesc.hasGate) {
				if(synthDesc.canFreeSynth) {
					// wrap gated polyphonic synth
					polyphonicSynths.put(synthDefName, { nil } ! 128);
					
					midiOnFuncs.put(synthDefName,
						MIDIFunc.noteOn({ |velocity, note| 
							polyphonicSynths[synthDefName][note] = Synth(synthDefName, [
								\gate, 1,
								\freq, note.midicps, 
								\amp, velocity / 127.0, 
								\out, scOutput[\number]]) 
						}, nil, usedMidiChannels - 1);
					);

					midiOffFuncs.put(synthDefName,
						MIDIFunc.noteOff({ |velocity, note| 
							polyphonicSynths[synthDefName][note].set(\gate, 0);
						}, nil, usedMidiChannels - 1)
					);
				} {
					// wrap gated monophonic synth
					monophonicSynths.put(synthDefName, Synth(synthDefName));

					midiOnFuncs.put(synthDefName, 
						MIDIFunc.noteOn({ |velocity, note| 
							monophonicSynths[synthDefName].set(
								\freq, note.midicps,
								\amp, velocity / 127.0, 
								\out, scOutput[\number],
								\gate, 1);
						}, nil, usedMidiChannels - 1)
					);

					midiOffFuncs.put(synthDefName,
						MIDIFunc.noteOff({ |velocity, note| 
							monophonicSynths[synthDefName].set(\gate, 0);
						}, nil, usedMidiChannels - 1)
					);		
				}
			} {
				// wrap "percussive" polyphonic synth - synth with no gate 
				midiOnFuncs.put(synthDefName, 
					MIDIFunc.noteOn({ |velocity, note| 
						Synth(synthDefName, [
							\freq, note.midicps, 
							\amp, velocity / 127.0, 
							\out, scOutput[\number]]) 
					}, nil, usedMidiChannels - 1);
				);
			}
		} {
			("SynthDef" + synthDefName + "does not exist.").throw;
		}
	}

	// -----------------------------
	// Standard renoise OSC messages
	// -----------------------------

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
	setDeviceParameter { arg track, device, parameter, value;
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