// class that can store certain parameters that are morphed over time on the client side. Can help when working with midi and other similar stuff

ThyParamManager {
	classvar instanceDict;
	var <>defaultLag, <name, <params, changeData, <>evalsPerSec=32, runTask;

	*new {|name, defaultLag = 0.5|
		var obj = super.new.init(name, defaultLag);
		instanceDict.add(name -> obj);
		^obj;
	}

	*initClass {
		instanceDict = IdentityDictionary.new(128);
	}

	*all {
		^instanceDict.copy;
	}

	*at {|key|
		^instanceDict.at(key);
	}

	init {|newName, newDefaultLag|
		name = newName;
		defaultLag = newDefaultLag;
		params = IdentityDictionary(128, know:true); //params associated with their values
		changeData = IdentityDictionary(128, know:true); //data for params that are being changed

	}

	at {|key|
		^params.at(key);
	}

	put {|key, value|
		if params.includesKey(key) {
			this.paramChangeTo(key, value, defaultLag);
		} {
			params.put(key, value);
		}

	}


	prAlterParam {|key|
		 = this.tempo * 2.pow(accel);
	}

	value {|newTempo|
		clock.tempo = newTempo.max(tempoRange[0]).min(tempoRange[1]);
		if (abs(1-(clock.tempo/tempoLimit.max(0.0001))) < 0.001 ) {accel = 0};
	}

	paramChangeTo {|param, targetValue, lagTime=0.5|
		var startValue = params[param];
		var accel = (targetValue/startValue).log2/(lagTime * evalsPerSec);
		//hidden: accelCoef.log2-> accel coef is always 2!
		limit = target
		^this.accel;

	}


}