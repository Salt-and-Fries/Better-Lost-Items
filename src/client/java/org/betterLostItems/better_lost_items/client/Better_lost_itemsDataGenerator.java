package org.betterLostItems.better_lost_items.client;

import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;

/**
 * Placeholder Fabric data-generation entry point.
 *
 * <p>The mod currently ships hand-authored assets, but keeping this entry point makes it easy to
 * add generated recipes, tags, or language files later without reworking the build setup.</p>
 */
public class Better_lost_itemsDataGenerator implements DataGeneratorEntrypoint {

    /**
     * Creates the generated-data pack when data generation is run.
     */
    @Override
    public void onInitializeDataGenerator(FabricDataGenerator fabricDataGenerator) {
        FabricDataGenerator.Pack pack = fabricDataGenerator.createPack();
    }
}
