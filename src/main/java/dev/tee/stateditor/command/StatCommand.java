package dev.tee.stateditor.command;

import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.tee.stateditor.config.StatEditorConfig;
import dev.tee.stateditor.data.PokemonLookup;
import dev.tee.stateditor.data.StatIds;
import dev.tee.stateditor.data.StatMath;
import dev.tee.stateditor.data.StatOverrideStore;
import dev.tee.stateditor.item.StatEditorItems;
import dev.tee.stateditor.net.OpenEditorPayload;
import dev.tee.stateditor.net.SetStatPayload;
import dev.tee.stateditor.net.StatEditorNetworking;
import dev.tee.stateditor.party.PartyEditor;
import dev.tee.stateditor.permission.StatEditorPermissions;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;


/**
 * The {@code /stat} command.
 * <pre>
 * /stat edit &lt;slot 1-6&gt; &lt;stat&gt; &lt;value&gt;                   edit a stat of your own party member
 * /stat set  &lt;slot 1-6&gt; &lt;stat&gt; &lt;value&gt; [&lt;stat&gt; &lt;value&gt; ...]   edit several stats at once
 * /stat edit other &lt;player&gt; &lt;slot 1-6&gt; &lt;stat&gt; &lt;value&gt;    edit somebody else's Pokémon
 * /stat reset &lt;slot 1-6&gt; [all | &lt;stat&gt; ...]               remove overrides
 * /stat view &lt;slot 1-6&gt;                                   show the current stats
 * /stat ui                                           open PokéEdit for your party
 * /stat live                                         open the live stat editor for your party
 * /stat give &lt;player&gt;                            give the PokéEdit Staff
 * </pre>
 * <p>
 * The stat names are registered as literal command nodes, which gives tab completion for free
 * without needing a custom brigadier argument type (those have to be registered with Minecraft's
 * command argument registry, which is not possible for mods without an access widener).
 */
public final class StatCommand {
    private StatCommand() {
    }

    /** Registers the command with fabric's command api. */
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher,
                                CommandRegistryAccess registryAccess,
                                CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(CommandManager.literal("stat")
                .then(CommandManager.literal("edit")
                        .then(CommandManager.literal("other")
                                .then(CommandManager.argument("player", EntityArgumentType.player())
                                        .then(otherSlotBranch())))
                        .then(ownSlotBranch()))
                .then(CommandManager.literal("set")
                        .then(CommandManager.argument("slot", IntegerArgumentType.integer(1, 6))
                                .then(CommandManager.argument("pairs", StringArgumentType.greedyString())
                                        .executes(StatCommand::setOwn))))
                .then(CommandManager.literal("reset")
                        .then(CommandManager.argument("slot", IntegerArgumentType.integer(1, 6))
                                .executes(context -> resetOwn(context, "all"))
                                .then(CommandManager.argument("stat", StringArgumentType.greedyString())
                                        .executes(StatCommand::resetOwn))))
                .then(CommandManager.literal("view")
                        .then(CommandManager.argument("slot", IntegerArgumentType.integer(1, 6))
                                .executes(StatCommand::viewOwn)))
                .then(CommandManager.literal("ui")
                        .executes(StatCommand::openUi)
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .requires(source -> source.getPlayer() == null
                                        ? source.hasPermissionLevel(StatEditorPermissions.ADMIN_LEVEL)
                                        : StatEditorPermissions.canEditOthers(source.getPlayer()))
                                .executes(StatCommand::openUiFor)))
                .then(CommandManager.literal("live")
                        .executes(StatCommand::openLiveUi))
                .then(CommandManager.literal("give")
                        .requires(source -> source.getPlayer() != null
                                && StatEditorPermissions.canEditOthers(source.getPlayer()))
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .executes(StatCommand::give))));
    }

    /** The {@code /stat edit <slot> <stat> <value>} branch. */
    private static ArgumentBuilder<ServerCommandSource, ?> ownSlotBranch() {
        ArgumentBuilder<ServerCommandSource, ?> slot =
                CommandManager.argument("slot", IntegerArgumentType.integer(1, 6));
        addStatBranches(slot, StatCommand::editOwn);
        return slot;
    }

    /** The {@code /stat edit other <player> <slot> <stat> <value>} branch. */
    private static ArgumentBuilder<ServerCommandSource, ?> otherSlotBranch() {
        ArgumentBuilder<ServerCommandSource, ?> slot =
                CommandManager.argument("slot", IntegerArgumentType.integer(1, 6));
        addStatBranches(slot, StatCommand::editOther);
        return slot;
    }

    /**
     * Adds one branch per known stat alias. Each branch takes the new value and calls the handler
     * with the alias that was used.
     */
    private static void addStatBranches(ArgumentBuilder<ServerCommandSource, ?> parent, StatHandler handler) {
        for (String alias : StatIds.aliases()) {
            parent.then(CommandManager.literal(alias)
                    .then(CommandManager.argument("value", IntegerArgumentType.integer())
                            .executes(context -> handler.handle(context, alias))));
        }
    }

    /** Handles a single {@code <stat> <value>} pair. */
    @FunctionalInterface
    private interface StatHandler {
        int handle(CommandContext<ServerCommandSource> context, String alias) throws CommandSyntaxException;
    }

    private static int editOwn(CommandContext<ServerCommandSource> context, String alias) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        int slot = IntegerArgumentType.getInteger(context, "slot");
        Stat stat = StatIds.parse(alias);
        int value = IntegerArgumentType.getInteger(context, "value");
        if (!StatEditorPermissions.canEditOwn(player)) {
            player.sendMessage(Text.translatable("stateditor.message.no_permission"), true);
            return 0;
        }
        Pokemon pokemon = ownPokemon(player, slot);
        if (pokemon == null) {
            return 0;
        }
        return apply(player, pokemon, stat, value, false);
    }

    private static int editOther(CommandContext<ServerCommandSource> context, String alias) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "player");
        int slot = IntegerArgumentType.getInteger(context, "slot");
        Stat stat = StatIds.parse(alias);
        int value = IntegerArgumentType.getInteger(context, "value");
        if (!StatEditorPermissions.canEditOthers(player)) {
            player.sendMessage(Text.translatable("stateditor.message.no_permission"), true);
            return 0;
        }
        Pokemon pokemon = ownPokemon(target, slot);
        if (pokemon == null) {
            player.sendMessage(Text.translatable("stateditor.message.no_such_pokemon"), true);
            return 0;
        }
        return apply(player, pokemon, stat, value, false);
    }

    private static int setOwn(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        int slot = IntegerArgumentType.getInteger(context, "slot");
        String pairs = StringArgumentType.getString(context, "pairs");
        if (!StatEditorPermissions.canEditOwn(player)) {
            player.sendMessage(Text.translatable("stateditor.message.no_permission"), true);
            return 0;
        }
        Pokemon pokemon = ownPokemon(player, slot);
        if (pokemon == null) {
            return 0;
        }
        Map<Stat, Integer> parsed = parsePairs(pairs);
        if (parsed.isEmpty()) {
            player.sendMessage(Text.translatable("commands.stateditor.invalid_pairs", pairs), true);
            return 0;
        }
        int changed = 0;
        for (Map.Entry<Stat, Integer> entry : parsed.entrySet()) {
            changed += apply(player, pokemon, entry.getKey(), entry.getValue(), false);
        }
        return changed;
    }

    private static int resetOwn(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        return resetOwn(context, StringArgumentType.getString(context, "stat"));
    }

    private static int resetOwn(CommandContext<ServerCommandSource> context, String argument) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        int slot = IntegerArgumentType.getInteger(context, "slot");
        if (!StatEditorPermissions.canEditOwn(player)) {
            player.sendMessage(Text.translatable("stateditor.message.no_permission"), true);
            return 0;
        }
        Pokemon pokemon = ownPokemon(player, slot);
        if (pokemon == null) {
            return 0;
        }
        if (argument.equalsIgnoreCase("all")) {
            int changed = 0;
            for (Stat stat : StatOverrideStore.all(pokemon).keySet()) {
                changed += apply(player, pokemon, stat, 0, true);
            }
            return changed;
        }
        var parsed = new LinkedHashSet<Stat>();
        for (String token : argument.trim().split("\\s+")) {
            Stat stat = StatIds.parse(token);
            if (stat == null) {
                player.sendMessage(Text.translatable("stateditor.message.unknown_stat", token), true);
                return 0;
            }
            parsed.add(stat);
        }
        int changed = 0;
        for (Stat stat : parsed) {
            changed += apply(player, pokemon, stat, 0, true);
        }
        return changed;
    }

    private static int viewOwn(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        int slot = IntegerArgumentType.getInteger(context, "slot");
        Pokemon pokemon = ownPokemon(player, slot);
        if (pokemon == null) {
            return 0;
        }
        player.sendMessage(Text.translatable("stateditor.message.view_header",
                slot, pokemon.getSpecies().getTranslatedName()), false);
        for (Stat stat : StatIds.EDITABLE) {
            Integer override = StatOverrideStore.get(pokemon, stat);
            int natural = StatMath.naturalStat(pokemon, stat);
            int effective = pokemon.getStat(stat);
            dev.tee.stateditor.StatEditorMod.LOGGER.debug(
                    "[stat] {} effective={} natural={} override={}",
                    StatIds.shortName(stat), effective, natural, override);
            Text line = override == null
                    ? Text.translatable("stateditor.message.view_line", StatIds.displayName(stat), natural)
                    : Text.translatable("stateditor.message.view_line_overridden",
                    StatIds.displayName(stat), override, natural);
            player.sendMessage(line, true);
        }
        return 1;
    }

    private static int openUi(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        PartyEditor.open(player, player);
        return 1;
    }

    private static int openLiveUi(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        if (!StatEditorPermissions.canEditOwn(player)) {
            player.sendMessage(Text.translatable("stateditor.message.no_permission"), true);
            return 0;
        }
        StatEditorNetworking.sendTo(player, new OpenEditorPayload(PokemonLookup.NO_ENTITY));
        return 1;
    }

    /** Opens another player's party on the invoking admin's client. */
    private static int openUiFor(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "player");
        ServerPlayerEntity editor = context.getSource().getPlayer();
        if (editor == null) {
            PartyEditor.open(target, target);
        } else {
            PartyEditor.open(editor, target);
        }
        return 1;
    }

    private static int give(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "player");
        target.getInventory().offerOrDrop(StatEditorItems.pokeEditConsole().getDefaultStack());
        player.sendMessage(Text.translatable("stateditor.message.given", target.getDisplayName()), true);
        target.sendMessage(Text.translatable("stateditor.message.received"), true);
        return 1;
    }

    private static int apply(ServerPlayerEntity source, Pokemon pokemon, Stat stat, int value, boolean clear) {
        return apply(source, pokemon, PokemonLookup.NO_ENTITY, stat, value, clear);
    }

    private static int apply(ServerPlayerEntity source, Pokemon pokemon, int entityId, Stat stat, int value, boolean clear) {
        dev.tee.stateditor.StatEditorMod.LOGGER.info(
                "[stat] {} {} = {} ({} {})",
                StatIds.shortName(stat), pokemon.getSpecies().getName(),
                clear ? "reset" : String.valueOf(value), pokemon.getUuid(), source.getName().getString());
        if (!clear && !StatEditorConfig.isValidValue(value)) {
            source.sendMessage(Text.translatable("stateditor.message.out_of_range",
                    StatEditorConfig.minValue, StatEditorConfig.maxValue), false);
            return 0;
        }
        if (clear) {
            StatOverrideStore.clear(pokemon, stat);
        } else {
            StatOverrideStore.set(pokemon, stat, value);
        }
        if (stat == Stats.HP) {
            pokemon.setCurrentHealth(Math.min(pokemon.getCurrentHealth(), pokemon.getStat(Stats.HP)));
        }
        StatEditorNetworking.broadcast(source.getServer(), pokemon,
                new SetStatPayload(pokemon.getUuid(), entityId, StatIds.key(stat), value, clear));
        if (clear) {
            source.sendMessage(Text.translatable("stateditor.message.cleared", StatIds.displayName(stat)), true);
        } else {
            source.sendMessage(Text.translatable("stateditor.message.changed",
                    StatIds.displayName(stat), value), true);
        }
        return 1;
    }

    private static Pokemon ownPokemon(ServerPlayerEntity player, int slot) {
        var party = PokemonLookup.party(player);
        if (party == null) {
            player.sendMessage(Text.translatable("stateditor.message.no_such_pokemon"), true);
            return null;
        }
        Pokemon pokemon = party.get(slot - 1);
        if (pokemon == null) {
            player.sendMessage(Text.translatable("stateditor.message.empty_slot", slot), true);
            return null;
        }
        return pokemon;
    }

    /** Parses {@code "atk 300 speed 200"} into a map of stat to value. */
    public static Map<Stat, Integer> parsePairs(String argument) {
        Map<Stat, Integer> result = new LinkedHashMap<>();
        if (argument == null) {
            return result;
        }
        String[] tokens = argument.trim().split("\\s+");
        int index = 0;
        while (index < tokens.length) {
            Stat stat = StatIds.parse(tokens[index]);
            if (stat == null || index + 1 >= tokens.length) {
                return Map.of();
            }
            int value;
            try {
                value = Integer.parseInt(tokens[index + 1]);
            } catch (NumberFormatException exception) {
                return Map.of();
            }
            result.put(stat, value);
            index += 2;
        }
        return result;
    }
}
