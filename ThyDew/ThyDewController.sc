//Class that facilitates controlling Dew droplet chains

ThyDewController {
	classvar <dewList, <maxTempo, <baseMelody, <baseArpeggio, <accelBaseCoef;
	var <>index, <>baseFreq, <>accel, <name, <clock, <stream, <argsDict, <instrument, <tempoRange, <tempoLimit, semaphore, reEvalPDef, melody, melodyPattern, accelerator;

	*new {|index=0, baseFreq=220.0, instrument=\default, startTempo=1, melody, startArgs|
		var obj = super.new;
		melody = if (melody.isKindOf(SequenceableCollection)) {melody} {baseMelody};
		startArgs = if (startArgs.isKindOf(IdentityDictionary)) {startArgs} {IdentityDictionary.new};
		obj.initDew(index, baseFreq, instrument, startTempo, melody, startArgs);
		this.dewList.add(obj.name -> obj);
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

	*getAccelFunc {|evalsPerSec = 25, obj, argsDict|
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

	initDew {|newIndex, newFreq, newInstrument, startTempo, newMelody, startArgs|
		name = (\dew ++ \_ ++ newInstrument ++ \_ ++ newIndex).asSymbol;
		index = newIndex;
		baseFreq = newFreq;
		accel = 0.0;
		instrument = newInstrument;
		semaphore = Semaphore(1);
		reEvalPDef = 0; //we don't want to reeval the pdef to many times :3
		melody = newMelody;
		tempoRange = [0.01, maxTempo];
		tempoLimit = maxTempo;
		argsDict = IdentityDictionary.newFrom([
			\currentQ, 1.0,
			\currentAmp, 1.0,
			\harmonicity, 1.0,
			\freqVar, 0.0
		]).merge(startArgs, {|default, starting| starting});
		argsDict.know = true;

		melodyPattern = Plet(\melody, Pseq(melody, inf), 1);
		clock = TempoClock(startTempo);
		accelerator = Task(ThyDewController.getAccelFunc(25, this, argsDict));
		this.evalPDef();
		"DewChain with the following ArgsDict initialized: \n%".format(this.argsDict).postln;

	}

	type {
		^\mel;
	}

	mappedArgsDict {
		^[
			argsDict.currentQ.linlin(0.0, 6.0, 0.0, 1.0).min(1.0),
			argsDict.currentAmp.linlin(0.0, 2.0, 0.0, 1.0).min(1.0),
			(argsDict.harmonicity*pi).cos.abs,
			argsDict.freqVar.linlin(0.0, 2.0, 0.0, 1,0).min(1.0)
		]
	}

	prAccelTempo {|evalsPerSec|
		this.tempo = this.tempo * accelBaseCoef.pow(accel/evalsPerSec);
	}

	evalPDef {
		if (reEvalPDef == 0) {{
			var freqVar = 1 + argsDict.freqVar.pow(2);
			reEvalPDef = 1;
			semaphore.wait;
			Pdef(this.name,
				Pbind(
					\instrument, this.instrument,
					\freq, (Pget(\melody, default: 1, repeats: inf)
						* this.baseFreq
						* freqVar.pow(Pgauss(0, 0.03))),
					\harmonicity, argsDict.harmonicity,
					\delta, 1,
					\amp,  this.tempo.reciprocal.sqrt * this.argsDict.currentAmp,
					\ring, this.argsDict.currentQ,
					\duration, (Pkey(\ring, inf) * Pkey(\amp, inf) * 1.5)
				)
			);
			semaphore.signal;
			reEvalPDef = 0;
		}.fork};
	}

	changeVal {|key, val|
		{
			semaphore.wait;
			argsDict[key] = val;
			semaphore.signal;
		}.fork;
		this.evalPDef;
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
		stream = Plambda(
			Ppar([melodyPattern, Pdef(name)], inf)
		).play(clock, quant:1);
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

