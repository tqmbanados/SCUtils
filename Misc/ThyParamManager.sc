// class that can store certain parameters that are morphed over time on the client side. Can help when working with midi and other similar stuff, regarding parameters that change exponentially

ThyParamManager {
	classvar instanceDict;
	var <defaultLag=0.5, <maxCloseness=0.0001, <name, params, changeData, paramData, <evalsPerSec=32, runTask, semaphore;

	*new {|name, defaultLag=0.5, maxCloseness=0.0001|
		var obj = super.new.init(name, defaultLag, maxCloseness);
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

	init {|newName, newDefaultLag, newMaxCloseness|
		name = newName;
		this.defaultLag = newDefaultLag;
		this.maxCloseness = newMaxCloseness;
		semaphore = Semaphore(1);
		params = IdentityDictionary.new(128); //params associated with their values
		changeData = IdentityDictionary.new(128); //data for params that are being changed
		paramData = IdentityDictionary.new(128);
		//data is stored as: [target to change to, accel, sign, alter, changeType]
		runTask = Task(this.runFunc);
		runTask.play;
	}

	runFunc {
		^{
			loop{
				changeData.keysValuesDo({|param, alterData|
					this.prAlterParam(param, alterData);
				});
				evalsPerSec.reciprocal.wait();
			}
		}
	}

	testTime {
		^bench{
			(evalsPerSec*10000).do{
				changeData.keysValuesDo({|param, alterData|
					this.prAlterParam(param, alterData);
				});
			}
		}/10000
	}

	defaultLag_ {|val|
		defaultLag = if(val.isKindOf(SimpleNumber)) {val} {
			"% is not a SimpleNumber".format(val).warn;
			defaultLag;
		};
	}

	maxCloseness_ {|val|
		maxCloseness = if(val.isKindOf(SimpleNumber)) {val} {
			"% is not a SimpleNumber".format(val).warn;
			maxCloseness;
		};
	}

	evalsPerSec_ {|val|
		evalsPerSec = if(val.isKindOf(Integer)) {val} {
			"% is not an Integer".format(val).warn;
			evalsPerSec;
		};
	}

	register {|key, initValue=1.0, modType=\exp, changeFunc|
		//param name, start value, \lin or \exp, func to call on change
		params[key] = initValue;
		paramData[key] = [modType, changeFunc];
	}

	at {|key|
		^params.at(key);
	}

	put {|key, value|
		if (params.includesKey(key)) {
			this.paramChangeTo(key, value, defaultLag);
		} {
			this.register(key, value, \exp, {});
		};
	}

	prAlterParam {|param, alterData|
		var next, current, target, changeType;
		{
			semaphore.wait;
			current = params[param] + alterData[3];
			target = alterData[0] + alterData[3];
			changeType = alterData[4];
			if (this.closeness(target, current, alterData[2], alterData[4]) > maxCloseness.neg ) {
				next = target;
				changeData[param] = nil;
			} {
				if (changeType == \exp) {
					next = if(current.abs < maxCloseness){
						current + maxCloseness/2 * alterData[2];
					} {
						current * 2.pow(alterData[1]);
					}
				} {
					next = current + alterData[1];
				}
			};
			next = next - alterData[3];
			params[param] = next;
			paramData[param][1].value(next);
			semaphore.signal;
		}.forkIfNeeded;
	}

	closeness {|target, current, startDiffSign, closenessType|
		//how close current is to target *without* crossing over
		//negative closeness means its far away... we want closeness of 0 or more
		if (closenessType == \exp) {
			^startDiffSign * (1.0-this.zeroSafeDiv(target, current));
		} {
			^startDiffSign * (current - target);
		}
	}

	zeroSafeDiv {|num, den, aproxValue=0.0001|
		if (den.abs<aproxValue) {
			var alter = (aproxValue / 5) - (den.abs / 5);
			den = den + (alter * if(den.sign != 0, den.sign, 1));
			num = num + (alter * if(num.sign != 0, num.sign, 1));
		}
		^(num/den);
	}

	paramChangeTo {|param, targetValue, lagTime=0.5, changeType|
		var startValue, accel, alterAmount, closenessSign;
		changeType = if([\lin, \exp].includes(changeType), changeType, paramData[param][0]);
		lagTime = lagTime.max(maxCloseness);
		{
			semaphore.wait;
			startValue = params[param];
			if (changeType == \exp) {
				targetValue = if(targetValue==0, maxCloseness / 100 * startValue.sign, targetValue);
				alterAmount = if(startValue.sign != targetValue.sign) {
					(targetValue + (maxCloseness * targetValue.sign)).neg;
				} {0};
				accel = this.zeroSafeDiv(targetValue+alterAmount, startValue+alterAmount).log2/(lagTime * evalsPerSec);
				closenessSign = (targetValue-startValue).sign * startValue.sign;
			} {
				targetValue = targetValue;
				alterAmount = 0;
				accel = (targetValue-startValue) / (lagTime * evalsPerSec);
				closenessSign = (targetValue-startValue).sign;
			};


			changeData[param] = [targetValue, accel, closenessSign, alterAmount, changeType];
			semaphore.signal;
		}.fork;

	}

	getChangeData {|param|
		^changeData[param];
	}

	getParamData {|param|
		^paramData[param];
	}


}