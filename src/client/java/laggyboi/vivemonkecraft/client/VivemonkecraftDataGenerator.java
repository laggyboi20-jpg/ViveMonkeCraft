package laggyboi.vivemonkecraft.client;

import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;

public class VivemonkecraftDataGenerator implements DataGeneratorEntrypoint {

    @Override
    public void onInitializeDataGenerator(FabricDataGenerator fabricDataGenerator) {
        // No generated assets/data yet — entrypoint kept so `gradlew run Data gen`
        // works the moment something needs generating.
    }
}
