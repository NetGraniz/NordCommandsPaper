package com.nordfjell.nordcommandspaper;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;
import org.yaml.snakeyaml.*;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/** Immutable, strictly parsed policy. Never coerces an argument-bearing entry into a root. */
record CommandSettings(Set<String> allowed, String deniedMessage) {
    static final int MAX_BYTES = 1024 * 1024, MAX_COMMANDS = 256;
    CommandSettings {
        allowed = Set.copyOf(allowed);
        Objects.requireNonNull(deniedMessage);
    }
    static CommandSettings load(Path file) throws IOException {
        if (!Files.isRegularFile(file) || Files.size(file) > MAX_BYTES)
            throw new IOException("Configuration missing, unreadable or oversized.");
        try {
            byte[] bytes = Files.readAllBytes(file);
            if (bytes.length > MAX_BYTES) throw new IOException("Configuration size limit.");
            String text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
            LoaderOptions options = new LoaderOptions();
            options.setAllowDuplicateKeys(false);
            options.setCodePointLimit(MAX_BYTES);
            options.setNestingDepthLimit(8);
            options.setMaxAliasesForCollections(0);
            Object value = new Yaml(new SafeConstructor(options)).load(text);
            if (!(value instanceof Map<?, ?> root)) throw new IOException("Configuration must be a map.");
            for (Object key : root.keySet())
                if (!Set.of("allowed-commands", "denied-message").contains(key))
                    throw new IOException("Unknown configuration field.");
            if (!(root.get("allowed-commands") instanceof List<?> entries) || entries.size() > MAX_COMMANDS)
                throw new IOException("allowed-commands must be a bounded list.");
            Set<String> commands = new HashSet<>();
            for (Object entry : entries) {
                if (!(entry instanceof String label) || !label.matches("[A-Za-z0-9_-]{1,64}"))
                    throw new IOException("Each allowed-commands entry must be a bare command label.");
                commands.add(label.toLowerCase(Locale.ROOT));
            }
            Object denial = root.containsKey("denied-message") ? root.get("denied-message") : "No such command.";
            if (!(denial instanceof String message) || message.length() > 512
                    || message.codePoints().anyMatch(cp -> Character.isISOControl(cp) || cp == 0x2028 || cp == 0x2029))
                throw new IOException("Invalid denied-message.");
            return new CommandSettings(commands, message);
        } catch (IOException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new IOException("Invalid YAML.", e);
        }
    }
    boolean allows(String label) {
        return label != null && !label.contains(":") && allowed.contains(label.toLowerCase(Locale.ROOT));
    }
}

