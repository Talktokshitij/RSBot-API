package org.powerbot.bot.rt4.client;

import org.powerbot.bot.*;
import org.powerbot.bot.rt4.client.internal.INpc;

public class Npc extends Actor<INpc> {
	private static final Reflector.FieldCache a = new Reflector.FieldCache();

	public Npc(final Reflector engine, final Object parent) {
		super(engine, parent);
	}

	public Npc(final INpc wrapped) {
		super(wrapped);
	}

	public NpcConfig getConfig() {
		if (wrapped != null) {
			return new NpcConfig(wrapped.get().getConfig());
		}

		return new NpcConfig(reflector, reflector.access(this, a));
	}
}
