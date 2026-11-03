package de.minentimer.gui;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

import java.util.function.IntConsumer;
import java.util.function.IntFunction;

/** Schieberegler für ganze Zahlen. */
public class IntSlider extends AbstractSliderButton {
	private final int min;
	private final int max;
	private final int step;
	private final IntFunction<String> label;
	private final IntConsumer onChange;

	public IntSlider(int x, int y, int width, int min, int max, int step, int value, IntFunction<String> label, IntConsumer onChange) {
		super(x, y, width, 20, Component.empty(), (double) (Math.clamp(value, min, max) - min) / (max - min));
		this.min = min;
		this.max = max;
		this.step = step;
		this.label = label;
		this.onChange = onChange;
		updateMessage();
	}

	private int intValue() {
		int raw = (int) Math.round(min + value * (max - min));
		return Math.clamp(Math.round((float) raw / step) * step, min, max);
	}

	@Override
	protected void updateMessage() {
		setMessage(Component.literal(label.apply(intValue())));
	}

	@Override
	protected void applyValue() {
		onChange.accept(intValue());
	}
}
