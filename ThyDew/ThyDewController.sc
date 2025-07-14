//Class that facilitates controlling Dew droplet chains

/*
TODO: separate in three classes:
ThyDewController: Abstract Class
ThyDewMelodicController: melodic Droplet control
ThyDewRhythmicController: rhythmic droplet control :3
the difference between them is mostly in the PDef section
*/

ThyDewController {
	classvar <dewList, <maxTempo, <baseMelody, <baseArpeggio, <accelBaseCoef;
	var <>index, <>baseFreq, <>accel, <name, <clock, <stream, argsDict, <instrument, <tempoRange, <tempoLimit, semaphore, reEvalPDef, accelerator;

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

	*clear { //clears and stops all DewController objects from the class interface. This *doesn't* mean the objects are deleted!
		dewList.do(_.stop);
		dewList.clear;
	}

	*getAccelFunc {|evalsPerSec = 25, obj|
		^{
			var loopNumber = 0;
			loop{
				//tempo changes by 2 when accel is 1, 0.5 when accel is -1
				obj.prAccelTempo(evalsPerSec);
				evalsPerSec.reciprocal.wait;
				if (loopNumber >= 10) {obj.evalPDef; loopNumber=0} {loopNumber = loopNumber + 1}
			}
		}

	}

	initDew {|newName, newIndex, newFreq, newInstrument, startTempo|
		name = newName.asSymbol;
		index = newIndex;
		baseFreq = newFreq;
		accel = 0.0;
		instrument = newInstrument;
		semaphore = Semaphore(1);
		reEvalPDef = 0; //we don't want to reeval the pdef to many times :3
		tempoRange = [0.01, maxTempo];
		tempoLimit = maxTempo;
		clock = TempoClock(startTempo);
		accelerator = Task(ThyDewController.getAccelFunc(25, this));
		this.evalPDef;
	}

	//interfacing with Args:
	changeVal {|key, val|
		{
			semaphore.wait;
			argsDict[key] = val;
			semaphore.signal;
		}.fork;
		this.evalPDef;
	}

	mappedArgs {
		//This method should return a list of Dew Args mapped to (0, 1)
		^this.subclassResponsibility(thisMethod);
	}

	evalPDef {
		//evaluate the running Pdef
		^this.subclassResponsibility(thisMethod);
	}

	prAccelTempo {|evalsPerSec|
		this.tempo = this.tempo * accelBaseCoef.pow(accel/evalsPerSec);
	}

	tempo {
		^clock.tempo;
	}

	tempo_ {|newTempo|
		clock.tempo = newTempo.max(tempoRange[0]).min(tempoRange[1]);
		if (abs(1-(clock.tempo/tempoLimit.max(0.0001))) < 0.001 ) {accel = 0};
	}

	accelTo {|target, time, limitAtTarget=true|
		//target tempo, time in seconds. if limit at target is true, tempo will stop accel at target
		//changes current accel value according to the input
		this.accel = (target/this.tempo).log2/(time * accelBaseCoef.log2);
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
		stream = Pdef[\name].play(clock, quant:1);
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
		^stream.pause;
	}

	resume {
		accelerator.resume;
		^stream.resume;
	}

}


ThyDewMelodic : ThyDewController {
	var melody, melodyPattern;

	*new {|index=0, baseFreq=220.0, instrument=\default, startTempo=1, melody, startArgs|
		var name = \dew ++ \_ ++ instrument ++ \_ ++ index;
		var obj = super.new(name);
		melody = if (melody.isKindOf(SequenceableCollection)) {melody} {baseMelody};
		startArgs = if (startArgs.isKindOf(IdentityDictionary)) {startArgs} {IdentityDictionary.new};
		obj.initDew(name, index, baseFreq, instrument, startTempo, melody, startArgs);
		^obj;
	}

	initDew {|newName, newIndex, newFreq, newInstrument, startTempo, newMelody, startArgs|
		melody = newMelody;
		melodyPattern = Plet(\melody, Pseq(melody, inf), 1);
		argsDict = IdentityDictionary.newFrom([
			\ringTime, startArgs[\ringTime] ? 1.0, //ringTime
			\amp, startArgs[\amp] ? 1.0, //amp
			\harmonicity, startArgs[\harmonicity] ? 1.0, //harmonicity
			\freqVar, startArgs[\freqVar] ? 0.0  //freqVar
		]);
		argsDict.know = true;
		^super.initDew(newName, newIndex, newFreq, newInstrument, startTempo);
	}

	evalPDef {
		if (reEvalPDef == 0) {{
			var remapFreqVar = 1 + this.freqVar.pow(2);
			reEvalPDef = 1;
			semaphore.wait;
			Pdef(this.name,
				Pbind(
					\instrument, this.instrument,
					\freq, (Pget(\melody, default: 1, repeats: inf)
						* this.baseFreq
						* remapFreqVar.pow(Pgauss(0, 0.03))),
					\harmonicity, this.harmonicity,
					\delta, 1,
					\amp,  this.tempo.reciprocal.sqrt * this.amp,
					\ring, this.ringTime,
					\duration, (Pkey(\ring, inf) * Pkey(\amp, inf) * 1.5)
				)
			);
			semaphore.signal;
			reEvalPDef = 0;
		}.fork};
	}

	type {
		^\mel;
	}

	start {
		stream = Plambda(
			Ppar([melodyPattern, Pdef(name)], inf)
		).play(clock, quant:1);
		accelerator.start;
		^stream
	}

	//ArgsDict interfacing
	ringTime {
		^argsDict[\ringTime];
	}

	ringTime_ {|val|
		this.changeVal(\ringTime, val);
	}

	amp {
		^argsDict[\amp];
	}

	amp_ {|val|
		this.changeVal(\amp, val);
	}

	harmonicity {
		^argsDict[\harmonicity];
	}

	harmonicity_ {|val|
		this.changeVal(\harmonicity, val);
	}


	freqVar {
		^argsDict[\freqVar];
	}

	freqVar_ {|val|
		this.changeVal(\freqVar, val);
	}

	mappedArgs {
		^[
			this.ringTime.linlin(0.0, 6.0, 0.0, 1.0).min(1.0),
			this.amp.linlin(0.0, 2.0, 0.0, 1.0).min(1.0),
			(this.harmonicity*pi).cos.abs,
			this.freqVar.linlin(0.0, 2.0, 0.0, 1,0).min(1.0)
		]
	}


}

