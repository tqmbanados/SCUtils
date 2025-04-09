//Classes to speed up working with patterns through midi

ThyMIDIPattern {
	classvar <>patList;
	var <>name, <>chan;

	*new {|name=\defaultPatternName, chan=0|
		var obj = super.new;
		obj.name = name.asSymbol;
		obj.chan = chan;
		this.patList.add(obj.name -> obj);
		^obj;
	}

	*initClass {
		patList = IdentityDictionary.new;
	}

	*clear { //clears all MIDISynth objects from the class interface. This *doesn't* mean the objects are deleted!
		patList.clear;
	}
}
