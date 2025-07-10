/*
GUI that gives out some info on Dew Controllers.
Is not interactive, as the Dew Controllers are meant to be used with MIDI.
But shows important information:
1.- index
2.- instrument
3.- type + other base Data (baseFreq for melodic controllers)
4.- tempo/accel
5.- if it has: target + time -> may require some rework in the ControllerClass
6.- argsDict: multislider!
7.- STOP button. Button is gray, but gets redder as tempo increases to signify increased CPU usage
8.- GUI also has a selection property: if the Dew Controller is "selected" for change, but this is communicated through MIDI, not the Dew Controller. -> MIDI has to communicate with both the GUI and the Dew Controller. Selected GUI should turn a shade of green or something.
*/

ThyDewGUI {
	classvar fontMain, fontBold, fontLarge;
	var <view, <controller, <>polimorphic, <>showTarget, updateStream; //base vars
	var indexView, instrumentView, typeView, baseFreqView; //basic info view
	var tempoView, speedView, targetTempoView, estTimeView; //tempo view
	var argsDictSliders; //Just a Multislider!
	var stopButton;

	*new {|parent, bounds, polimorphic=false|
		var obj = super.new;
		bounds = if(bounds.notNil) {bounds} {900@100};
		^obj.initView(parent, bounds, polimorphic);
	}

	*initClass {
		fontMain = Font("Spectral", 12, usePointSize:true);
		fontBold = Font("Spectral", 12, true, usePointSize:true);
		fontLarge = Font("Spectral", 24, usePointSize:true);
	}

	initView {|parent, bounds, polimorphicStatus|
		var baseInfoGrid, tempoValV, tempoInfoH, tempoInfoV;
		view = View(parent, bounds).font_(fontMain);
		polimorphic = polimorphicStatus;
		showTarget = true;
		updateStream = Task({loop{this.updateView; 10.reciprocal.wait}});
		//BuildView
		indexView = StaticText(view).string_("index").font_(fontBold);
		instrumentView = StaticText(view).string_("instr");
		typeView = StaticText(view).string_("type");
		baseFreqView = StaticText(view).string_("baseFreq");
		baseInfoGrid = GridLayout.rows(
			[[indexView,      a:\left],   25, nil, 25, [typeView, a:\left]],
			[[instrumentView, a:\left], 25, nil, 25, [baseFreqView, a:\left]]
		);

		tempoView = StaticText(view).string_("2000").font_(fontLarge);
		speedView = StaticText(view).string_("speed");
		targetTempoView = StaticText(view).string_("target");
		estTimeView = StaticText(view).string_("est.");
		tempoInfoV = VLayout(speedView, targetTempoView, estTimeView);
		tempoInfoH = HLayout([tempoView, a:\center], tempoInfoV);

		argsDictSliders = MultiSliderView(view).elasticMode_(1).isFilled_(true).editable_(false);
		argsDictSliders.indexIsHorizontal_(false).size_(4);

		stopButton = Button(view).states_([["ON"], ["OFF"]]).action_({controller.stop});

		view.layout = HLayout(
			[baseInfoGrid, a:\left, s:1],
			nil,
			[tempoInfoH, a:\center, s:1.5],
			nil,
			[argsDictSliders, s:3],
			nil,
			stopButton,
			nil,
		);
	}

	pairController {|newController|
		if(newController.isKindOf(ThyDewController)) {
			(controller = newController);
			this.updateView;
			updateStream.start;
		} {
			"object % is not a valid Dew Controller".format(newController).warn;
		}
	}

	updateView {
		{
			var green, interp, red, warnInterp, warningRed;
			if (controller.isNil) {
				view.background = Color.gray(0.04);
				^this;
			};
			green = Color.new255(200, 255, 170, 215);
			interp = controller.tempo.linlin(0.01, 1200, 1.0, 0.0).min(1.0).max(0.0);
			red = Color.new255(245, 150, 130, 215).blend(green, interp);
			warnInterp = controller.tempo.max(0.01).explin(1000, 2000, 1.0, 0.0).min(1.0).max(0.0);
			warningRed = Color.new255(255, 70, 100, 215).blend(red, warnInterp);
			view.background = warningRed;
			indexView.string = controller.index;
			instrumentView.string = controller.instrument;
			typeView.string = controller.type;
			baseFreqView.string = controller.baseFreq.asFloat.asStringPrec(4);

			tempoView.string = controller.tempo.asStringPrec(4);
			if (showTarget) {
				var speed = 1.62.pow(controller.argsDict.accel);
				var estTime = 0;
				speedView.string = speed.asStringPrec(4);
				targetTempoView.string = controller.tempoLimit.asFloat.asStringPrec(4);
				if (speed != 1) {
					estTime = (log(controller.tempoLimit / controller.tempo) / log(speed))
				};
				estTimeView.string = estTime.asFloat.asStringPrec(4);
			} {
				speedView.string = 1.62.pow(controller.argsDict.accel);
				targetTempoView = "";
				estTimeView.string = "";
			};

			argsDictSliders.value = this.mapArgsDict(controller.argsDict);
		}.defer
	}

	mapArgsDict {|argsDict|
		^[
			argsDict.currentQ.linlin(0.0, 6, 0.0, 1.0).min(1.0),
			argsDict.currentAmp.linlin(0.0, 2.0, 0.0, 1.0).min(1.0),
			(argsDict.harmonicity*pi).cos.abs,
			argsDict.freqVar.linlin(0.0, 2.0, 0.0, 1,0).min(1.0)
		]
	}

	stopUpdateStream {
		//if we wanna manually update
		updateStream.stop;
	}

	//methods to better use polymorphism
	parent {
		^view.parent;
	}

	parents {
		^view.parents;
	}

	bounds {
		^view.bounds;
	}

	bounds_ {|val|
		^view.bounds_(val);
	}

	remove {
		controller=nil;
		updateStream.stop;
		^view.remove;
	}

	removeAll {
		controller=nil;
		updateStream.stop;
		^view.remove;
	}

	asView {
		if (polimorphic) {^this} {"DewGUI View being used instead of the DewGUI".warn; ^view}
	}

	visible {
		^view.visible;
	}

	visible_ {|val|
		^view.visible_(val);
	}

	front {
		^view.front;
	}

	doesNotUnderstand { arg selector ... args;
		var returns;
		if (polimorphic) {

			returns = selector.applyTo(view, *args);
			if (returns.notNil) {
				^returns;
			};
			^this.superPerformList(\doesNotUnderstand, selector, args);
		};
		^this.superPerformList(\doesNotUnderstand, selector, args);
	}



}