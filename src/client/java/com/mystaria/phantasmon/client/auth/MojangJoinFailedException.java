package com.mystaria.phantasmon.client.auth;

/** Unchecked wrapper for a Mojang {@code joinServer} failure, to cross a {@code CompletableFuture} boundary. */
final class MojangJoinFailedException extends RuntimeException {

	MojangJoinFailedException(Throwable cause) {
		super(cause);
	}
}
