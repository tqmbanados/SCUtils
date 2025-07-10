//Classes to speed up working with midi keyboards/controllers
/*


*/

ThyMIDISynth {
	classvar <synthList, <defaultSynthArgsFunc;
	var <>name, <>chan, <>synthGroup, <>synthOutBus, <>defaultAddAction, <argsDict, <statArgsDict, notesArray;

	*new {|name=\defaultSynthName, chan=0, group, outBus, addAction=\addToHead|
		var obj = super.new;
		obj.name = name.asSymbol;
		obj.chan = chan;
		obj.synthGroup = if (group.isKindOf(Group)) {group} {Group.new};
		obj.synthOutBus = if (outBus.isKindOf(Bus) || outBus.isKindOf(Number)) {outBus} {0};
		obj.setArgsDict(IdentityDictionary.new(16));
		obj.setNotesArray(Array.newClear(128));
		obj.setStatArgsDict(IdentityDictionary.new(8));
		obj.defaultAddAction = addAction;
		this.synthList.add(obj.name -> obj);
		^obj;
	}

	*initClass {
		synthList = IdentityDictionary.new;
		defaultSynthArgsFunc = {|vel, note_num, chan, src|
			[\freq, note_num.midicps, \amp, vel.linexp(1, 127, 0.05, 0.5)]
		};
	}

	*all {
		^synthList;
	}

	*clear { //clears all MIDISynth objects from the class interface. This *doesn't* mean the objects are deleted!
		synthList.clear;
	}

	setArgsDict {|dict|
		if (dict.isKindOf(Dictionary)) {argsDict = dict} {Error("Object % is not a kind of Dictionary".format(dict)).throw}
	}

	setStatArgsDict {|dict|
		if (dict.isKindOf(Dictionary)) {statArgsDict = dict} {Error("Object % is not a kind of Dictionary".format(dict)).throw}
	}

	setNotesArray {|array|
		if (array.isKindOf(ArrayedCollection)) {notesArray = array} {Error("Object % is not a kind of ArrayedCollection".format(array)).throw}
	}

	setNoteOn {|synthDef=\default, argsFunc, server=(Server.default)|
		argsFunc = argsFunc ? ThyMIDISynth.defaultSynthArgsFunc;
		MIDIdef.noteOn(name++\On, {|vel, note_num, chan, src|
			var args = argsFunc.value(vel, note_num, chan, src) ++ argsDict.asKeyValuePairs ++ [out: synthOutBus] ++ statArgsDict.asKeyValuePairs;
			var prev = notesArray[note_num];
			notesArray[note_num] = Synth(synthDef, args, synthGroup, defaultAddAction);
			if (prev.isNil.not) {prev.release};
		}, chan: chan);
	}

	setNoteOff {|argsFunc, server=(Server.default)|
		MIDIdef.noteOff(name++\Off, {|vel, note_num, chan, src|
			notesArray[note_num].release;
			notesArray[note_num] = nil;
		}, chan: chan);
	}

	plugDefaultSynth {|synthDef=\default, argsFunc, server=(Server.default)|
		this.setNoteOn(synthDef, argsFunc, server);
		this.setNoteOff(argsFunc, server);
	}

	plugControl {|ccNum=0, modName, valFunc, dynamic=true, startVal=1, ccChan| //if dynamic, then already played notes will change as well
		modName = (modName ? (\mod ++ ccNum.asSymbol)).asSymbol;
		valFunc = valFunc ? {|val| val};
		ccChan = ccChan ? chan;
		argsDict.add(modName -> startVal);
		MIDIdef.cc(name ++ \_ ++ modName.asSymbol, {|val, num, chan, src|
			val = valFunc.value(val);
			if (dynamic) {synthGroup.set(modName, val)};
			argsDict.add(modName -> val.copy);
		}, ccNum: ccNum, chan: ccChan);

	}

}

ThyMIDILatchSynth : ThyMIDISynth {
	var <currentNotes, <>maxNotes;

	*new {|name=\defaultSynthName, chan=0, group, outBus, addAction=\addToHead, maxNotes=8|
		var obj = super.new(name, chan, group, outBus, addAction);
		obj.maxNotes = maxNotes;
		obj.makeNotesArray(maxNotes);
		^obj;
	}

	makeNotesArray {|newMaxNotes|
		newMaxNotes = newMaxNotes ? this.maxNotes;
		currentNotes = Array.new(newMaxNotes);
	}

	setNoteOn {|synthDef=\default, argsFunc, server=(Server.default)|
		argsFunc = argsFunc ? ThyMIDISynth.defaultSynthArgsFunc;
		MIDIdef.noteOn(name++\On, {|vel, note_num, chan, src|
			var args, prev;
			var currentIdx = currentNotes.indexOf(note_num);
			if (currentIdx.isNil.not) {
				notesArray[currentNotes.removeAt(currentIdx)].release;
			} {
				args = argsFunc.value(vel, note_num, chan, src) ++ argsDict.asKeyValuePairs ++ [out: synthOutBus];
				notesArray[note_num] = Synth(synthDef, args, synthGroup, defaultAddAction);
				currentNotes.add(note_num);
			};
			if (currentNotes.size >= maxNotes) {
				var nodeIdx = currentNotes.removeAt(0);
				var node = notesArray[nodeIdx];
				node.release;
			};
		}, chan: chan);
	}

	plugDefaultSynth {|synthDef=\default, argsFunc, server=(Server.default)|
		this.setNoteOn(synthDef, argsFunc, server);
	}
}
