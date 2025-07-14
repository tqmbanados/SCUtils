/*
Simplify converting midinotes to partial numbers :3

*/

ThyPartialScale {
	var <baseFreq=27.5, <rootDegree=12;
	*new {|baseFreq=27.5, rootDegree=12|
		^super.new.initPScale(baseFreq, rootDegree);
	}

	initPScale {|newbaseFreq, newRootDegree|
		this.baseFreq = newbaseFreq;
		this.rootDegree = newRootDegree;
	}

	baseFreq_ {|val|
		if (val.isKindOf(SimpleNumber)) {
			baseFreq = val;
		} {
			"% is not a SimpleNumber".format(val).error;
		}

	}

	rootDegree_ {|val|
		if (val.isKindOf(SimpleNumber)) {
			rootDegree = val;
		} {
			"% is not a SimpleNumber".format(val).error;
		}

	}

	degreeToFreq {|degree, rootFreq, octave=0|
		rootFreq = if(rootFreq.isKindOf(SimpleNumber)) {rootFreq} {baseFreq};
		degree = abs(degree - rootDegree);
		^(degree * rootFreq * 2.pow(octave))



	}

	degreeToRatio {|degree, octave=0|
		//essentially returns the partial, added for Scale polymorphism
		^abs(degree - rootDegree);
	}

	degreeToPartial {|degree, octave=0|
		^abs(degree - rootDegree);
	}


}