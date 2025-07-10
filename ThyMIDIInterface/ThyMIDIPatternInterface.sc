//Classes to speed up working with patterns through midi

ThyMIDIPbind {
	classvar <>patList;
	var <>name, <>chan, <argsDict, <bindArgsDict, <localArgsDict, <>clock, lastMidiVals;

	*new {|name=\defaultPatternName, chan=0, clock|
		var obj = super.new;
		obj.name = name.asSymbol;
		obj.chan = chan;
		obj.clock = clock ? TempoClock.new(1);
		obj.setArgsDict(IdentityDictionary.new(16, know: true));
		obj.setBindArgsDict(IdentityDictionary.new(16));
		obj.setLocalArgsDict(IdentityDictionary.new(16));
		this.patList.add(obj.name -> obj);
		^obj;
	}

	*initClass {
		patList = IdentityDictionary.new;
	}

	*all {
		^patList;
	}

	*clear {
		patList.clear;
	}

	setArgsDict {|dict|
		if (dict.isKindOf(Dictionary)) {argsDict = dict; argsDict.know = true} {Error("Object % is not a kind of Dictionary".format(dict)).throw}
	}

	setBindArgsDict {|dict|
		if (dict.isKindOf(Dictionary)) {bindArgsDict = dict} {Error("Object % is not a kind of Dictionary".format(dict)).throw}
	}

	setLocalArgsDict {|dict|
		if (dict.isKindOf(Dictionary)) {localArgsDict = dict} {Error("Object % is not a kind of Dictionary".format(dict)).throw}
	}

	reeval {
		var bindArgs = bindArgsDict.collect({|item| item.value(argsDict, *lastMidiVals)}).asKeyValuePairs;
		Pdef ( name, Pbind(*bindArgs) );
	}

	prEvalLocalArgs {|vel, note_num, chan, src|
		localArgsDict.keysValuesDo({|key, func| argsDict.put(key, func.value(vel, note_num, chan, src))});
	}

	setNoteOn {
		MIDIdef.noteOn(name ++ \On, {|vel, note_num, chan, src|
			var bindArgs;
			this.prEvalLocalArgs(vel, note_num, chan, src);
			bindArgs = bindArgsDict.collect({|item| item.value(argsDict, vel, note_num, chan, src)}).asKeyValuePairs;
			bindArgs.postln;
			Pdef ( name, Pbind(*bindArgs) );
			lastMidiVals = [vel, note_num, chan, src];
		}, chan: chan);
	}

	setTempoControl {|ccNum, ccChan, lower=0.5, higher=2|
		ccChan = ccChan ? chan;
		MIDIdef.cc(name ++ \_tempoMod, {|val, num, chan, src|
			~clock.tempo_(val.linexp(0, 127, lower, higher))
		}, ccNum: ccNum, chan: ccChan);
	}

	plugControl {|ccNum=0, argName, valFunc, startVal=1, ccChan, reEvaluateOnChange=false|
		argName = (argName ? (\arg ++ ccNum.asSymbol)).asSymbol;
		valFunc = valFunc ? {|val| val};
		ccChan = ccChan ? chan;
		argsDict.add(argName -> startVal);
		MIDIdef.cc(name ++ \_ ++ argName.asSymbol, {|val, num, chan, src|
			val = valFunc.value(val);
			argsDict.add(argName -> val.copy);
			if (reEvaluateOnChange) {this.reeval};
		}, ccNum: ccNum, chan: ccChan);
	}

	addArg {|argName, val|
		argsDict.put(argName, val)
	}

	addArgsFromPairs {|pairs|
		pairs.pairsDo({|argName, val| this.addArg(argName, val)})
	}

	addLocalArg {|argName, val|
		localArgsDict.put(argName, val)
	}

	addLocalArgsFromPairs {|pairs|
		pairs.pairsDo({|argName, val| this.addLocalArg(argName, val)})
	}

	addBindArg {|bindName, bindVal|
		bindArgsDict.put(bindName, bindVal)
	}

	addBindArgsFromPairs {|pairs|
		pairs.pairsDo({|argName, val| this.addBindArg(argName, val)})
	}

	play {|argClock ...args|
		argClock = argClock ? clock;
		^Pdef(name).play(argClock, *args);
	}

	getPdef {
		^Pdef(name);
	}
}


