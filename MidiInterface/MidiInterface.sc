MidiInterface {
	classvar <>synthList;
	var <>name, <>chan, <>synthGroup, <>synthOutBus, <>defaultAddAction, argsDict, notesArray, <>independentModulation;

	*new {|name=\defaultSynthName, chan=0, group, outBus, addAction=\addToHead, independentModulation=false|
		var obj = super.new;
		obj.name = name.asSymbol;
		obj.chan = chan;
		obj.synthGroup = if (group.isKindOf(Group)) {group} {Group.new};
		obj.synthOutBus = if (outBus.isKindOf(Bus)) {outBus} {Bus.audio};
		obj.setArgsDict(IdentityDictionary.new);
		obj.setNotesArray(Array.new(128));
		obj.defaultAddAction = addAction;
		obj.independentModulation = independentModulation;
		this.synthList.add(obj.name -> obj);
		^obj;
	}

	*initClass {
		synthList = IdentityDictionary.new;
	}

	*clear { //clears all MidiInterface objects from the class interface. This *doesn't* mean the objects are deleted!
		synthList.clear;
	}

	*defaultSynthArgsFunc {|vel, note_num, chan, src|
		^[\freq, note_num.midicps, \amp, vel.linexp(1, 127, 0.01, 0.5)]
	}

	getArgsArray {
		^argsDict.keys.asArray.collect({|item, idx| [item, {argsDict[item]}]}).flatten
	}

	setArgsDict {|dict|
		if (dict.isKindOf(Dictionary)) {argsDict = dict} {Error("Object % is not a kind of Dictionary".format(dict)).throw}
	}

	setNotesArray {|array|
		if (array.isKindOf(ArrayedCollection)) {notesArray = array} {Error("Object % is not a kind of ArrayedCollection".format(array)).throw}
	}

	setNoteOn {|synthDef=\default, argsFunc, server=(Server.default), latency=0.11| //default latency is what works best for me
		argsFunc = if (argsFunc.isKindOf(Function)) {argsFunc} {MidiInterface.defaultSynthArgsFunc};
		MIDIdef.noteOn(name++\On, {|vel, note_num, chan, src|
			var args = argsFunc.value(vel, note_num, chan, src) ++ argsDict.asKeyValuePairs;
			var prev = notesArray[note_num];
			server.makeBundle(latency, {
				if (notesArray[note_num].canFreeSynth) {
					notesArray[note_num].release;
					notesArray[note_num] = nil;
				};
				notesArray[note_num] = Synth(synthDef, args, synthGroup, defaultAddAction);
			});
			if (prev.canFreeSynth) {prev.release};
		}, chan: chan);
	}

	setNoteOff {|argsFunc, server=(Server.default), latency=0.11|
		MIDIdef.noteOff(name++\Off, {|vel, note_num, chan, src|
			server.makeBundle(latency, {
				notesArray[note_num].release;
				notesArray[note_num] = nil;
			});
		}, chan: chan);
	}

	plugDefaultSynth {|synthDef=\default, argsFunc, server=(Server.default), latency=0.11|
		this.setNoteOn(synthDef, argsFunc, server, latency);
		this.setNoteOff(argsFunc, server, latency);

	}

	plugControl {|ccNum=0, modName, valFunc|
		modName = (modName ? \mod ++ ccNum.asSymbol).asSymbol;
		valFunc = if (valFunc.isKindOf(Function)) {valFunc} {|val| val};
		MIDIdef.cc(name ++ \_ ++ modName.asSymbol, {|val, num, chan, src|
			val = valFunc.value(val);
			if (independentModulation.not) {synthGroup.set(modName, val)};
			argsDict.add(modName -> val);
		}, ccNum: ccNum, chan: chan);

	}

}
/*

MIDIdef.cc(\wavermod1, {|val, num, chan, src|
	~synthGroup.set(\mod1, val);
	~mod1Current = val;
}, ccNum: 2);
*/
