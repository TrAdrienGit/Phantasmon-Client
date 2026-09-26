package com.mystaria.phantasmon.client.trade;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionException;

import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import com.mystaria.phantasmon.client.auth.AuthSession;
import com.mystaria.phantasmon.client.network.BackendApiException;
import com.mystaria.phantasmon.client.network.BackendErrorMessages;

/**
 * Business logic behind {@code /phantasmon trade *} (CAD Phase 8) — same
 * all-commands approach as Phases 5-7 (Adrien: 2026-09-26, reconfirmed each
 * phase). Also implements {@link TradeNotificationListener} to show the
 * backend's real-time {@code TradeProposed}/{@code TradeAccepted}/
 * {@code TradeCancelled} WS events as chat messages.
 */
public final class TradeCommandHandler implements TradeNotificationListener {

	private final TradeClient tradeClient;
	private final AuthSession session;

	public TradeCommandHandler(TradeClient tradeClient, AuthSession session) {
		this.tradeClient = tradeClient;
		this.session = session;
	}

	public void propose(FabricClientCommandSource source, UUID recipientUuid, UUID offeredPokemonUuid, UUID requestedPokemonUuid) {
		if (!requireAuthenticated(source)) {
			return;
		}
		ProposeTradeRequestDto request = new ProposeTradeRequestDto(UUID.randomUUID(), recipientUuid, offeredPokemonUuid, requestedPokemonUuid);
		tradeClient.propose(session.accessToken(), request)
				.thenAccept(trade -> feedback(source, Component.translatable("phantasmon.trade.propose.done", tradeUuidComponent(trade.uuid()))))
				.exceptionally(ex -> {
					reportFailure(source, ex);
					return null;
				});
	}

	public void accept(FabricClientCommandSource source, UUID tradeUuid) {
		if (!requireAuthenticated(source)) {
			return;
		}
		tradeClient.accept(session.accessToken(), tradeUuid)
				.thenAccept(trade -> feedback(source, Component.translatable("phantasmon.trade.accept.done")))
				.exceptionally(ex -> {
					reportFailure(source, ex);
					return null;
				});
	}

	public void cancel(FabricClientCommandSource source, UUID tradeUuid) {
		if (!requireAuthenticated(source)) {
			return;
		}
		tradeClient.cancel(session.accessToken(), tradeUuid)
				.thenAccept(trade -> feedback(source, Component.translatable("phantasmon.trade.cancel.done")))
				.exceptionally(ex -> {
					reportFailure(source, ex);
					return null;
				});
	}

	public void view(FabricClientCommandSource source, UUID tradeUuid) {
		if (!requireAuthenticated(source)) {
			return;
		}
		tradeClient.get(session.accessToken(), tradeUuid)
				.thenAccept(trade -> feedback(source, summaryLine(trade)))
				.exceptionally(ex -> {
					reportFailure(source, ex);
					return null;
				});
	}

	public void list(FabricClientCommandSource source) {
		if (!requireAuthenticated(source)) {
			return;
		}
		tradeClient.listForPlayer(session.accessToken(), session.playerUuid())
				.thenAccept(trades -> {
					if (trades.length == 0) {
						feedback(source, Component.translatable("phantasmon.trade.list.empty"));
						return;
					}
					for (TradeDto trade : trades) {
						feedback(source, summaryLine(trade));
					}
				})
				.exceptionally(ex -> {
					reportFailure(source, ex);
					return null;
				});
	}

	@Override
	public void onTradeProposed(Map<String, Object> data) {
		reportEvent("phantasmon.trade.event.proposed", data);
	}

	@Override
	public void onTradeAccepted(Map<String, Object> data) {
		reportEvent("phantasmon.trade.event.accepted", data);
	}

	@Override
	public void onTradeCancelled(Map<String, Object> data) {
		reportEvent("phantasmon.trade.event.cancelled", data);
	}

	private void reportEvent(String translationKey, Map<String, Object> data) {
		Object tradeUuid = data.get("trade_uuid");
		Minecraft client = Minecraft.getInstance();
		if (client.player != null) {
			client.player.displayClientMessage(Component.translatable(translationKey,
					tradeUuidComponent(tradeUuid == null ? null : UUID.fromString(tradeUuid.toString()))), false);
		}
	}

	private boolean requireAuthenticated(FabricClientCommandSource source) {
		if (!session.isAuthenticated()) {
			source.sendError(Component.translatable("phantasmon.error.not_authenticated"));
			return false;
		}
		return true;
	}

	private static MutableComponent summaryLine(TradeDto trade) {
		String label = trade.status() + " ";
		return Component.literal(label).append(tradeUuidComponent(trade.uuid()));
	}

	/** Full UUID shown as plain text (never truncated — see feedback_phantasmon_full_uuids_in_chat), click pre-fills the chat box as a copy shortcut. */
	private static MutableComponent tradeUuidComponent(UUID uuid) {
		if (uuid == null) {
			return Component.literal("?");
		}
		return Component.literal(uuid.toString())
				.withStyle(ChatFormatting.GRAY, ChatFormatting.UNDERLINE)
				.withStyle(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, uuid.toString())));
	}

	private static void reportFailure(FabricClientCommandSource source, Throwable throwable) {
		Throwable cause = throwable instanceof CompletionException ? throwable.getCause() : throwable;
		String translationKey = cause instanceof BackendApiException apiException
				? BackendErrorMessages.translationKey(apiException.errorCode())
				: "phantasmon.error.network";
		Minecraft.getInstance().execute(() -> source.sendError(Component.translatable(translationKey)));
	}

	private static void feedback(FabricClientCommandSource source, Component message) {
		Minecraft.getInstance().execute(() -> source.sendFeedback(message));
	}
}
