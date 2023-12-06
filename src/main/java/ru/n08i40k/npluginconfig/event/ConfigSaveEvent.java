package ru.n08i40k.npluginconfig.event;

import lombok.Getter;
import lombok.NonNull;
import ru.n08i40k.npluginconfig.Config;

@Getter
public class ConfigSaveEvent<T> {
    private final String configName;
    private final Config<T> config;

    protected ConfigSaveEvent(@NonNull Config<T> config) {
        this.config = config;
        this.configName = config.getId();
    }

    public static class Pre<T> extends ConfigSaveEvent<T> {
        public Pre(@NonNull Config<T> config) {
            super(config);
        }
    }

    @Getter
    public static class Post<T> extends ConfigSaveEvent<T> {
        private final boolean successful;

        public Post(@NonNull Config<T> config, boolean successful) {
            super(config);

            this.successful = successful;
        }
    }
}
