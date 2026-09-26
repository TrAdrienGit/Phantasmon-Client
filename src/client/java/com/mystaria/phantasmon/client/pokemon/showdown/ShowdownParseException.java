package com.mystaria.phantasmon.client.pokemon.showdown;

/** A Showdown block (or line within one) that {@link ShowdownParser} could not make sense of. */
public final class ShowdownParseException extends RuntimeException {

	public ShowdownParseException(String message) {
		super(message);
	}
}
