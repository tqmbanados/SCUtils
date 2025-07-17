//Class that facilitates controlling Dew droplet chains

/*
TODO (PRIORITY): rework
- direct messaging/Pfunc/Pchain?
- add outbus, group

TODO: separate in three classes:
ThyDewController: Abstract Class
ThyDewMelodicController: melodic Droplet control
ThyDewRhythmicController: rhythmic droplet control :3
*/


ThyDewController {
	classvar <dewList, <maxTempo, <baseMelody, <baseArpeggio, <accelBaseCoef;
	var <>index, <>baseFreq, <>accelMod=0.0, <>deltaFactor=1.0, <>server;
	var <accel, <name, <clock, <stream, <instrument, <busIndices, <group, <tempoRange, <tempoLimit;
	var semaphore, reEvalPdefn, accelerator, defaultMsg, argsDict, argsMsgMap, busStream;

	*new {|name|
		var obj = super.new;
		this.dewList.add(name -> obj);
		^obj;
	}

	*initClass {
		dewList = IdentityDictionary.new(16);
		maxTempo = 2000.0;
		baseMelody = [1, 6/5, 7/4, 9/4, 3/2, 1, 6/5, 7/4, 9/4, 3/2, 27/16];
		baseArpeggio = [1, 6/5, 7/2, 9/4];
		accelBaseCoef = 2.0//1.62;
	}

	*all {
		^dewList;
	}

	*stopAll { //stops all DewController objects from the class interface. This *doesn't* mean the objects are deleted!
		dewList.do(_.stop);
	}

	*removeReference {|name|
		dewList[name] = nil;
	}

	*getAccelFunc {|evalsPerSec = 25, obj|
		^{
			loop{
				//tempo changes by 2 when accel is 1, 0.5 when accel is -1
				obj.prAccelTempo(evalsPerSec);
				evalsPerSec.reciprocal.wait;
			}
		}

	}

	*createDefaultMessage {|instrument, server, group, args|
		var argMaps = IdentityDictionary.newFrom(
			args.clump(2).collect({|pair, idx|
				[pair[0], 2*idx+6]
			}).flatten
		);
		var msg = Synth.basicNew(instrument, server, -1).newMsg(group, args: args);
		^[msg, argMaps]
	}

	initDew {|newName, newIndex, newFreq, newInstrument, newBus, newGroup, startTempo|
		name = newName.asSymbol;
		index = newIndex;
		baseFreq = newFreq;
		accel = 0.0;
		accelMod = 0.0;
		instrument = newInstrument;
		busIndices = newBus;
		group = newGroup;
		semaphore = Semaphore(1);
		reEvalPdefn = 0; //we don't want to reeval the Pdefn to many times :3
		tempoRange = [0.01, maxTempo];
		tempoLimit = maxTempo;
		clock = TempoClock(startTempo);
		server = Server.default;
	}

	defaultMsg {
		^defaultMsg.copy;
	}

	//interfacing with Args:
	changeVal {|key, val|
		^Message.new(this, (key++\_).asSymbol).value(val);
	}

	withSemaphore {|func, args|
		{
			semaphore.wait;
			func.value(args);
			semaphore.signal;
		}.forkIfNeeded
	}

	mappedArgs {
		//This method should return a list of Dew Args mapped to (0, 1)
		^this.subclassResponsibility(thisMethod);
	}

	evalPdefn {
		//evaluate the running Pdefn
		^this.subclassResponsibility(thisMethod);
	}

	prAccelTempo {|evalsPerSec|
		this.tempo = this.tempo * accelBaseCoef.pow((accel+accelMod)/evalsPerSec);
	}

	tempo {
		^clock.tempo;
	}

	tempo_ {|newTempo|
		if (newTempo != this.tempo) {
			clock.tempo = newTempo.max(tempoRange[0]).min(tempoRange[1]);
			if (abs(1-(clock.tempo/tempoLimit.max(0.0001))) < 0.001 ) {accel = 0};
			this.withSemaphore({
				defaultMsg.put(argsMsgMap[\amp], this.amp * this.tempo.reciprocal.sqrt.min(4));
				defaultMsg.put(argsMsgMap[\duration], this.duration);
			})
		}
	}

	amp {
		^this.subclassResponsibility(thisMethod);
	}

	duration {
		^this.subclassResponsibility(thisMethod);
	}

	accelTo {|target, time, limitAtTarget=true|
		//target tempo, time in seconds. if limit at target is true, tempo will stop accel at target
		//changes current accel value according to the input
		accelMod = 0.0;
		accel = (target/this.tempo).log2/(time * accelBaseCoef.log2);
		if (limitAtTarget) {tempoLimit = target};
		^this.accel;

	}

	isPlaying {
		if (stream.notNil) {
			^stream.isPlaying;
		} {
			^false;
		}
	}

	start {
		stream = Pdefn(name).play(clock);
		accelerator.start;
		^stream
	}

	stop {
		stream.stop;
		clock.stop;
		accelerator.stop;
	}

	pause {
		accelerator.pause;
		stream.pause;
	}

	resume {
		accelerator.resume;
		stream.resume;
	}

}

ThyDewMelodic : ThyDewController {
	var <melody, melodyStream;

	*new {|index=0, baseFreq=220.0, instrument=\default, busses, group, startTempo=1, melody, startArgs|
		var name = \dew ++ \_ ++ instrument ++ \_ ++ index;
		var obj = super.new(name);
		melody = if (melody.isKindOf(SequenceableCollection)) {melody} {baseMelody};
		startArgs = if (startArgs.isKindOf(IdentityDictionary)) {startArgs} {IdentityDictionary.new};
		busses = busses ? [0, 1];
		group = if (group.isKindOf(Group)) {group} {Server.default.defaultGroup};

		obj.initDew(name, index, baseFreq, instrument, busses, group, startTempo, melody, startArgs);
		^obj;
	}

	initDew {|newName, newIndex, newFreq, newInstrument, newBusses, newGroup, startTempo, newMelody, startArgs|
		melody = newMelody;
		argsDict = IdentityDictionary.newFrom([
			\ringTime, startArgs[\ringTime] ? 1.0, //ringTime
			\dB, startArgs[\dB] ? 0.0, //dB
			\harmonicity, startArgs[\harmonicity] ? 0.0, //harmonicity
			\freqVar, startArgs[\freqVar] ? 0.0  //freqVar
		]).know_(true);

		super.initDew(newName, newIndex, newFreq, newInstrument, newBusses, newGroup, startTempo);
		this.initStreams;
	}

	initStreams {
		var msgData = ThyDewController.createDefaultMessage(
			instrument,
			Server.default,
			group,
			[
				freq: baseFreq,
				outbus: busIndices.choose,
				partialDensity: this.partialDensity,
				amp: this.dB.dbamp,
				ringTime: this.ringTime,
				duration: this.dB.dbamp * this.ringTime * 1.5,
			]
		);
		defaultMsg = msgData[0];
		argsMsgMap = msgData[1];
		accelerator = Task(ThyDewController.getAccelFunc(25, this));
		melodyStream = Pseq(melody, inf).asStream;
		busStream = Pseq(busIndices, inf).asStream;
		this.evalPdefn;
	}

	evalPdefn {
		if (reEvalPdefn == 0) {{
			var remapFreqVar = 1 + this.freqVar.pow(2);
			reEvalPdefn = 1;
			semaphore.wait;
			Pdefn(this.name,
				Pfunc {//we hardcode freq and outbus positions for speed
					defaultMsg[6] = melodyStream.next * baseFreq * remapFreqVar.pow(0.gauss(0.03)); //freq
					defaultMsg[8] = busStream.next;
					server.sendMsg(*defaultMsg);
					deltaFactor;
			});
			semaphore.signal;
			reEvalPdefn = 0;
		}.fork};
	}

	type {
		^\mel;
	}

	start {
		stream = Pdefn(name).play(clock);
		accelerator.start;
		^stream
	}

	//ArgsDict interfacing
	ringTime {
		^argsDict[\ringTime];
	}

	duration {
		^this.amp.min(1) * this.tempo.reciprocal.sqrt * this.ringTime * 1.5;
	}

	ringTime_ {|val|
		this.withSemaphore({
			argsDict[\ringTime] = val;
			defaultMsg.put(argsMsgMap[\ringTime], val);
			defaultMsg.put(argsMsgMap[\duration], this.duration);
		})
	}

	dB {
		^argsDict[\dB];
	}

	amp {
		^this.dB.dbamp;
	}

	dB_ {|val|
		this.withSemaphore({
			argsDict[\dB] = val;
			defaultMsg.put(argsMsgMap[\amp], this.amp * this.tempo.reciprocal.sqrt.min(4));
			defaultMsg.put(argsMsgMap[\duration], this.duration);
		})
	}

	harmonicity {
		^argsDict[\harmonicity];
	}

	harmonicity_ {|val|
		this.withSemaphore({
			argsDict[\harmonicity] = val;
			defaultMsg.put(argsMsgMap[\partialDensity], this.partialDensity);
		})
	}

	partialDensity {|harmonicity|
		harmonicity = harmonicity ? this.harmonicity;
		^(harmonicity + (200*harmonicity.pow(2) + harmonicity + 1).reciprocal)
	}


	freqVar {
		^argsDict[\freqVar];
	}

	freqVar_ {|val|
		this.withSemaphore({
			argsDict[\freqVar] = val;
		});
		this.evalPdefn;
	}

	mappedArgs {
		^[
			this.ringTime.linlin(0.0, 6.0, 0.0, 1.0).min(1.0),
			this.dB.linlin(-48, 24, 0.0, 1.0).min(1.0),
			this.harmonicity.linlin(0.0, 3.0, 0.0, 1.0).min(1.0),
			(this.freqVar+0.01).explin(0.01, 2.0, 0.0, 1,0).min(1.0)
		]
	}


}
