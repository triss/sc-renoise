sc - renoise
============

[SuperCollider][] and [Renoise][] extensions that allow better 
integration between the two tools. 

Status
------

Currently only a single part of the SC extension exists. The class 
does two things currently: 

*	Map a Renoise MIDI Instrument to a SynthDef such that it can be
	played/recorded etc. in the manner of any other Renoise 
	instrument or external synthesiser with a single command.

*	Provides quick access to all of Renoise's [OSC commands][]

Although this code is functional note that this is work in progress, 
is likely to change significantly and should be considered only as 
a proof of concept currently.

It's only been tested on Linux with Jack installed.

Setting Up
----------

*	This class requires the JACK Quark to be present. See
	[Using Quarks][] in the Help if you aren't sure how to install
 	this. 

*	The [Renoise.sc][] file you can grab here needs to be copied in 
	to your SC Extension's folder.

	You can find its location by executing the following in SC: 

		Platform.userExtensionDir; 

*	Restart SC and Start Renoise. 

*	Both Renoise and SC need to be configured to use Jack for audio
	routing.

*	[OSC communication][] needs to be turned on in Renoise.

*	Boot the server and connect MIDI input in SC:

		s.boot;
		MIDIIn.connectAll;

Using the Renoise class in SuperCollider
----------------------------------------

Create a Renoise object:

	r = Renoise();

or if you've tweaked the OSC settings in Renoise:

	r = Renoise("127.0.0.1", 3333);

Creating a Renoise Instrument for a SynthDef
----------------------------------------------

Define a SynthDef in the usual manner:
	
	(
	SynthDef(\sound, { |out = 0, gate = 1, freq = 440, amp = 0.5|
		var sig, env, gate;

		env = EnvGen.kr(Env.adsr(0.01, 0.9, 0.3, 1), gate, doneAction: 2);

		sig = Pulse.ar(freq) * amp * env;

		Out.ar(out, sig);	
	}).add
	)
	
Then create a Renoise instrument and have it wired up to the SynthDef like so:

	r.createSynthDefInstrument(\sound)
	
Switch over to Renoise and trigger the SynthDef by playing the keyboard. This 
can now be played/recorded/fx'd like any other Renoise instrument.

\sound Synths are now being triggered by MIDI notes coming from Renoise, with 
it's parameters mapped as follows:

*	MIDI note 			-> freq
*	MIDI velocity 		-> amp
*	MIDI note on/off 	-> gate

The out argument of the SynthDef was automatically set to appropriate SC 
output which is routed in to Renoise via a Line Input device.

The .createSynthDefInstrument() method is clever/presumptuous enough to 
detect if a SynthDef should be played monophonicly or polyphonicly and also 
deals with mono/stereo SynthDefs sensibly.

A single Synth will be created if the SynthDef passed in doesn't free itself
(has no doneAction) and it's parameters changed each time a note comes in.

If the SynthDef passed in does free itself it will be set up such that 
multiple Synths are created and can be played polyphonicly.

Manipulating Renoise
--------------------

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
those above. You'll need to refer to the "Standard renoise OSC messages" 
section of [Renoise.sc][] to find them for now, they won't be documented 
properly for a few days yet.

Contact
-------

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
[Renoise.sc]: https://github.com/triss/sc-renoise/blob/master/renoiseSCExtension/Renoise.sc
[sc-users]: http://new-supercollider-mailing-lists-forums-use-these.2681727.n2.nabble.com/SuperCollider-Users-New-Use-this-f2676391.html