package br.com.cronicasdavila.config;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import br.com.cronicasdavila.data.PlayerDataManager;
import br.com.cronicasdavila.net.Notify;

/**
 * Mudanças nas opções do mundo vindas da aba Opções do menu.
 * Só o dono do mundo (no singleplayer/LAN) ou um operador pode mexer.
 */
public final class SettingsService {
	private SettingsService() {
	}

	public static boolean canEdit(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();

		if (server == null) {
			return false;
		}

		return server.isSingleplayerOwner(player.nameAndId()) || server.getPlayerList().isOp(player.nameAndId());
	}

	/** Ações "setting:<chave>:<valor>" e "setting_reset". Retorna true se tratou. */
	public static boolean onAction(ServerPlayer player, String action) {
		if (!action.startsWith("setting")) {
			return false;
		}

		if (!canEdit(player)) {
			Notify.send(player, "<r>Só o dono do mundo ou um operador pode mudar as opções do Crônicas da Vila.</r>");
			return true;
		}

		if (action.equals("setting_reset")) {
			ModSettings.reset();
			Notify.send(player, "<a>Opções do Crônicas da Vila voltaram ao padrão.</a>");
		} else if (action.startsWith("setting:")) {
			String[] parts = action.split(":");

			if (parts.length == 3) {
				try {
					ModSettings.set(parts[1], Integer.parseInt(parts[2]));
				} catch (NumberFormatException ignored) {
					return true;
				}
			}
		}

		// Todo mundo recebe as opções novas.
		MinecraftServer server = player.level().getServer();

		if (server != null) {
			for (ServerPlayer other : server.getPlayerList().getPlayers()) {
				PlayerDataManager.markDirty(other);
			}
		}

		return true;
	}
}
