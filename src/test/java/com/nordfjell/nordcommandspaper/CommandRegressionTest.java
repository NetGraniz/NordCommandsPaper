package com.nordfjell.nordcommandspaper;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public final class CommandRegressionTest {
    private static int passed;
    private static void pass(String name) { passed++; System.out.println("PASS " + name); }
    private static CommandSettings config(String text) throws Exception {
        Path file = Files.createTempDirectory("nordcommands-unit-").resolve("config.yml");
        Files.writeString(file, text); return CommandSettings.load(file);
    }
    private static void reject(String text) throws Exception {
        Path file = Files.createTempDirectory("nordcommands-invalid-").resolve("config.yml");
        Files.writeString(file, text); byte[] before = Files.readAllBytes(file);
        try { CommandSettings.load(file); throw new AssertionError("Invalid policy accepted"); }
        catch (IOException expected) { assert Arrays.equals(before, Files.readAllBytes(file)); }
    }
    private static void until(java.util.function.BooleanSupplier predicate) throws Exception {
        long end = System.nanoTime() + 3_000_000_000L;
        while (!predicate.getAsBoolean()) {
            if (System.nanoTime() >= end) throw new AssertionError("Test timeout");
            Thread.sleep(5);
        }
    }
    public static void main(String[] args) throws Exception {
        CommandSettings settings = config("allowed-commands: [msg, HOME, home]\n");
        assert settings.allowed().equals(Set.of("msg", "home"));
        assert settings.deniedMessage().equals("No such command.");
        try { settings.allowed().add("op"); throw new AssertionError(); } catch (UnsupportedOperationException expected) {}
        pass("Strict labels, case normalization, harmless deduplication and immutable snapshot");
        assert config("allowed-commands: []\n").allowed().isEmpty();
        pass("Explicit empty list means deny player commands, not a parse failure");
        assert !settings.allows("minecraft:msg") && !settings.allows("op") && settings.allows("MSG");
        pass("Allowlist does not widen to namespaced or unlisted labels");
        for (String text : List.of("allowed-commands: ['/msg']\n", "allowed-commands: ['msg Bob']\n",
                "allowed-commands: ['minecraft:msg']\n", "allowed-commands: [' msg']\n",
                "allowed-commands: ['']\n", "allowed-commands: [42]\n", "allowed-commands: [true]\n"))
            reject(text);
        pass("Slash, arguments, namespace, whitespace and non-string entries rejected");
        for (String text : List.of("allowed-commands: null\n", "allowed-commands: msg\n", "{}\n", "null\n", "", "wrong: []\n"))
            reject(text);
        pass("Missing, null, wrong-typed, blank and unknown policy structure fail closed");
        reject("allowed-commands: [msg]\nallowed-commands: [op]\n");
        reject("allowed-commands: !!java.util.ArrayList []\n");
        reject("allowed-commands: &x [msg]\ndenied-message: *x\n");
        pass("Duplicate YAML keys, unsafe tags and invalid references rejected");
        reject("allowed-commands: [msg]\ndenied-message: null\n");
        reject("allowed-commands: [msg]\ndenied-message: true\n");
        reject("allowed-commands: [msg]\ndenied-message: \"line\\nline\"\n");
        reject("allowed-commands: [msg]\ndenied-message: '" + "x".repeat(513) + "'\n");
        pass("Denial text type, control characters and length bounded");
        StringJoiner tooMany = new StringJoiner(",", "allowed-commands: [", "]\n");
        for (int i = 0; i <= CommandSettings.MAX_COMMANDS; i++) tooMany.add("cmd" + i);
        reject(tooMany.toString());
        reject("allowed-commands: ['" + "a".repeat(65) + "']\n");
        pass("Policy entry count and label length capped");
        Path invalid = Files.createTempDirectory("nordcommands-file-").resolve("config.yml");
        Files.write(invalid, new byte[]{(byte) 0xc3, 0x28});
        try { CommandSettings.load(invalid); throw new AssertionError(); } catch (IOException expected) {}
        Files.write(invalid, new byte[CommandSettings.MAX_BYTES + 1]);
        try { CommandSettings.load(invalid); throw new AssertionError(); } catch (IOException expected) {}
        try { CommandSettings.load(invalid.resolveSibling("absent.yml")); throw new AssertionError(); } catch (IOException expected) {}
        pass("Invalid UTF8, oversized and missing files fail without writes");
        assert CommandInput.executionLabel("/MsG Bob two  spaces; /op fake").equals("msg");
        assert CommandInput.label("MSG").equals("msg");
        assert CommandInput.executionLabel("/minecraft:msg Bob text").equals("minecraft:msg");
        pass("Input reads routing root without rewriting argument payload");
        for (String input : List.of("", "/", "//msg Bob text", " /msg Bob text", "/ msg", "/m\u200bsg Bob",
                "/msg\tBob text", "/msg Bob\ntext", "/msg Bob\u0000text", "/msg Bob\u2028text"))
            assert CommandInput.executionLabel(input) == null : input;
        assert CommandInput.executionLabel("msg Bob text") == null;
        assert CommandInput.executionLabel("/" + "a".repeat(65)) == null;
        assert CommandInput.executionLabel("/msg " + "x".repeat(CommandInput.MAX_INPUT)) == null;
        pass("Malformed slash, whitespace, controls and oversized inputs denied");
        assert CommandInput.administrative("nordcommands");
        assert CommandInput.administrative("nordcommands:nordcommands");
        assert !CommandInput.administrative("other:nordcommands");
        pass("Only plugin-owned management roots receive the explicit admin exception");
        Locale original = Locale.getDefault();
        try { Locale.setDefault(Locale.forLanguageTag("tr-TR")); assert CommandInput.executionLabel("/LOGIN x").equals("login"); }
        finally { Locale.setDefault(original); }
        pass("Routing normalization does not depend on the machine locale");
        RefreshQueue<Integer> queue = new RefreshQueue<>();
        queue.replace(java.util.stream.IntStream.range(0, 1000).boxed().toList());
        AtomicInteger refreshed = new AtomicInteger();
        queue.drain(i -> refreshed.incrementAndGet());
        assert refreshed.get() == 8 && queue.size() == 992;
        while (queue.size() > 0) queue.drain(i -> refreshed.incrementAndGet());
        assert refreshed.get() == 1000;
        pass("1000 view refreshes respect an eight-player-per-tick budget");
        queue.replace(List.of(1, 2, 3)); queue.replace(List.of(4));
        List<Integer> delivered = new ArrayList<>(); queue.drain(delivered::add);
        assert delivered.equals(List.of(4));
        assert !queue.replace(java.util.stream.IntStream.range(0, RefreshQueue.MAX_PENDING + 1).boxed().toList());
        assert queue.size() == RefreshQueue.MAX_PENDING;
        queue.clear(); assert queue.size() == 0;
        pass("Refresh replacement coalesces old work and enforces hard capacity");
        try (ReloadGate<String> gate = new ReloadGate<>("NordCommands-unit-reader")) {
            CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1);
            assert gate.start(() -> { started.countDown(); release.await(); return "valid"; });
            assert started.await(2, TimeUnit.SECONDS);
            for (int i = 0; i < 1000; i++) assert !gate.start(() -> "unexpected");
            assert gate.busy();
            AtomicReference<ReloadGate.Outcome<String>> outcome = new AtomicReference<>();
            gate.drain(outcome::set); assert outcome.get() == null;
            release.countDown();
            until(() -> { gate.drain(outcome::set); return outcome.get() != null; });
            assert outcome.get().value().equals("valid") && !gate.busy();
            pass("Slow reader rejects 1000 duplicate reload requests without blocking caller");
            assert gate.start(() -> { throw new IOException("Synthetic invalid policy"); });
            outcome.set(null);
            until(() -> { gate.drain(outcome::set); return outcome.get() != null; });
            assert outcome.get().value() == null && outcome.get().error().equals("Synthetic invalid policy") && !gate.busy();
            pass("Failed reload returns an error without publishing a replacement policy");
            AtomicReference<Thread> applyThread = new AtomicReference<>();
            assert gate.start(() -> "second");
            until(() -> { gate.drain(result -> applyThread.set(Thread.currentThread())); return applyThread.get() != null; });
            assert applyThread.get() == Thread.currentThread();
            pass("Publication callback executes on the drain caller, not the file-reader thread");
        }
        ReloadGate<String> closing = new ReloadGate<>("NordCommands-unit-close");
        CountDownLatch entered = new CountDownLatch(1);
        closing.start(() -> { entered.countDown(); Thread.sleep(30000); return "stale"; });
        assert entered.await(2, TimeUnit.SECONDS); closing.close();
        assert !closing.start(() -> "after-close");
        closing.drain(value -> { throw new AssertionError("Closed gate published stale data"); });
        pass("Closing interrupts reader, rejects new jobs and discards stale results");
        System.out.println("ALL_COMMAND_REGRESSION_TESTS_PASSED count=" + passed);
    }
}

