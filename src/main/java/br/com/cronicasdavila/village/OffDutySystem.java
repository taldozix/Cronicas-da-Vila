package br.com.cronicasdavila.village;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import br.com.cronicasdavila.compat.VanillaCompat;
import br.com.cronicasdavila.config.ModSettings;
import br.com.cronicasdavila.net.ModPayloads;
import br.com.cronicasdavila.rank.Professions;

/**
 * Folga dos aldeões. Cada vila tem a sua "cultura": um ciclo de alguns dias (que muda de vila para vila),
 * e cada aldeão descansa no seu dia do ciclo, então a vila nunca para inteira. Trabalhos pesados
 * descansam dois dias por ciclo.
 *
 * <p>Na folga o aldeão não vai para o bloco de trabalho (a {@code BrainMixin} troca o "modo trabalho"
 * pelo "modo livre"), não passa missões nem abre a banca, tira o uniforme (no cliente) e fica segurando
 * algo que lembra a profissão.
 */
public final class OffDutySystem {
	private static final double RANGE = 80.0;
	/** Cérebros dos aldeões de folga agora (lido pela mixin a cada troca de atividade). */
	private static final Map<Brain<?>, UUID> OFF_BRAINS = Collections.synchronizedMap(new WeakHashMap<>());
	private static final Set<UUID> OFF = new HashSet<>();
	/** Aldeões que estão segurando o item de folga que nós colocamos. */
	private static final Map<UUID, Item> HINTS = new HashMap<>();
	private static final Map<UUID, String> LAST_SENT = new HashMap<>();

	private static final Map<String, String> HINT_ITEMS = Map.ofEntries(
			Map.entry("farmer", "minecraft:wheat"),
			Map.entry("fisherman", "minecraft:cod"),
			Map.entry("shepherd", "minecraft:shears"),
			Map.entry("butcher", "minecraft:porkchop"),
			Map.entry("leatherworker", "minecraft:leather"),
			Map.entry("fletcher", "minecraft:arrow"),
			Map.entry("mason", "minecraft:brick"),
			Map.entry("toolsmith", "minecraft:wooden_pickaxe"),
			Map.entry("weaponsmith", "minecraft:wooden_sword"),
			Map.entry("armorer", "minecraft:leather_helmet"),
			Map.entry("cartographer", "minecraft:paper"),
			Map.entry("librarian", "minecraft:book"),
			Map.entry("cleric", "minecraft:glass_bottle"),
			// Profissões do More Villagers: Re-employed.
			Map.entry("oceanographer", "minecraft:prismarine_shard"),
			Map.entry("netherian", "minecraft:nether_wart"),
			Map.entry("woodworker", "minecraft:oak_sapling"),
			Map.entry("enderian", "minecraft:chorus_fruit"),
			Map.entry("engineer", "minecraft:redstone"),
			Map.entry("florist", "minecraft:poppy"),
			Map.entry("hunter", "minecraft:rabbit_hide"),
			Map.entry("miner", "minecraft:coal"),
			Map.entry("iceman", "minecraft:snowball"),
			Map.entry("explorer", "minecraft:map")
	);

	private OffDutySystem() {
	}

	/** Chamado pela mixin: esse cérebro é de um aldeão de folga? */
	public static boolean isBrainOff(Brain<?> brain) {
		return OFF_BRAINS.containsKey(brain);
	}

	/** O aldeão está de folga hoje? (Atualizado a cada 2 segundos perto dos jogadores.) */
	public static boolean isOff(Villager villager) {
		return OFF.contains(villager.getUUID());
	}

	private static boolean enabled() {
		return ModSettings.get().heavyDayOff;
	}

	/** Quem tem folga: aldeões adultos com profissão (vanilla ou de outros mods). */
	private static boolean hasJob(Villager villager) {
		String key = VanillaCompat.professionKey(villager);
		return !villager.isBaby() && !key.equals(Professions.UNEMPLOYED) && !key.equals(Professions.NITWIT);
	}

	/** A vila do aldeão: o sino onde ele se reúne, ou (sem sino) o bairro onde está. */
	private static String villageKey(Villager villager) {
		GlobalPos meeting = villager.getBrain().getMemory(MemoryModuleType.MEETING_POINT).orElse(null);

		if (meeting != null) {
			BlockPos pos = meeting.pos();
			return "bell:" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
		}

		BlockPos pos = villager.blockPosition();
		return "area:" + VanillaCompat.villagerType(villager) + ":" + (pos.getX() >> 7) + "," + (pos.getZ() >> 7);
	}

	/** Tamanho do ciclo de folga da vila: o valor das opções mais 0 a 2 dias, conforme a "cultura" da vila. */
	static int cycleLength(String villageKey) {
		int base = Math.max(3, ModSettings.get().heavyEveryDays);
		return base + Math.floorMod(villageKey.hashCode() * 31 + 7, 3);
	}

	public static void tick(MinecraftServer server) {
		Set<UUID> nowOff = new HashSet<>();
		Map<String, List<Villager>> villages = new HashMap<>();
		Set<UUID> seen = new HashSet<>();

		if (enabled()) {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				ServerLevel level = (ServerLevel) player.level();

				for (Villager villager : level.getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(RANGE),
						villager -> villager.isAlive() && hasJob(villager))) {
					if (seen.add(villager.getUUID())) {
						villages.computeIfAbsent(villageKey(villager), key -> new ArrayList<>()).add(villager);
					}
				}
			}
		}

		for (Map.Entry<String, List<Villager>> village : villages.entrySet()) {
			List<Villager> villagers = village.getValue();
			villagers.sort(Comparator.comparing(Villager::getUUID));
			int cycle = cycleLength(village.getKey());
			int start = Math.floorMod(village.getKey().hashCode(), cycle);
			long day = VanillaCompat.day(villagers.get(0).level());
			int today = (int) Math.floorMod(day, (long) cycle);
			List<Villager> resting = new ArrayList<>();

			for (int i = 0; i < villagers.size(); i++) {
				Villager villager = villagers.get(i);
				int restDay = Math.floorMod(start + i, cycle);
				boolean heavy = Professions.HEAVY_PROFESSIONS.contains(VanillaCompat.professionKey(villager));
				boolean off = today == restDay || (heavy && today == Math.floorMod(restDay + cycle / 2, cycle));

				if (off) {
					resting.add(villager);
				}
			}

			// A vila nunca para inteira: se todos cairiam de folga no mesmo dia, o primeiro fica trabalhando.
			if (!resting.isEmpty() && resting.size() == villagers.size()) {
				resting.remove(0);
			}

			for (Villager villager : resting) {
				nowOff.add(villager.getUUID());
			}
		}

		for (Map.Entry<String, List<Villager>> village : villages.entrySet()) {
			for (Villager villager : village.getValue()) {
				apply(villager, nowOff.contains(villager.getUUID()));
			}
		}

		// Quem não está perto de nenhum jogador volta à rotina normal do jogo.
		OFF.clear();
		OFF.addAll(nowOff);
		synchronized (OFF_BRAINS) {
			OFF_BRAINS.values().removeIf(id -> !nowOff.contains(id));
		}

		HINTS.keySet().retainAll(seen);
		sendToPlayers(server, villages);
	}

	private static void apply(Villager villager, boolean off) {
		Brain<Villager> brain = villager.getBrain();

		if (off) {
			OFF_BRAINS.put(brain, villager.getUUID());

			if (brain.isActive(Activity.WORK)) {
				brain.setActiveActivityIfPossible(Activity.IDLE);
			}

			Item hint = hintItem(VanillaCompat.professionKey(villager));

			if (hint != null && villager.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty() && !villager.isTrading()) {
				villager.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(hint));
				HINTS.put(villager.getUUID(), hint);
			}
		} else {
			OFF_BRAINS.remove(brain);
			clearHint(villager);
		}
	}

	private static void clearHint(Villager villager) {
		Item hint = HINTS.remove(villager.getUUID());

		if (hint != null && villager.getItemBySlot(EquipmentSlot.MAINHAND).is(hint)) {
			villager.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		}
	}

	private static Item hintItem(String profession) {
		Identifier id = Identifier.tryParse(HINT_ITEMS.getOrDefault(profession, "minecraft:emerald"));
		return id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
	}

	/** Manda para cada jogador a lista dos aldeões de folga por perto (o cliente tira o uniforme deles). */
	private static void sendToPlayers(MinecraftServer server, Map<String, List<Villager>> villages) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			List<Integer> ids = new ArrayList<>();

			for (List<Villager> villagers : villages.values()) {
				for (Villager villager : villagers) {
					if (OFF.contains(villager.getUUID()) && villager.level() == player.level()
							&& villager.distanceToSqr(player) < (RANGE + 16) * (RANGE + 16)) {
						ids.add(villager.getId());
					}
				}
			}

			Collections.sort(ids);
			String key = ids.toString();

			if (key.equals(LAST_SENT.get(player.getUUID()))) {
				continue;
			}

			LAST_SENT.put(player.getUUID(), key);
			JsonObject json = new JsonObject();
			json.addProperty("type", "off_duty");
			JsonArray array = new JsonArray();
			ids.forEach(array::add);
			json.add("ids", array);
			ServerPlayNetworking.send(player, new ModPayloads.DialoguePayload(json.toString()));
		}
	}

	public static void forget(ServerPlayer player) {
		LAST_SENT.remove(player.getUUID());
	}

	/** Ao fechar o mundo, ninguém fica com o item de folga salvo na mão. */
	public static void clearAll(MinecraftServer server) {
		for (ServerLevel level : server.getAllLevels()) {
			for (UUID id : new ArrayList<>(HINTS.keySet())) {
				if (level.getEntity(id) instanceof Villager villager) {
					clearHint(villager);
				}
			}
		}

		HINTS.clear();
		OFF.clear();
		OFF_BRAINS.clear();
		LAST_SENT.clear();
	}
}
