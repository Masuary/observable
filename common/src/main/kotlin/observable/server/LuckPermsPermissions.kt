package observable.server

import net.minecraft.world.entity.player.Player
import org.apache.logging.log4j.LogManager
import java.util.UUID

object LuckPermsPermissions {
    private val LOGGER = LogManager.getLogger("ObservableLuckPerms")

    fun hasPermission(player: Player, permission: String): Boolean {
        return try {
            val luckPerms = Class.forName("net.luckperms.api.LuckPermsProvider")
                .getMethod("get")
                .invoke(null)
            val userManager = luckPerms.javaClass
                .getMethod("getUserManager")
                .invoke(luckPerms)
            val user = userManager.javaClass
                .getMethod("getUser", UUID::class.java)
                .invoke(userManager, player.gameProfile.id) ?: return false
            val cachedData = user.javaClass
                .getMethod("getCachedData")
                .invoke(user)
            val permissionData = cachedData.javaClass
                .getMethod("getPermissionData")
                .invoke(cachedData)
            val result = permissionData.javaClass
                .getMethod("checkPermission", String::class.java)
                .invoke(permissionData, permission)

            result.javaClass
                .getMethod("asBoolean")
                .invoke(result) as Boolean
        } catch (e: ClassNotFoundException) {
            false
        } catch (e: IllegalStateException) {
            false
        } catch (e: LinkageError) {
            false
        } catch (e: Exception) {
            LOGGER.warn("Error checking LuckPerms permission $permission for ${player.name.contents}", e)
            false
        }
    }
}
