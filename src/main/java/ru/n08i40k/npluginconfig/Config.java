package ru.n08i40k.npluginconfig;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import lombok.Getter;
import lombok.NonNull;
import meteordevelopment.orbit.IEventBus;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.inspector.TagInspector;
import ru.n08i40k.npluginconfig.event.ConfigLoadEvent;
import ru.n08i40k.npluginconfig.event.ConfigSaveEvent;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.util.*;

public class Config<T> {
    @Getter
    private final Plugin plugin;

    private final Logger logger;
    private final IEventBus eventBus;

    @Getter
    private final String id;
    @Getter
    private boolean loaded;

    private final ImmutableSet<String> allowedTags;

    private final Class<T> klass;

    @Nullable
    private T data;

    public Config(@NonNull Plugin plugin, @NonNull IEventBus eventBus, @NonNull String id,
                  @NonNull Class<T> klass, @Nullable Set<String> allowedTags) {
        this.plugin = plugin;
        this.eventBus = eventBus;
        this.id = id;
        this.klass = klass;

        logger = plugin.getSLF4JLogger();

        Set<String> resultSet = new HashSet<>();

        if (allowedTags != null)
            resultSet.addAll(allowedTags);

        resultSet.add(klass.getName());

        this.allowedTags = ImmutableSet.copyOf(resultSet);

        load();
    }

    @NotNull
    public T getData() {
        Preconditions.checkState(loaded,
                "Config with id %s is not loaded!", id);

        assert data != null;
        return data;
    }

    @NotNull
    private File getConfigsDirectory() {
        File directory = new File(plugin.getDataFolder(), "configs/");

        if (!directory.exists())
            Preconditions.checkState(directory.mkdirs(),
                    "Cannot create configuration directory at path %s!", directory.getPath());
        else
            Preconditions.checkState(directory.isDirectory(),
                    "There is something other than a folder along the path %s!", directory.getPath());

        return directory;
    }

    @NotNull
    public List<File> getAvailableConfigs() {
        File directory = getConfigsDirectory();

        File[] files = directory.listFiles();

        if (files == null)
            return ImmutableList.of();

        List<File> locales = new ArrayList<>();

        for (File file : files) {
            if (file.getName().endsWith(".yml")) {
                locales.add(file);
            }
        }

        return locales;
    }

    @NotNull
    public Set<String> getAvailableConfigNames() {
        Set<String> filenames = new HashSet<>();

        getAvailableConfigs().forEach(file ->
                filenames.add(file.getName().substring(0, file.getName().length() - 4)));

        return filenames;
    }

    private int lastLoadTick = 0;

    private boolean tryLoad() {
        loaded = false;

        LoaderOptions loaderOptions = new LoaderOptions();
        TagInspector tagInspector = tag -> allowedTags.contains(tag.getClassName());
        loaderOptions.setTagInspector(tagInspector);
        Yaml yaml = new Yaml(new Constructor(klass, loaderOptions));

        try (FileReader reader = new FileReader(
                getConfigsDirectory().getPath() + "/" + id + ".yml", Charsets.UTF_8)) {
            data = yaml.loadAs(reader, klass);

            loaded = true;
        } catch (FileNotFoundException e) {
            if (Bukkit.getServer().getCurrentTick() - lastLoadTick > 0) {
                logger.error("""
                        An error occurred while loading the configuration!
                        The previous attempt to save the configuration template and reload failed!
                        {}""", e.getMessage());

                return false;
            }

            lastLoadTick = Bukkit.getServer().getCurrentTick();

            saveTemplate();
            tryLoad();
        } catch (IOException e) {
            logger.error(e.getMessage());

            return false;
        }

        return true;
    }

    public boolean load() {
        eventBus.post(new ConfigLoadEvent.Pre<>(this));

        boolean successful = tryLoad();

        eventBus.post(new ConfigLoadEvent.Post<>(this, successful));

        return successful;
    }

    public void saveTemplate() {
        try {
            data = klass.getDeclaredConstructor().newInstance();
        } catch (InstantiationException | InvocationTargetException | IllegalAccessException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }

        loaded = true;
        trySave();
    }

    private boolean trySave() {
        if (!loaded) {
            logger.error("The configuration cannot be saved, because it has not yet been loaded!");

            return false;
        }

        DumperOptions dumperOptions = new DumperOptions();
        dumperOptions.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);

        Yaml yaml = new Yaml(dumperOptions);

        try (FileWriter writer = new FileWriter(
                getConfigsDirectory().getPath() + "/" + id + ".yml", Charsets.UTF_8)) {
            yaml.dump(data, writer);
        } catch (IOException e) {
            logger.error(e.getMessage());

            return false;
        }

        return true;
    }

    public boolean save() {
        eventBus.post(new ConfigSaveEvent.Pre<>(this));

        boolean successful = trySave();

        eventBus.post(new ConfigSaveEvent.Post<>(this, successful));

        return successful;
    }
}
