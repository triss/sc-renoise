# sc - renoise
[SuperCollider][] and [Renoise][] extensions that allow integration of the two tools. 

## Status
Currently only a single part of the SC extension exists. The class 
does two things currently: 

*	Maps SynthDef's to Renoise instruments such that they can be 	sequenced/played/recorded in the manner of any other Renoise 	instrument.
	
*	Provides quick access to all of Renoise's [OSC commands][]

Although this code is functional you should note that this is work in progress and is likely to change significantly.

It's only been tested on Linux with Jack installed.

## Setting Up
### SuperCollider
*	This class requires the JACK Quark to be present. See
	[Using Quarks][] in the Help if you aren't sure how to install
 	this. 

*	Copy [Renoise.sc][] in to your SC Extension's folder. You can find 	its location by executing the following in SC: 

		Platform.userExtensionDir; 

*	SC now needs to be restarted

### Renoise
*	Renoise needs to be set up to use Jack Audio in it's 	[Audio Preferences][] with at least 4 (preferably more) input 	channel's. 

*	[OSC communication][] needs to be turned on in Renoise.

## Use
### Preparing SuperCollider
Boot the server and connect MIDI inputs to SC:

	s.boot;
	MIDIIn.connectAll;

### Using the Renoise class in SuperCollider
Create a Renoise object:

	r = Renoise();

or if you've tweaked the OSC settings in Renoise:

	r = Renoise("127.0.0.1", 3333);

### Creating a Renoise Instrument for a SynthDef
Define a SynthDef in the usual manner:
	
	(
	SynthDef(\sound, { |out = 0, gate = 1, freq = 440, amp = 0.5|
		var sig, env;

		env = EnvGen.kr(Env.adsr(0.01, 0.9, 0.3, 1), gate, doneAction: 2);

		sig = Pulse.ar(freq) * amp * env;

		Out.ar(out, sig);	
	}).add
	)
	
Then create a Renoise instrument and have it wired up to the SynthDef like so:

	r.createSynthDefInstrument(\sound)
	
Switch over to Renoise and trigger the SynthDef by playing the keyboard. This can now be sequenced/played/recorded like any other Renoise instrument.

#### How the SynthDef mapping worked
\sound Synths are now being triggered by MIDI notes coming from Renoise, with it's parameters mapped as follows:

*	MIDI note 			-> freq
*	MIDI velocity 		-> amp
*	MIDI note on/off 	-> gate

The out argument of the SynthDef was automatically set an appropriate SC output which is routed into Renoise via a Line Input device.

The .createSynthDefInstrument() method is clever/presumptuous enough to detect if a SynthDef should be played monophonicly or polyphonicly and also deals with mono/stereo SynthDefs sensibly.

If the SynthDef passed in does free itself it will be set up such thatmultiple Synths are created and can be played polyphonicly.

A single Synth will be created if the SynthDef passed in doesn't free itself (has no doneAction) and it's parameters changed each time a note comes in. i.e. it is played in a monophonic fashion.

### Manipulating Renoise
Now set up Renoise's bpm and start and stop it playing:

	r.bpm = 128;
	r.start;
	
Mute and unmute a track:
	
	r.muteTrack(3);
	r.unmuteTrack(3);

Change a device parameter's value:

	// set track: 1, device: 4, parameter: 3 to 0.5
	r.setDeviceParameter(1, 4, 3, 0.5);
	
Play Renoise Instruments:

	// play instrument 1 on track 1 with note 64, velocity 111
	r.noteOn(1, 1, 64, 111)
	// turn off note
	r.noteOff(1, 1, 64)

All of Renoise's [OSC commands][] have convenience methods similar to 
those above. You'll need to refer to the "Standard renoise OSC messages" section of [Renoise.sc][] to find them for now, they won't be documented properly for a few days yet.

## Contact Details
It'd be much appreciated if you could log questions, bugs and feature 
requests here: <https://github.com/triss/sc-renoise/issues>

I'll also respond to queries on [sc-users][] or to mails sent directly 
to me at <tristan.strange@gmail.com>

Cheers,

Tristan

[SuperCollider]: http://supercollider.sourceforge.net
[Renoise]: http://www.renoise.com
[Using Quarks]: http://doc.sccode.org/Guides/UsingQuarks.html
[OSC commands]:	http://tutorials.renoise.com/wiki/Open_Sound_Control
[OSC communication]: http://tutorials.renoise.com/wiki/Open_Sound_Control
[Audio Preferences]: http://tutorials.renoise.com/wiki/Preferences#Audio
[Renoise.sc]: https://github.com/triss/sc-renoise/blob/master/renoiseSCExtension/Renoise.sc
[sc-users]: http://new-supercollider-mailing-lists-forums-use-these.2681727.n2.nabble.com/SuperCollider-Users-New-Use-this-f2676391.html
