BeatGraph {
	var <>env, <>numBeats, <>attackIndexes;

	*new { |attacks = #[1.0], times = #[1.0], curve = #[-4, 4], numBeats=4, floorLevel=0.0, floorOffset=0.0|
		var env, obj, processedAttacks, processedTimes, attackIndexes, currentSum=0;
		attacks = attacks.asArray;
		times = times.asArray.wrapExtend(attacks.size);
		curve = curve.asArray.wrapExtend(attacks.size * 2);
		processedAttacks = [attacks, floorLevel!attacks.size].lace ++ [attacks[0]];
		processedTimes = [times.asArray, times.asArray * 2.pow(floorOffset)].lace.normalizeSum * numBeats;
		attackIndexes = attacks.size.collect({|idx|
			var pair = [currentSum, processedAttacks[idx * 2]];
			currentSum = currentSum + processedTimes[[idx*2, (idx*2)+1]].sum;
			pair;
		});
        env = Env.new(processedAttacks, processedTimes, curve, 0, processedAttacks.size -1);
		obj = super.new;
		obj.env = env;
		obj.numBeats = numBeats;
		obj.attackIndexes = attackIndexes;
		^obj;
    }

	*simple2 {|numDivs=8, numLayers=(-1), beatDecay=0.1, numBeats=2, floorOffset = 0.0| //beat graph of simple binary bar
		var arr, divisor;
		beatDecay = beatDecay.max(0.001);
		if(numLayers < 1) {numLayers = numDivs.log2.round} {numDivs=2.pow(numLayers)};
		divisor = all {:1/x, x <- (1..numLayers)}.sum;
		arr = Array.fill(numDivs, {|idx|
			({|oct|
				oct = oct + 1;
				NormTrig.normCos(idx, 0.5.pow(oct)) / oct
			}.sum(numLayers) / divisor)
			.pow(2) * ((idx/numDivs).pow(1/beatDecay).neg + 1); //decay beat as bar progresses
		});

		^BeatGraph.new(arr, [1], numBeats: numBeats, floorOffset:floorOffset);
	}

	*simpleN {|numDivs=3, numLayers=3, beatDecay=0.1, numBeats=3, floorOffset = 0.0|
		var arr, divisor;
		beatDecay = beatDecay.max(0.001);
		if(numLayers < 1) {numLayers = this.getNumLayers(numDivs, numBeats)-1} {numDivs=2.pow(numLayers)};
		divisor = all {:1/x, x <- (1..numLayers)}.sum;
		arr = Array.fill(numDivs, {|idx|
			({|oct|
				oct = oct + 1;
				NormTrig.normCos(idx, 0.5.pow(oct)) / oct
			}.sum(numLayers) / divisor)
			.pow(2) * ((idx/numDivs).pow(1/beatDecay.pow(2)).neg + 1); //decay beat as bar progresses
		})!numBeats;
		arr = arr.flatten;
		^BeatGraph.new(arr, [1], numBeats: numBeats, floorOffset:floorOffset);
	}

	*cascade3 {|numDivs=9, numLayers=(-1), beatDecay=0.1, numBeats=3, floorOffset = 0.0| //beat graph of cascading ternary measure
		var arr, divisor;
		beatDecay = beatDecay.max(0.001);
		if(numLayers < 1) {numLayers = this.getNumLayers(numDivs, 3)} {numDivs=3.pow(numLayers)};
		divisor = all {:1/x, x <- (1..numLayers)}.sum;
		arr = Array.fill(numDivs, {|idx|
			({|rec|
				rec = rec + 1;
				NormTrig.normCos(idx, 3.pow(rec.neg)) / rec
			}.sum(numLayers) / divisor)
			.pow(1.5) * ((idx/numDivs).pow(1/beatDecay).neg + 1); //decay beat as bar progresses
		});

		^BeatGraph.new(arr, [1], numBeats: numBeats, floorOffset: floorOffset);
	}
	//TODO: this doesn't work well at the moment. might be too difficult for me :(
	//fix: add custom duration of each beatGroup (also recursive) | fix second order recursion |
	//add layered attack sizes
	*fromDivArray {|divArray=#[4], beatDecay=0.1, layerDecay=0.6, floorOffset=0.0, numBeats=1|
		var timesArray = this.prProcessArray(divArray, beatDecay, layerDecay);
		^BeatGraph.new(1!timesArray.size, timesArray, numBeats: numBeats, floorOffset: floorOffset)
	}

	*prProcessArray {|divArray=#[4], beatDecay=0.1, layerDecay=0.6, current=#[], depth=1|
		divArray.do({|item, idx|
			if (item.respondsTo(\at)) {
				current = current ++ this.prProcessArray.value(item, beatDecay, layerDecay, current);
			} {
				current = current ++ ((1/item)!item)
			};
		});
		^current.normalizeSum;
	}

	*getNumLayers {|numDivs=9, divSize=3| //use this until I find out a way to get a log3
		var currentNum = 0;
		while {numDivs > 1} {
			numDivs = numDivs/divSize;
			currentNum = currentNum + 1;
		}
		^currentNum;
	}

	plot {|...args|
		^this.env.plot(*args);
	}

	normalizedAt {|idx=0.0|
		var tempIdx = idx * this.env.duration;
		^this.env.at(tempIdx);
	}

	at {|idx=0.0|
		^this.env.at(idx);
	}

	levels {
		^this.env.levels;
	}

	asRoutine {
		^Routine({
			var env = this.env.copy;
			var idx = 0;
			var timeAdvance = 0;
			loop {
				timeAdvance = env.at(idx).yield ? 0;
				idx = timeAdvance + idx;
				idx = idx % this.numBeats;
			}
		})
	}

	asProut {|func=nil|
		func = if (func.isKindOf(Function)) {func} {{
			//Function that should work for two possible use cases, an incoming Pbind Event, or a Number
			|event|
			{event.at(\delta)}.try(event)
		}};
		^Prout({
			var env = this.env.copy;
			var idx = 0;
			var timeAdvance = 0;
			loop {
				timeAdvance = env.at(idx).yield ? 0;
				timeAdvance = func.value(timeAdvance);
				idx = timeAdvance + idx;
				idx = idx % this.numBeats;
			}
		})
	}

	asPseg {
		^Pseg(Pseq(env.levels), Pseq(env.times), Pseq(env.curves), inf)
	}

	// Todo: fix all of this :3
	getBiggestAttackPair {|numAttacks=4|
		var revSorted = attackIndexes.copy.sort({|a, b| a[1] > b[1]});
		^revSorted[(0..numAttacks-1)]
	}

	asDeltaArray {|numDeltas=4, variance=0, tempoFactor=1|
		var attackPairs = this.getBiggestAttackPair((numDeltas+variance).max(2));
		var chosenIndexes = attackPairs.scramble[(0..numDeltas.max(2)-1)].sort({|a, b| a[0] < b[0]});
		^chosenIndexes.collect({|item, idx|
			(chosenIndexes.wrapAt(idx+1)[0] - item[0]) % numBeats
		}) * tempoFactor;
	}


}


NormTrig {
	*normCos {|val=0, partial=1|
		^(cos(val*pi*2*partial)+1)/2;
	}

	*normSin {|val=0, partial=1|
		^(sin(val*pi*2*partial)+1)/2;
	}


}

