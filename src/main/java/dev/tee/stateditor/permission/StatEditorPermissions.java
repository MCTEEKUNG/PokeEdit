package dev.tee.stateditor.permission;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.permission.CobblemonPermissions;
import com.cobblemon.mod.common.api.permission.Permission;
import com.cobblemon.mod.common.api.permission.PermissionValidator;
import dev.tee.stateditor.config.StatEditorConfig;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Permission checks for the stat editor.
 * <p>
 * Editing your own Pokémon is allowed for everybody (unless the server turns it off). Editing
 * somebody else's Pokémon requires operator rights. When Cobblemon's
 * own permission validator is present its permissions are honoured as well, so permission plugins
 * can grant access without giving out operator rights.
 */
public final class StatEditorPermissions {
    /** Vanilla operator level that is required to edit other players' Pokémon. */
    public static final int ADMIN_LEVEL = 2;

    private StatEditorPermissions() {
    }

    /** @return whether the player may edit the stats of their own Pokémon. */
    public static boolean canEditOwn(ServerPlayerEntity player) {
        return StatEditorConfig.allowSelfEdit;
    }

    /** @return whether the player may edit the stats of any Pokémon. */
    public static boolean canEditOthers(ServerPlayerEntity player) {
        return player.hasPermissionLevel(ADMIN_LEVEL)
                || hasCobblemonPermission(player, CobblemonPermissions.getPOKEMON_EDIT_OTHER());
    }

    /** @return whether the player may use the Stat Console item. */
    public static boolean canUseConsole(ServerPlayerEntity player) {
        return canEditOwn(player) || canEditOthers(player);
    }

    private static boolean hasCobblemonPermission(ServerPlayerEntity player, Permission permission) {
        try {
            PermissionValidator validator = Cobblemon.INSTANCE.getPermissionValidator();
            return validator != null && validator.hasPermission(player, permission);
        } catch (Throwable throwable) {
            return false;
        }
    }
}
