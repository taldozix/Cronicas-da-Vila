package br.com.cronicasdavila.client;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonArray;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.entity.npc.villager.VillagerProfession;

/**
 * Folga, no cliente: os aldeões de folga aparecem sem o uniforme da profissão (só com a roupa da vila).
 * Quando a folga acaba, o uniforme volta.
 */
final class OffDutyClient {
	private static final Set<Integer> OFF = new HashSet<>();
	/** Profissão de verdade de cada aldeão que estamos mostrando sem uniforme. */
	private static final Map<Integer, Holder<VillagerProfession>> ORIGINAL = new HashMap<>();

	private OffDutyClient() {
	}

	static void update(JsonArray ids) {
		OFF.clear();
		ids.forEach(id -> OFF.add(id.getAsInt()));
	}

	static void clear() {
		OFF.clear();
		ORIGINAL.clear();
	}

	static void tick(Minecraft client) {
		if (client.level == null) {
			ORIGINAL.clear();
			return;
		}

		for (Integer id : OFF) {
			Entity entity = client.level.getEntity(id);

			if (entity instanceof Villager villager) {
				VillagerData data = villager.getVillagerData();

				if (!data.profession().is(VillagerProfession.NONE)) {
					ORIGINAL.put(id, data.profession());
					villager.setVillagerData(data.withProfession(client.level.registryAccess(), VillagerProfession.NONE));
				}
			}
		}

		ORIGINAL.entrySet().removeIf(entry -> {
			if (OFF.contains(entry.getKey())) {
				return false;
			}

			Entity entity = client.level.getEntity(entry.getKey());

			if (entity instanceof Villager villager && villager.getVillagerData().profession().is(VillagerProfession.NONE)) {
				villager.setVillagerData(villager.getVillagerData().withProfession(entry.getValue()));
			}

			return true;
		});
	}
}
