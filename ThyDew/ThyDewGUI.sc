/*
GUI that gives out some info on Dew Controllers.
Is not interactive, as the Dew Controllers are meant to be used with MIDI.
*/

ThyDewGUI {
	classvar fontMain, fontBold, fontLarge;
	var <view, <controller, <>polimorphic, <>showTarget, <>selected, updateStream; //base vars
	var indexView, instrumentView, typeView, baseFreqView; //basic info view
	var tempoView, speedView, targetTempoView, estTimeView; //tempo view
	var argsDictSliders; //Just a Multislider!
	var colourView, stopButton;

	*new {|parent, bounds, polimorphic=false|
		var obj = super.new;
		bounds = if(bounds.notNil) {bounds} {900@85};
		^obj.initView(parent, bounds, polimorphic);
	}

	*initClass {
		fontMain = Font("Spectral", 12, usePointSize:true);
		fontBold = Font("Spectral", 12, true, usePointSize:true);
		fontLarge = Font("Spectral", 24, usePointSize:true);
	}

	initView {|parent, bounds, polimorphicStatus|
		var baseInfoGrid, tempoValV, tempoInfoH, tempoInfoV;
		polimorphic = polimorphicStatus;
		showTarget = true;
		selected = false;
		updateStream = Task({loop{this.updateView; 10.reciprocal.wait}});

		//BuildView
		{
			view = View(parent, bounds).font_(fontMain);
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
			argsDictSliders.indexIsHorizontal_(false);

			colourView = View(view, 100@100);
			stopButton = Button(colourView).states_([["ON"], ["OFF"]]).action_({this.stopController});
			colourView.layout = VLayout([nil, s:1], [stopButton, s:1], [nil, s:1]);

			view.layout = HLayout(
				[baseInfoGrid, a:\left, s:1],
				nil,
				[tempoInfoH, a:\center, s:1.5],
				nil,
				[argsDictSliders, s:3],
				nil,
				[colourView, s:1],
				nil,
			);
			view.maxHeight_(100);
		}.defer
	}

	pairController {|newController|
		if(newController.isKindOf(ThyDewController)) {
			controller = newController;
			this.updateView;
			updateStream.start;
		} {
			"object % is not a valid Dew Controller".format(newController).warn;
		}
	}


	updateView {
		if (controller.isNil.not) {defer{
			view.name = controller.name;
			if (controller.isPlaying) {
				var green, interp, red, warnInterp, warningRed;
				green = Color.new255(26, 181, 32, 245);
				interp = controller.tempo.linlin(0.01, 1200, 1.0, 0.0).min(1.0).max(0.0);
				red = Color.new255(255, 160, 107, 245).blend(green, interp);
				warnInterp = controller.tempo.max(0.01).explin(1000, 2000, 1.0, 0.0).min(1.0).max(0.0);
				warningRed = Color.new255(255, 30, 130, 245).blend(red, warnInterp);
				colourView.background = warningRed;
			} {
				colourView.background = Color.gray(0.96);
			};

			if (selected) {
				view.background = Color.new255(200, 255, 170, 215)
			} {
				view.background = Color.gray(0.96);
			};

			indexView.string = controller.index;
			instrumentView.string = controller.instrument;
			typeView.string = controller.type;
			baseFreqView.string = controller.baseFreq.asFloat.asStringPrec(4);

			tempoView.string = controller.tempo.asStringPrec(4);
			if (showTarget) {
				var speed = ThyDewController.accelBaseCoef.pow(controller.accel);
				var estTime = 0;
				speedView.string = speed.asStringPrec(4);
				targetTempoView.string = controller.tempoLimit.asFloat.asStringPrec(4);
				if (speed != 1) {
					estTime = (log(controller.tempoLimit / controller.tempo) / log(speed))
				};
				estTimeView.string = estTime.asFloat.asStringPrec(4);
			} {
				speedView.string = ThyDewController.accelBaseCoef.pow(controller.accel);
				targetTempoView = "";
				estTimeView.string = "";
			};

			argsDictSliders.value = controller.mappedArgs;
		}} {defer{
			colourView.background = Color.gray(0.96);
			view.background = Color.gray(0.96);
		}}
	}

	pauseUpdateStream {
		//if we wanna manually update
		updateStream.pause;
	}

	resumeUpdateStream {
		updateStream.resume;
	}

	stopUpdateStream {
		updateStream.stop;
	}

	stopController {
		controller.stop;
		controller = nil;
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

	alwaysOnTop {
		^view.alwaysOnTop;
	}

	alwaysOnTop_ {|val|
		^view.alwaysOnTop_(val);
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