package br.com.cronicasdavila.karma;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.phys.Vec3;

import br.com.cronicasdavila.compat.VanillaCompat;
import br.com.cronicasdavila.data.PlayerData;
import br.com.cronicasdavila.data.PlayerDataManager;
import br.com.cronicasdavila.dialogue.DialogueManager;
import br.com.cronicasdavila.quest.NpcDialogue;
import br.com.cronicasdavila.quest.QuestRegistry;
import br.com.cronicasdavila.util.Text;

/**
 * Roubo de baús na vila: se um aldeão te vir tirando coisas de um baú que não é seu,
 * você perde karma, a vila inteira fica sabendo e os golens de ferro vêm atrás de você.
 *
 * <p>Um baú é "seu" quando você o encontrou vazio (acabou de colocar) ou guardou coisas nele.
 * Baús de estrutura que ainda não foram abertos nunca são seus.</p>
 */
public final class TheftSystem {
	private static final double WITNESS_RANGE = 16.0;
	private static final double GOLEM_RANGE = 32.0;
	private static final int KARMA = -20;
	private static final int MAX_OWN_CHESTS = 512;

	private static final Map<UUID, Watch> WATCHING = new HashMap<>();

	/** O baú que o jogador está mexendo agora. */
	private static final class Watch {
		final String key;
		final boolean natural;
		int start = -1;
		int last = -1;
		boolean deposited;
		boolean caught;

		Watch(String key, boolean natural) {
			this.key = key;
			this.natural = natural;
		}
	}

	private TheftSystem() {
	}

	private static String key(ServerLevel level, BlockPos pos) {
		return level.dimension().identifier() + "|" + pos.getX() + "|" + pos.getY() + "|" + pos.getZ();
	}

	/** Botão direito num bloco: se for um baú da vila que não é do jogador, começa a vigiar. */
	public static void onUseBlock(ServerPlayer player, ServerLevel level, BlockPos pos) {
		if (player.isCreative() || player.isSpectator() || !br.com.cronicasdavila.config.ModSettings.get().theft) {
			return;
		}

		String id = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString();

		if (!id.equals("minecraft:chest") && !id.equals("minecraft:trapped_chest") && !id.equals("minecraft:barrel")) {
			return;
		}

		if (!VanillaCompat.isVillage(level, pos)) {
			return;
		}

		String key = key(level, pos);

		if (PlayerDataManager.get(player).ownChests.contains(key)) {
			return;
		}

		WATCHING.put(player.getUUID(), new Watch(key, VanillaCompat.hasUnopenedLoot(level.getBlockEntity(pos))));
	}

	public static void forget(ServerPlayer player) {
		WATCHING.remove(player.getUUID());
	}

	/** A cada meio segundo: confere se o baú aberto ficou mais vazio e se alguém viu. */
	public static void tick(ServerPlayer player) {
		Watch watch = WATCHING.get(player.getUUID());

		if (watch == null) {
			return;
		}

		if (!(player.containerMenu instanceof ChestMenu menu)) {
			// Fechou o baú. Baú vazio que não é de estrutura, ou onde ele guardou coisas, passa a ser dele.
			WATCHING.remove(player.getUUID());

			if (!watch.caught && !watch.natural && (watch.start == 0 || (watch.deposited && watch.last >= watch.start))) {
				PlayerData data = PlayerDataManager.get(player);

				if (data.ownChests.size() < MAX_OWN_CHESTS) {
					data.ownChests.add(watch.key);
					PlayerDataManager.save(player);
				}
			}

			return;
		}

		int count = count(menu.getContainer());

		if (watch.start < 0) {
			watch.start = count;
			watch.last = count;
			return;
		}

		if (count > watch.last) {
			watch.deposited = true;
		}

		watch.last = count;

		if (!watch.caught && count < watch.start) {
			List<Villager> witnesses = witnesses(player);

			if (!witnesses.isEmpty()) {
				watch.caught = true;
				caught(player, witnesses);
			}
		}
	}

	private static int count(Container container) {
		int total = 0;

		for (int i = 0; i < container.getContainerSize(); i++) {
			total += container.getItem(i).getCount();
		}

		return total;
	}

	/** Aldeões acordados, por perto, com o jogador no campo de visão. */
	private static List<Villager> witnesses(ServerPlayer player) {
		ServerLevel level = (ServerLevel) player.level();
		return level.getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(WITNESS_RANGE),
				villager -> villager.isAlive() && !villager.isSleeping() && !villager.isNoAi() && sees(villager, player));
	}

	private static boolean sees(Villager villager, ServerPlayer player) {
		if (!villager.hasLineOfSight(player)) {
			return false;
		}

		Vec3 toPlayer = player.getEyePosition().subtract(villager.getEyePosition()).normalize();
		return villager.getViewVector(1.0F).dot(toPlayer) > 0.2;
	}

	private static void caught(ServerPlayer player, List<Villager> witnesses) {
		ServerLevel level = (ServerLevel) player.level();
		KarmaSystem.change(player, KARMA, "um aldeão te viu roubando um baú da vila");

		Villager first = witnesses.get(0);
		String profession = VanillaCompat.professionKey(first);
		NpcDialogue lines = QuestRegistry.dialogue(profession, VanillaCompat.villagerType(first));
		List<String> shouts = lines.extra("thief_seen");
		String shout = shouts.isEmpty() ? "<r>Ladrão!</r> Socorro, golem!" : shouts.get(player.getRandom().nextInt(shouts.size()));
		br.com.cronicasdavila.net.Notify.send(player, ("<y>" + DialogueManager.npcName(first, lines) + ":</y> "
				+ shout.replace("{player}", player.getScoreboardName())));

		for (Villager villager : witnesses) {
			VanillaCompat.addGossip(villager, player.getUUID(), false, 15);
			VanillaCompat.villagerFlee(villager, player);
			level.playSound(null, villager.getX(), villager.getY(), villager.getZ(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 1.0F, 1.0F);
		}

		// A segurança da vila: todos os golens por perto vêm atrás do ladrão.
		List<Mob> golems = level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(GOLEM_RANGE),
				mob -> mob.isAlive() && KarmaSystem.typeId(mob).equals("minecraft:iron_golem"));

		for (Mob golem : golems) {
			VanillaCompat.angerGolem(golem, player);
		}

		if (!golems.isEmpty()) {
			player.sendOverlayMessage(Text.parse("<r>☠ A segurança da vila está atrás de você!</r>"));
		}
	}
}
